# Blancall Windows 打包脚本
# 用法：powershell -ExecutionPolicy Bypass -File tools/package-windows.ps1
# 前置：JDK 21。默认使用 .tools 下的 Temurin（含 jlink/java）；也可传 -JdkHome。
#
# 产物：
#   dist\Blancall-7.6.1-win-x64-portable.zip   便携版（解压后双击 Blancall.vbs / Blancall.bat）
#
# 说明：jpackage 在受限令牌/沙箱环境下会因 Files.isWritable 误判而失败；
# 本脚本改用 uberJar + jlink + 自行组装 app image 的等价路径，
# 并在脚本末尾尝试 jpackage MSI（可用时才产出）。

param(
    [string]$JdkHome = "",
    [switch]$SkipTests,
    # 可选：指向包含 hccr/ 的目录（例如从 Release 下载的手写包解压后）。
    # 中文模型不随仓库分发；不传此参数时打出的包不含中文手写模型。
    [string]$ModelDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $JdkHome) {
    $candidate = Get-ChildItem -Path ".tools" -Directory -Filter "jdk-21*" -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($candidate) { $JdkHome = $candidate.FullName }
}
if (-not $JdkHome -or -not (Test-Path (Join-Path $JdkHome "bin\java.exe"))) {
    throw "找不到 JDK 21。请先解压 Temurin 到 .tools，或传 -JdkHome。"
}
$env:JAVA_HOME = $JdkHome
$env:GRADLE_USER_HOME = Join-Path $root ".tools\gradle-home"
Write-Host "JAVA_HOME=$JdkHome"

if (-not $SkipTests) {
    & .\gradlew.bat :shared:test :desktop:test --console=plain
    if ($LASTEXITCODE -ne 0) { throw "测试失败，停止打包" }
}

& .\gradlew.bat :desktop:uberJar --console=plain
if ($LASTEXITCODE -ne 0) { throw "uberJar 失败" }

$image = Join-Path $root ".tools\app-image\Blancall"
if (Test-Path $image) { Remove-Item $image -Recurse -Force }
New-Item -ItemType Directory -Force -Path (Join-Path $image "app") | Out-Null

# 精简运行时（与 Compose checkRuntime 相同的模块集合）
& (Join-Path $JdkHome "bin\jlink.exe") `
    --add-modules java.base,java.datatransfer,java.xml,java.prefs,java.desktop,java.logging,jdk.crypto.ec `
    --strip-debug --no-header-files --no-man-pages --compress=2 `
    --output (Join-Path $image "runtime")
if ($LASTEXITCODE -ne 0) { throw "jlink 失败" }

# jlink 产物在部分 JDK 上不带启动器；从完整 JDK 补 java.exe / javaw.exe
Copy-Item (Join-Path $JdkHome "bin\java.exe") (Join-Path $image "runtime\bin") -Force
Copy-Item (Join-Path $JdkHome "bin\javaw.exe") (Join-Path $image "runtime\bin") -Force

Copy-Item "desktop\build\libs\blancall-win.jar" (Join-Path $image "app\blancall-win.jar") -Force
Copy-Item "README.md", "PRIVACY.md" $image -Force
if (Test-Path "THIRD-PARTY-LICENSES.md") { Copy-Item "THIRD-PARTY-LICENSES.md" $image -Force }
$icon = "desktop\src\main\resources\icon\blancall.ico"
if (Test-Path $icon) { Copy-Item $icon (Join-Path $image "Blancall.ico") -Force }
if (Test-Path "models") { Copy-Item "models" (Join-Path $image "models") -Recurse -Force }

# 中文手写模型不在仓库内：需要完整包时用 -ModelDir 注入（目录下应有 hccr/model.ncnn.param）
if ($ModelDir) {
    $srcModel = Join-Path $ModelDir "hccr"
    if (-not (Test-Path (Join-Path $srcModel "model.ncnn.param"))) {
        throw "找不到中文模型：$srcModel\model.ncnn.param"
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $image "models\hccr") | Out-Null
    Copy-Item (Join-Path $srcModel "*") (Join-Path $image "models\hccr") -Force
    Write-Host "已注入中文手写模型：$srcModel"
}
$withHccr = Test-Path (Join-Path $image "models\hccr\model.ncnn.param")
Write-Host ("便携包将包含中文手写模型：{0}" -f $withHccr)

$launcher = @'
@echo off
setlocal
set DIR=%~dp0
cd /d "%DIR%"
start "" "%DIR%runtime\bin\javaw.exe" -Dcompose.application.configure.swing.globals=true -jar "%DIR%app\blancall-win.jar"
endlocal
'@
Set-Content -Path (Join-Path $image "Blancall.bat") -Value $launcher -Encoding OEM

$vbs = @'
Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
dir = fso.GetParentFolderName(WScript.ScriptFullName)
shell.CurrentDirectory = dir
shell.Run """" & dir & "\runtime\bin\javaw.exe"" -Dcompose.application.configure.swing.globals=true -jar """ & dir & "\app\blancall-win.jar""", 0, False
'@
Set-Content -Path (Join-Path $image "Blancall.vbs") -Value $vbs -Encoding ASCII

New-Item -ItemType Directory -Force -Path "dist" | Out-Null
$zip = Join-Path $root "dist\Blancall-7.6.1-win-x64-portable.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $image -DestinationPath $zip -CompressionLevel Optimal
Write-Host "便携包：$zip"

# 精简包（不含 models/，手写入口运行时自动隐藏）
$liteDir = Join-Path $root ".tools\app-lite"
if (Test-Path $liteDir) { Remove-Item $liteDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $liteDir | Out-Null
Copy-Item $image (Join-Path $liteDir "Blancall") -Recurse -Force
Remove-Item (Join-Path $liteDir "Blancall\models") -Recurse -Force -ErrorAction SilentlyContinue
$liteZip = Join-Path $root "dist\Blancall-7.6.1-win-x64-portable-lite.zip"
if (Test-Path $liteZip) { Remove-Item $liteZip -Force }
Compress-Archive -Path (Join-Path $liteDir "Blancall") -DestinationPath $liteZip -CompressionLevel Optimal
Write-Host "精简包：$liteZip"

# EXE 安装包（Inno Setup 6；当前用户安装，卸载不删学习数据）
$isccCandidates = @(
    "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    "C:\Program Files\Inno Setup 6\ISCC.exe",
    (Join-Path $root ".tools\innosetup\ISCC.exe")
)
$iscc = $isccCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) {
    $cmd = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($cmd) { $iscc = $cmd.Source }
}
if ($iscc) {
    & $iscc "/DAppVersion=7.6.1" "/DSourceDir=$image" "/DOutputDir=$(Join-Path $root 'dist')" "tools\windows-installer.iss"
    if ($LASTEXITCODE -eq 0) {
        Write-Host "安装包：dist\Blancall-7.6.1-win-x64-setup.exe"
    } else {
        Write-Warning "Inno Setup 编译失败（退出码 $LASTEXITCODE），便携包已生成。"
    }
} else {
    Write-Warning "未找到 Inno Setup 6（ISCC.exe），跳过 EXE 安装包；安装后重跑本脚本即可。"
}

# 可选：jpackage / MSI（受限令牌下通常不可用，失败不阻断便携包）
$jpackage = Join-Path $JdkHome "bin\jpackage.exe"
if (Test-Path $jpackage) {
    # 优先使用仓库自带的 WiX 3.14（普通用户会话可直接出 MSI）
    $wixDir = Join-Path $root ".tools\wix314"
    if (Test-Path (Join-Path $wixDir "light.exe")) {
        $env:PATH = "$wixDir;$env:PATH"
    } elseif (Test-Path (Join-Path $wixDir "bin\light.exe")) {
        $env:PATH = (Join-Path $wixDir "bin") + ";$env:PATH"
    }
    try {
        $msiOut = Join-Path $root "dist\jpackage-msi"
        New-Item -ItemType Directory -Force -Path $msiOut | Out-Null
        # jpackage 要求 --temp 指向不存在或空的目录；每次先清空
        $msiTemp = Join-Path $root "dist\jpackage-temp"
        if (Test-Path $msiTemp) { Remove-Item $msiTemp -Recurse -Force -ErrorAction SilentlyContinue }
        & $jpackage --type msi --name Blancall --input (Join-Path $image "app") `
            --main-jar "blancall-win.jar" --main-class "com.ilyskyo.blancall.desktop.MainKt" `
            --dest $msiOut --app-version 7.6.1 --vendor ilyskyo --temp $msiTemp 2>$null
        if ($LASTEXITCODE -eq 0) {
            Get-ChildItem $msiOut -Filter *.msi | ForEach-Object {
                Copy-Item $_.FullName "dist\" -Force
                Write-Host "MSI：dist\$($_.Name)"
            }
        } else {
            Write-Warning "jpackage MSI 不可用（受限令牌/临时目录特权），便携包已生成；可在普通用户会话重跑本脚本（会自动使用 .tools\wix314）。"
        }
    } catch {
        Write-Warning "jpackage 失败：$($_.Exception.Message)；便携包已生成。"
    }
}

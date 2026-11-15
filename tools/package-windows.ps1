# Blancall Windows 打包脚本
# 用法：powershell -ExecutionPolicy Bypass -File tools/package-windows.ps1
# 前置：JDK 21。默认使用 .tools 下的 Temurin（含 jlink/java）；也可传 -JdkHome。
#
# 产物：
#   dist\Blancall-7.6.1-win-x64-setup.exe      EXE 安装包（主发布产物）
#   dist\Blancall-7.6.1-win-x64-portable.zip   免安装包（解压后双击 Blancall.exe）
#
# 说明：安装包是自绘单文件向导（不经过系统 Temp，也不在安装时生成卸载器）；
# Blancall.exe 启动器与 BlancallUninstall.exe 卸载器由系统自带的 csc 编译。

param(
    [string]$JdkHome = "",
    [switch]$SkipTests,
    # MSI 需要 jpackage + WiX 3.14；受限令牌环境下 light.exe 可能失败，跳过不影响 EXE 安装包
    [switch]$SkipMsi,
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
Copy-Item "README.md", "PRIVACY.md", "LICENSE" $image -Force
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
Write-Host ("安装包将包含中文手写模型：{0}" -f $withHccr)

# 原生启动器：双击进入应用，不经过脚本宿主，也不会闪控制台窗口
$csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { throw "找不到 .NET Framework 4 编译器：$csc" }
& $csc /nologo /target:winexe /platform:anycpu /codepage:65001 /optimize+ `
    "/win32icon:desktop\src\main\resources\icon\blancall.ico" `
    "/r:System.Windows.Forms.dll" `
    "/out:$image\Blancall.exe" "tools\launcher\BlancallLauncher.cs"
if ($LASTEXITCODE -ne 0) { throw "启动器编译失败" }

New-Item -ItemType Directory -Force -Path "dist" | Out-Null
Get-ChildItem "dist" -File -ErrorAction SilentlyContinue |
    Where-Object {
        $_.Name -like "Blancall-*-setup.exe" -or
        $_.Name -like "Blancall-*-portable*.zip" -or
        $_.Name -like "Blancall-*-win-x64.zip" -or
        $_.Extension -eq ".msi"
    } |
    Remove-Item -Force

# EXE 安装包（自绘单文件向导；不经过系统 Temp，也不在安装时生成 unins000.exe）
$uninstaller = Join-Path $root ".tools\installer\BlancallUninstall.exe"
New-Item -ItemType Directory -Force -Path (Split-Path $uninstaller) | Out-Null
& $csc /nologo /target:winexe /platform:anycpu /codepage:65001 /optimize+ `
    "/win32icon:desktop\src\main\resources\icon\blancall.ico" `
    "/win32manifest:tools\installer\app.manifest" `
    "/r:System.Windows.Forms.dll" "/r:System.Drawing.dll" `
    "/out:$uninstaller" "tools\installer\InstallerStrings.cs" "tools\installer\BlancallUninstall.cs"
if ($LASTEXITCODE -ne 0) { throw "卸载器编译失败" }
Copy-Item $uninstaller (Join-Path $image "BlancallUninstall.exe") -Force

# 安装负载：app image 压成 zip，安装器直接解到安装目录，不经过系统 Temp
$payload = Join-Path $root ".tools\installer\payload.zip"
if (Test-Path $payload) { Remove-Item $payload -Force }
Compress-Archive -Path (Join-Path $image "*") -DestinationPath $payload -CompressionLevel Optimal

# 自绘安装向导：欢迎（MIT 信息页）→ 目录/快捷方式 → 进度 → 完成
& $csc /nologo /target:winexe /platform:anycpu /codepage:65001 /optimize+ `
    "/win32icon:desktop\src\main\resources\icon\blancall.ico" `
    "/win32manifest:tools\installer\app.manifest" `
    "/r:System.Windows.Forms.dll" "/r:System.Drawing.dll" "/r:System.Core.dll" "/r:Microsoft.CSharp.dll" `
    "/r:System.IO.Compression.dll" "/r:System.IO.Compression.FileSystem.dll" `
    "/resource:$payload,payload.zip" "/resource:LICENSE,license.txt" `
    "/out:dist\Blancall-7.6.1-win-x64-setup.exe" "tools\installer\InstallerStrings.cs" "tools\installer\DpiScale.cs" "tools\installer\BlancallInstaller.cs"
if ($LASTEXITCODE -ne 0) { throw "安装向导编译失败" }
Write-Host "安装包：dist\Blancall-7.6.1-win-x64-setup.exe"

# MSI（ARCHITECTURE §14）：jpackage + WiX 3.14，per-user 安装。
# 受限令牌环境下 light.exe 创建带 ACL 的临时目录会被拒绝；此时只警告，EXE 安装包仍可用。
if (-not $SkipMsi) {
    $jpackage = Join-Path $JdkHome "bin\jpackage.exe"
    $wixBin = Join-Path $root ".tools\wix314"
    if ((Test-Path $jpackage) -and (Test-Path (Join-Path $wixBin "light.exe"))) {
        $env:PATH = "$wixBin;$env:PATH"
        $msiOut = Join-Path $root ".tools\msi-out"
        if (Test-Path $msiOut) { Remove-Item $msiOut -Recurse -Force }
        New-Item -ItemType Directory -Force -Path $msiOut | Out-Null
        $appContent = @(
            (Join-Path $image "models"),
            (Join-Path $image "README.md"),
            (Join-Path $image "PRIVACY.md"),
            (Join-Path $image "LICENSE"),
            (Join-Path $image "THIRD-PARTY-LICENSES.md")
        ) | Where-Object { Test-Path $_ }
        & $jpackage --type msi --name Blancall --app-version 7.6.1 `
            --vendor ilyskyo --description "Blancall - local-first cloze memorization" `
            --icon "desktop\src\main\resources\icon\blancall.ico" `
            --input (Join-Path $image "app") --main-jar "blancall-win.jar" `
            --main-class "com.ilyskyo.blancall.desktop.MainKt" `
            --runtime-image (Join-Path $image "runtime") `
            --dest $msiOut --install-dir "Programs\Blancall" `
            --win-per-user-install --win-menu --win-menu-group "Blancall" --win-shortcut --win-dir-chooser `
            --win-upgrade-uuid "2f7f6c25-9a7b-4f53-8d0e-6a0a1e8a7c11" `
            --app-content ($appContent -join ",") --temp (Join-Path $root ".tools\wix-temp-msi")
        if ($LASTEXITCODE -eq 0) {
            $msi = Get-ChildItem $msiOut -Filter *.msi | Select-Object -First 1
            if ($msi) {
                Copy-Item $msi.FullName "dist\Blancall-7.6.1-win-x64.msi" -Force
                Write-Host "MSI：dist\Blancall-7.6.1-win-x64.msi"
            }
        } else {
            Write-Warning "MSI 未生成（jpackage/WiX 失败）；EXE 安装包不受影响。"
        }
    } else {
        Write-Warning "未找到 jpackage 或 WiX 3.14，跳过 MSI；EXE 安装包不受影响。"
    }
}

# 免安装包：解压后双击 Blancall.exe，适合不想安装或装到移动硬盘的场景
$zip = Join-Path $root "dist\Blancall-7.6.1-win-x64-portable.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $image -DestinationPath $zip -CompressionLevel Optimal
Write-Host "免安装包：$zip"

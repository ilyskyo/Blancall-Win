# 把便携版安装到当前用户目录并创建快捷方式（无需管理员权限）。
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools/install-portable.ps1 [-FromZip dist\Blancall-...zip]
# 卸载：删除 %LOCALAPPDATA%\Programs\Blancall 与开始菜单/桌面快捷方式即可；不会删除学习数据。

param(
    [string]$FromZip = "",
    [string]$AppDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dest = Join-Path $env:LOCALAPPDATA "Programs\Blancall"

if ($FromZip) {
    $tmp = Join-Path $env:TEMP ("blancall-install-" + [Guid]::NewGuid().ToString("N"))
    Expand-Archive -Path $FromZip -DestinationPath $tmp -Force
    $src = Join-Path $tmp "Blancall"
} elseif ($AppDir) {
    $src = $AppDir
} else {
    $src = Join-Path $root ".tools\app-image\Blancall"
}
if (-not (Test-Path (Join-Path $src "Blancall.vbs"))) {
    throw "找不到应用目录（需要 Blancall.vbs）：$src"
}

if (Test-Path $dest) {
    # 只替换程序文件，不碰 %LOCALAPPDATA%\Blancall 学习数据
    Remove-Item $dest -Recurse -Force
}
New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
Copy-Item $src $dest -Recurse -Force

$ws = New-Object -ComObject WScript.Shell
$target = Join-Path $dest "Blancall.vbs"
foreach ($lnkPath in @(
        (Join-Path $env:USERPROFILE "Desktop\Blancall.lnk"),
        (Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Blancall.lnk")
    )) {
    New-Item -ItemType Directory -Force -Path (Split-Path $lnkPath) | Out-Null
    $lnk = $ws.CreateShortcut($lnkPath)
    $lnk.TargetPath = $target
    $lnk.WorkingDirectory = $dest
    $lnk.Description = "Blancall - local-first cloze memorization"
    $ico = Join-Path $dest "Blancall.ico"
    if (Test-Path $ico) { $lnk.IconLocation = $ico }
    $lnk.Save()
}

Write-Host "已安装到：$dest"
Write-Host "已创建桌面与开始菜单快捷方式（Blancall）。"
Write-Host "学习数据仍保存在：$env:LOCALAPPDATA\Blancall"

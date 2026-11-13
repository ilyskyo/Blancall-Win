; Blancall Windows 安装包（Inno Setup 6）
; 生成：tools\package-windows.ps1 调用 ISCC，产物 dist\Blancall-7.6.1-win-x64-setup.exe
;
; 约定：
; - 当前用户安装（PrivilegesRequired=lowest），默认目录 %LOCALAPPDATA%\Programs\Blancall
; - 卸载只删除程序目录与快捷方式，不触碰 %LOCALAPPDATA%\Blancall 学习数据
; - 中文手写模型不随安装包分发（CASIA-HWDB 限制），由 Release 手写包单独安装

#define AppName "Blancall"
#ifndef AppVersion
  #define AppVersion "7.6.1"
#endif
#ifndef SourceDir
  #define SourceDir "..\.tools\app-image\Blancall"
#endif
#ifndef OutputDir
  #define OutputDir "..\dist"
#endif

[Setup]
; 与 Gradle 中的 upgradeUuid 保持一致：升级时识别为同一产品
AppId={{2F7F6C25-9A7B-4F53-8D0E-6A0A1E8A7C11}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=ilyskyo
AppPublisherURL=https://github.com/ilyskyo/Blancall-Win
AppSupportURL=https://github.com/ilyskyo/Blancall-Win/issues
AppUpdatesURL=https://github.com/ilyskyo/Blancall-Win/releases
DefaultDirName={localappdata}\Programs\Blancall
DefaultGroupName=Blancall
DisableProgramGroupPage=yes
AllowNoIcons=yes
LicenseFile=..\LICENSE
OutputDir={#OutputDir}
OutputBaseFilename=Blancall-{#AppVersion}-win-x64-setup
SetupIconFile=..\desktop\src\main\resources\icon\blancall.ico
UninstallDisplayIcon={app}\Blancall.ico
UninstallDisplayName={#AppName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
CloseApplications=yes
RestartApplications=no
ChangesAssociations=no
ChangesEnvironment=no
VersionInfoVersion=7.6.1.2
VersionInfoCompany=ilyskyo
VersionInfoDescription=Blancall Setup
VersionInfoProductName=Blancall
VersionInfoProductVersion={#AppVersion}
VersionInfoCopyright=Copyright (c) 2026 ilyskyo

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\Blancall.vbs"; WorkingDir: "{app}"; IconFilename: "{app}\Blancall.ico"
Name: "{group}\{cm:UninstallProgram,{#AppName}}"; Filename: "{uninstallexe}"
Name: "{userdesktop}\{#AppName}"; Filename: "{app}\Blancall.vbs"; WorkingDir: "{app}"; IconFilename: "{app}\Blancall.ico"; Tasks: desktopicon

[Run]
Filename: "{app}\Blancall.vbs"; Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent

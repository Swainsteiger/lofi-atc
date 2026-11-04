; Windows installer for Lofi ATC (Inno Setup 6). Built by build-windows.ps1, which first packages
; pc\serve.pyw with Python included as build-win\dist\LofiATC. Installs for the current user only, so it
; needs no administrator rights; adds Start menu and desktop shortcuts and an uninstaller.

#define AppVersion "1.1.0"

[Setup]
AppId={{6E2B7D4A-3C1F-4A8E-9B5D-2F7C1E0A9D34}
AppName=Lofi ATC
AppVersion={#AppVersion}
AppVerName=Lofi ATC {#AppVersion}
AppPublisher=Swainsteiger
AppPublisherURL=https://github.com/Swainsteiger/lofi-atc
AppSupportURL=https://github.com/Swainsteiger/lofi-atc/issues
PrivilegesRequired=lowest
DefaultDirName={autopf}\Lofi ATC
DisableProgramGroupPage=yes
DisableDirPage=yes
OutputDir=..\dist
OutputBaseFilename=Lofi-ATC-Setup
SetupIconFile=..\pc\lofiatc.ico
UninstallDisplayIcon={app}\LofiATC.exe
UninstallDisplayName=Lofi ATC
WizardStyle=modern
Compression=lzma2
SolidCompression=yes
CloseApplications=yes

[Tasks]
Name: "desktopicon"; Description: "Put a Lofi ATC icon on the desktop"

[Files]
Source: "..\build-win\dist\LofiATC\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\Lofi ATC"; Filename: "{app}\LofiATC.exe"
Name: "{autodesktop}\Lofi ATC"; Filename: "{app}\LofiATC.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\LofiATC.exe"; Description: "Start Lofi ATC now"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; Stop it if it is running, so its files can be removed.
Filename: "{sys}\taskkill.exe"; Parameters: "/IM LofiATC.exe /F"; Flags: runhidden; RunOnceId: "StopLofiATC"

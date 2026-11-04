# Builds the Windows installer, dist\Lofi-ATC-Setup.exe: packages pc\serve.pyw with Python included
# (PyInstaller), then wraps it in an installer (Inno Setup 6). Needs Python 3 and Inno Setup 6
# (winget install --id JRSoftware.InnoSetup -e --source winget --scope user).
#   powershell -ExecutionPolicy Bypass -File build-windows.ps1
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$venv = 'build-win\venv'
if (-not (Test-Path "$venv\Scripts\python.exe")) {
    python -m venv $venv
    & "$venv\Scripts\python.exe" -m pip install -q pyinstaller
}

# One folder rather than one file: starts faster and antivirus programs flag it less often.
& "$venv\Scripts\python.exe" -m PyInstaller --noconfirm --clean --windowed --onedir `
    --name LofiATC --icon "$PSScriptRoot\pc\lofiatc.ico" `
    --add-data "$PSScriptRoot\pc;pc" --add-data "$PSScriptRoot\airports.json;." `
    --distpath build-win\dist --workpath build-win\work --specpath build-win `
    pc\serve.pyw
if ($LASTEXITCODE -ne 0) { throw 'PyInstaller failed' }

$iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe", "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe") |
    Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { throw 'Inno Setup 6 not found (see the top of this script)' }
& $iscc /Q windows\installer.iss
if ($LASTEXITCODE -ne 0) { throw 'Inno Setup failed' }
Get-Item dist\Lofi-ATC-Setup.exe | Select-Object Name, @{n='MB'; e={[math]::Round($_.Length / 1MB, 1)}}

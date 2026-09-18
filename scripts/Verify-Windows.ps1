$ErrorActionPreference="Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Path verification -Force | Out-Null
Start-Transcript -Path verification/windows-build-and-tests.txt
try {
 dotnet --info
 if($LASTEXITCODE -ne 0) { throw "Install the .NET 10 SDK first." }
 dotnet restore JengaVideoStudio.sln
 if($LASTEXITCODE -ne 0) { throw "Restore failed." }
 dotnet build JengaVideoStudio.sln -c Debug --no-restore
 if($LASTEXITCODE -ne 0) { throw "Debug build failed." }
 dotnet build JengaVideoStudio.sln -c Release --no-restore
 if($LASTEXITCODE -ne 0) { throw "Release build failed." }
 dotnet run --project tests/JengaVideoStudio.Tests -c Release --no-build
 if($LASTEXITCODE -ne 0) { throw "Core tests failed." }
 $settingsFile=Join-Path $env:LOCALAPPDATA 'JengaVideoStudio/settings.json'
 if(Test-Path $settingsFile) {
  $settings=Get-Content $settingsFile -Raw | ConvertFrom-Json
  $env:JENGA_FFMPEG=$settings.Ffmpeg
  $env:JENGA_FFPROBE=$settings.Ffprobe
  dotnet run --project tests/JengaVideoStudio.Tests -c Release --no-build -- --render
  if($LASTEXITCODE -ne 0) { throw "Render integration test failed." }
 } else { Write-Host "Render test skipped: first install FFmpeg through the app and save settings." }
 Write-Host "Build and available tests passed. Complete docs/ACCEPTANCE-CHECKLIST.md next."
} finally { Stop-Transcript }

param([string]$ApiUrl='http://127.0.0.1:8000')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Invoke-RestMethod -Uri ($ApiUrl.TrimEnd('/')+'/api/health') -TimeoutSec 5 | Out-Null
& (Join-Path $root 'code/javafx_desktop/build.ps1') -IfNeeded
$previous=$env:SCMS_API_URL
try {$env:SCMS_API_URL=$ApiUrl;& java -jar (Join-Path $root 'code/javafx_desktop/target/javafx-desktop-1.0.0.jar')}
finally {$env:SCMS_API_URL=$previous}

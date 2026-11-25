param([switch]$Release)
$ErrorActionPreference='Stop'
Push-Location $PSScriptRoot
try {
  if(-not $env:ANDROID_HOME -and -not(Test-Path 'local.properties')) {
    $sdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if(-not(Test-Path $sdk)){throw 'Install Android SDK and set ANDROID_HOME, or open this folder in Android Studio.'}
    # Machine-specific SDK configuration is generated locally and excluded from Git.
    ('sdk.dir='+$sdk.Replace('\','/')) | Set-Content -Encoding ASCII 'local.properties'
  }
  $task=if($Release){'assembleRelease'}else{'assembleDebug'}
  & '.\gradlew.bat' $task
  if($LASTEXITCODE -ne 0){throw 'Android Java build failed.'}
} finally { Pop-Location }

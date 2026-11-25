param([switch]$Demo,[switch]$SkipBuild,[int]$ApiPort=18001,[int]$WebPort=18081)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$names=@('SCMS_JAVA_DB','SCMS_JAVA_SECRET','SCMS_JAVA_DEMO_PASSWORD','API_PORT','API_HOST','SCMS_WEB_PORT','SCMS_WEB_HOST','SCMS_API_URL')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
$apiProcess=$null
$webProcess=$null
try {
  $local=Join-Path $root '.local/java-version'
  New-Item -ItemType Directory -Force $local | Out-Null
  # Dedicated database; the existing FastAPI database is never opened by this launcher.
  $env:SCMS_JAVA_DB=Join-Path $local 'java-preview.db'
  $bytes=New-Object byte[] 48
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  $env:SCMS_JAVA_SECRET=[Convert]::ToBase64String($bytes)
  $env:API_PORT="$ApiPort";$env:API_HOST='0.0.0.0'
  $env:SCMS_WEB_PORT="$WebPort";$env:SCMS_WEB_HOST='127.0.0.1'
  $env:SCMS_API_URL="http://127.0.0.1:$ApiPort/api"
  if($Demo) {
    $secret=Read-Host 'Choose the Java demo password (8+ characters; existing accounts are not reset)' -AsSecureString
    $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try{$env:SCMS_JAVA_DEMO_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
    finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
    if($env:SCMS_JAVA_DEMO_PASSWORD.Length -lt 8){throw 'Password must be at least 8 characters.'}
  } else { $env:SCMS_JAVA_DEMO_PASSWORD=$null }
  if(-not $SkipBuild) {
    & (Join-Path $root 'code/java_backend/build.ps1') -SkipTests
    & (Join-Path $root 'code/vaadin_dashboard/build.ps1') -SkipTests
  }
  $apiJar=Join-Path $root 'code/java_backend/target/java-backend-1.0.0.jar'
  $webJar=Join-Path $root 'code/vaadin_dashboard/target/vaadin-dashboard-1.0.0.jar'
  if(-not (Test-Path $apiJar) -or -not (Test-Path $webJar)){throw 'Build the two Java JAR files first.'}
  foreach($port in @($ApiPort,$WebPort)) {
    if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Port $port is already in use. Existing processes were left running."}
  }
  $apiProcess=Start-Process java -ArgumentList @('-jar',('"{0}"' -f $apiJar)) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'api.log') -RedirectStandardError (Join-Path $local 'api-error.log')
  $ready=$false
  for($attempt=0;$attempt -lt 60;$attempt++) {
    if($apiProcess.HasExited){throw 'Java API exited. Read .local/java-version/api-error.log.'}
    try{$null=Invoke-RestMethod "http://127.0.0.1:$ApiPort/api/health" -TimeoutSec 2;$ready=$true;break}catch{Start-Sleep -Seconds 1}
  }
  if(-not $ready){throw 'Java API did not become ready.'}
  # The child web process does not need the seed password or API signing key.
  $env:SCMS_JAVA_DEMO_PASSWORD=$null;$env:SCMS_JAVA_SECRET=$null
  $webProcess=Start-Process java -ArgumentList @('-jar',('"{0}"' -f $webJar)) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'website.log') -RedirectStandardError (Join-Path $local 'website-error.log')
  Write-Host "Website: http://127.0.0.1:$WebPort"
  Write-Host "API: http://YOUR-LAPTOP-WIFI-IP:$ApiPort (use this in the separate Java Android preview)"
  if($Demo){Write-Host 'Accounts: admin@scms.local, staff@scms.local, student@scms.local'}
  Read-Host 'Press Enter to stop only the Java preview processes started here' | Out-Null
} finally {
  foreach($process in @($webProcess,$apiProcess)){if($null -ne $process -and -not $process.HasExited){Stop-Process -Id $process.Id -ErrorAction SilentlyContinue}}
  foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')}
}

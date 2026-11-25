param([switch]$SkipTests, [switch]$IfNeeded)
$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jar=Join-Path $PSScriptRoot 'target/java-backend-1.0.0.jar'
if($IfNeeded -and (Test-Path -LiteralPath $jar)) {
    $latest=Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -File | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $pom=Get-Item (Join-Path $PSScriptRoot 'pom.xml')
    if((Get-Item $jar).LastWriteTimeUtc -gt $latest.LastWriteTimeUtc -and (Get-Item $jar).LastWriteTimeUtc -gt $pom.LastWriteTimeUtc) {
        Write-Host 'Java backend build is up to date.'
        return
    }
}
$tools = Join-Path $root '.local/tools' 
$maven = Join-Path $tools 'apache-maven-3.9.9/bin/mvn.cmd'
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install JDK 17 and add Java to PATH first.' }
if (-not (Test-Path -LiteralPath $maven)) {
    New-Item -ItemType Directory -Force $tools | Out-Null
    $zip = Join-Path $tools 'apache-maven-3.9.9-bin.zip'
    $url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zip
    $expected = ((Invoke-WebRequest -UseBasicParsing -Uri "$url.sha512").Content.Trim() -split '\s+')[0]
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA512).Hash -ne $expected) { throw 'Maven download checksum did not match.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
}
$arguments = @('-f', (Join-Path $PSScriptRoot 'pom.xml'), '-B', 'package')
if ($SkipTests) { $arguments += '-DskipTests' }
& $maven @arguments
if ($LASTEXITCODE -ne 0) { throw 'Java backend desktop build failed.' }

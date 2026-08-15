param(
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Find-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) {
        return $env:JAVA_HOME
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $home = Split-Path (Split-Path $cmd.Source)
        if (Test-Path (Join-Path $home "bin\javac.exe")) {
            return $home
        }
    }
    $candidates = @(
        "$env:USERPROFILE\.jdks\jbr-21.0.11",
        "$env:USERPROFILE\.jdks\jbr-21.0.11.intellij"
    )
    $jdks = Get-ChildItem "$env:USERPROFILE\.jdks" -ErrorAction SilentlyContinue
    foreach ($j in $jdks) { $candidates += $j.FullName }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path (Join-Path $c "bin\javac.exe"))) {
            return $c
        }
    }
    throw "JDK not found. Install Java 17+ and set JAVA_HOME."
}

$javaHome = Find-JavaHome
$javac = Join-Path $javaHome "bin\javac.exe"
$java = Join-Path $javaHome "bin\java.exe"
Write-Host "Using JDK: $javaHome"

$out = Join-Path $root "out"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item $out -ItemType Directory | Out-Null

$sources = Get-ChildItem -Path (Join-Path $root "src") -Filter *.java -Recurse | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "Compilation failed." }

if ($SelfTest) {
    & $java -cp $out dsp501.voicegender.App --self-test
    exit $LASTEXITCODE
}

& $java -cp $out dsp501.voicegender.App

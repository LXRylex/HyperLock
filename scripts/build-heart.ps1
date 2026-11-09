param([switch]$UseMavenMirror, [switch]$Performance)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
if(!$env:JAVA_HOME){$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'}
if(!$env:ANDROID_HOME){$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
$candidate=Get-Command gradle -ErrorAction SilentlyContinue
$gradle=if($candidate){$candidate.Source}else{
    $cache=Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-9.6.0-bin'
    (Get-ChildItem $cache -Recurse -Filter gradle.bat -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}
if(!$gradle){throw 'Gradle 9.6 is required. Open this project in Android Studio or install Gradle 9.6.'}
$arguments=@('--no-daemon','-PhyperduoVersionCode=10757','-PhyperduoVersionName=1.7.4-heart.53',':app:assembleDebug')
if($Performance){$arguments[-1]=':app:assemblePerformance'}
if($UseMavenMirror){$arguments=@('-PheartMavenMirror=true')+$arguments}
Push-Location $root
try {
    & $gradle @arguments
    if($LASTEXITCODE){throw 'Heart build failed'}
    $builtApk=if($Performance){Join-Path $root 'app\build\outputs\apk\performance\app-performance.apk'}else{Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'}
    $namedApk=if($Performance){Join-Path $root 'app\build\outputs\apk\performance\HyperLock.apk'}else{Join-Path $root 'app\build\outputs\apk\debug\HyperLock.apk'}
    Copy-Item -LiteralPath $builtApk -Destination $namedApk -Force
    Write-Host "APK: $namedApk"
} finally {Pop-Location}















$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:\Program Files\Android\Android Studio\jbr\bin'}
$src=Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo'
$shims=Get-ChildItem (Join-Path $PSScriptRoot 'shims') -Recurse -Filter '*.java' | ForEach-Object FullName
$hashes=@()
foreach($variant in @('before','after')) {
    $out=Join-Path $root "work\style-regression\$variant"
    New-Item -ItemType Directory -Force $out | Out-Null
    $sources=@()
    foreach($name in @('Prefs.java','TrioSettings.java','TrioAppearance.java','TrioGeometry.java','TrioZones.java','TrioRenderer.java')) {
        $original=Join-Path $root ".heart-backup\app\src\main\java\io\github\yixing233\hyperduo\$name"
        $sources+=if($variant -eq 'before' -and (Test-Path $original)){$original}else{Join-Path $src $name}
    }
    if($variant -eq 'after') { $sources+=@('HeartBattery.java','HeartBatteryLayout.java','HeartChargeMotion.java','HeartBatteryRenderer.java','BatteryShape.java','HeartShape.java') | ForEach-Object {Join-Path $src $_} }
    $sources+=$shims
    $sources+=Join-Path $PSScriptRoot 'StyleRasterRegression.java'
    & "$jdk\javac.exe" -encoding UTF-8 -d $out @sources
    if($LASTEXITCODE){throw "Style regression $variant compilation failed"}
    $result=& "$jdk\java.exe" '-Djava.awt.headless=true' -cp $out io.github.yixing233.hyperduo.StyleRasterRegression
    if($LASTEXITCODE){throw "Style regression $variant failed"}
    Write-Host "$variant $result"
    $hashes+=$result
}
if($hashes[0] -ne $hashes[1]){throw 'Existing Ring/Capsule rendering changed'}
Write-Host 'PASS: 48 Ring/Capsule frames are pixel-identical to the original downloaded sources.'

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { 'C:\Program Files\Android\Android Studio\jbr\bin' }
$out = Join-Path $root 'work\heart-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$src = Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo'
$shims = Join-Path $PSScriptRoot 'shims'
$sources = @("$src\Prefs.java", "$src\TrioSettings.java", "$src\TrioAppearance.java", "$src\TrioZones.java", "$src\TrioGeometry.java", "$src\HeartBattery.java", "$src\StatusBarTintPolicy.java", "$src\ControlCenterImagePolicy.java", "$src\StatusBarTintProxy.java", "$src\StatusBarTintOverlay.java", "$PSScriptRoot\StatusBarTintTest.java", "$src\HeartBatteryLayout.java", "$PSScriptRoot\HeartPercentagePositionTest.java", "$src\HeartChargeMotion.java", "$PSScriptRoot\HeartChargeMotionTest.java", "$src\HeartBatteryRenderer.java", "$src\BatteryShape.java", "$src\HeartShape.java", "$src\TrioRenderer.java", "$PSScriptRoot\HeartBatteryTest.java", "$PSScriptRoot\HeartBoltTest.java", "$PSScriptRoot\SettingsColorTest.java")
$sources += Get-ChildItem "$shims\android" -Recurse -Filter '*.java' | ForEach-Object FullName
$sources += @("$shims\io\github\yixing233\hyperduo\TrioState.java", "$shims\io\github\yixing233\hyperduo\TrioConfig.java")
& "$jdk\javac.exe" -encoding UTF-8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Heart test compilation failed' }
& "$jdk\java.exe" "-Djava.awt.headless=true" -cp $out io.github.yixing233.hyperduo.HeartBatteryTest
if ($LASTEXITCODE -ne 0) { throw 'Heart tests failed' }

& "$jdk\java.exe" "-Djava.awt.headless=true" -cp $out io.github.yixing233.hyperduo.HeartChargeMotionTest
if ($LASTEXITCODE -ne 0) { throw "Charging wave tests failed" }

& "$jdk\java.exe" "-Djava.awt.headless=true" -cp $out io.github.yixing233.hyperduo.HeartPercentagePositionTest
if ($LASTEXITCODE -ne 0) { throw "Percentage placement tests failed" }

& "$jdk\java.exe" -cp $out io.github.yixing233.hyperduo.StatusBarTintTest
if ($LASTEXITCODE -ne 0) { throw "Status bar tint tests failed" }

& "$jdk\java.exe" -cp $out io.github.yixing233.hyperduo.HeartBoltTest
if ($LASTEXITCODE -ne 0) { throw 'Heart bolt checks failed' }


& "$jdk\java.exe" -cp $out io.github.yixing233.hyperduo.SettingsColorTest
if ($LASTEXITCODE -ne 0) { throw 'Settings color checks failed' }


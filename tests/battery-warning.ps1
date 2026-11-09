$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:\Program Files\Android\Android Studio\jbr\bin'}
$out=Join-Path $root 'work\battery-warning-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& "$jdk\javac.exe" -encoding UTF-8 -d $out (Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo\BatteryWarningStyle.java') (Join-Path $PSScriptRoot 'BatteryWarningStyleTest.java')
if($LASTEXITCODE){throw 'Battery warning test compilation failed'}
& "$jdk\java.exe" -cp $out io.github.yixing233.hyperduo.BatteryWarningStyleTest
if($LASTEXITCODE){throw 'Battery warning tests failed'}

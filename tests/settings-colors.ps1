$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { 'C:\Program Files\Android\Android Studio\jbr\bin' }
$out = Join-Path $root 'work\settings-color-tests'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$src = Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo'
& "$jdk\javac.exe" -encoding UTF-8 -d $out "$src\SettingsColorChannels.java" "$PSScriptRoot\SettingsColorChannelsTest.java" "$src\ui\RestartTargetPolicy.java" "$PSScriptRoot\RestartSettingsTest.java" "$PSScriptRoot\RestartTargetPolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Settings controls test compilation failed' }
foreach ($test in @('io.github.yixing233.hyperduo.SettingsColorChannelsTest','io.github.yixing233.hyperduo.ui.RestartSettingsTest','io.github.yixing233.hyperduo.ui.RestartTargetPolicyTest')) {
    & "$jdk\java.exe" -cp $out $test
    if ($LASTEXITCODE -ne 0) { throw "Settings controls test failed: $test" }
}

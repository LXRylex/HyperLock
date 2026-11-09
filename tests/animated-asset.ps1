$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:\Program Files\Android\Android Studio\jbr\bin'}
$out=Join-Path $root 'work\animated-asset-tests';New-Item -ItemType Directory -Force -Path $out | Out-Null
$src=Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo'
& "$jdk\javac.exe" -encoding UTF-8 -d $out "$src\AnimatedAssetFormat.java" "$src\GifFrameTiming.java" "$PSScriptRoot\AnimatedAssetFormatTest.java" "$PSScriptRoot\GifFrameTimingTest.java"
if($LASTEXITCODE){throw 'Animated tests compilation failed'}
foreach($name in @('AnimatedAssetFormatTest','GifFrameTimingTest')){& "$jdk\java.exe" -cp $out "io.github.yixing233.hyperduo.$name";if($LASTEXITCODE){throw "$name failed"}}

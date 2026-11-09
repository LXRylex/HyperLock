$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:\Program Files\Android\Android Studio\jbr\bin'}
$hooks=[IO.File]::ReadAllText((Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo\TrioHooks.java'))
$method=[regex]::Match($hooks,'(?s)private static void applyMeterText\(List<View> meters\) \{.*?\n    \}').Value
if(!$method){throw 'Production applyMeterText missing'}
$out=Join-Path $root 'work\native-restore'
New-Item -ItemType Directory -Force $out | Out-Null
$source=@'
import java.util.*;
public class NativeRestoreTest {
    static class View { int style,realStyle,nativeCalls,forcedWrites; void post(Runnable r){r.run();} }
    static class TrioAppearance { boolean glyph; boolean hidesNativeBolt(){return false;} boolean handsBackPercentage(){return false;} }
    static class TrioConfig { static TrioAppearance appearance(){return new TrioAppearance();} }
    static Object sChargingViewField,sBatteryIconViewField,sHollowBatteryIconViewField,sPercentContainerField,sUpdateChargeAndTextField;
    static final Object sBatteryStyleField=new Object();
    static final int BOLT_STYLE=1,LOG_INFO=4;
    static boolean debugLog(){return false;} static void log(int level,String text){}
    static class Refl {
        static Object get(Object field,Object owner){return null;}
        static void set(Object field,Object owner,Object value){View v=(View)owner;if(field==sBatteryStyleField){v.style=(Integer)value;v.forcedWrites++;}}
        static Object invoke(Object method,Object owner){return null;}
    }
    static void showIfHidden(Object view){} static void hide(Object view){}
    static void reapplyStyle(View view){view.style=view.realStyle;view.nativeCalls++;}
    PRODUCTION_METHOD
    public static void main(String[] args) {
        for(int style=0;style<=3;style++) {
            View meter=new View();meter.style=99;meter.realStyle=style;
            applyMeterText(Arrays.asList(meter));
            if(meter.style!=style || meter.forcedWrites!=0 || meter.nativeCalls!=1)
                throw new AssertionError("Native style "+style+" was overwritten: style="+meter.style+", writes="+meter.forcedWrites+", native="+meter.nativeCalls);
        }
        System.out.println("PASS: native battery styles 0, 1, 2 and 3 are restored without forced module writes");
    }
}
'@
$source=$source.Replace('PRODUCTION_METHOD',$method)
$file=Join-Path $out 'NativeRestoreTest.java'
[IO.File]::WriteAllText($file,$source)
& "$jdk\javac.exe" -encoding UTF-8 -d $out $file
if($LASTEXITCODE){throw 'Native restore test compile failed'}
& "$jdk\java.exe" -cp $out NativeRestoreTest
if($LASTEXITCODE){throw 'Native restore tests failed'}

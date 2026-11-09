$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$jdk=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin'}else{'C:\Program Files\Android\Android Studio\jbr\bin'}
$hooks=[IO.File]::ReadAllText((Join-Path $root 'app\src\main\java\io\github\yixing233\hyperduo\TrioHooks.java'))
$method=[regex]::Match($hooks,'(?s)static (?:synchronized )?void install\(XposedModule module, ClassLoader cl\) \{.*?\n    \}').Value
if(!$method){throw 'Production hook install method missing'}
$method=$method.Replace('android.os.Build.VERSION.SDK_INT','36')
$out=Join-Path $root 'work\heart-lifecycle'
New-Item -ItemType Directory -Force $out | Out-Null
$source=@'
public class InstallLifecycleTest {
    static class XposedModule {} static class Canvas {} static class BatteryHost {}
    static class StatusBarTintHooks {static void install(XposedModule module,ClassLoader loader){}}
    static class Loader extends ClassLoader { final boolean hasHost; Loader(boolean hasHost){this.hasHost=hasHost;} }
    static class HeartBattery { static boolean failed; }
    static XposedModule sModule;
    static boolean sHooksInstalled;
    static int hooks,preferences,listeners;
    static final int LOG_WARN=5,LOG_INFO=4;
    static class Refl {
        static Class<?> cls(String name,ClassLoader loader){return ((Loader)loader).hasHost?BatteryHost.class:null;}
        static Object method(Class<?> cls,String name,Class<?>... args){return new Object();}
        static Object field(Class<?> cls,String name){return new Object();}
    }
    static class TrioConfig {
        interface Listener { void onConfigChanged(); }
        static class Settings {boolean enabled=true;}
        static void install(XposedModule module){preferences++;}
        static void addListener(Listener listener){listeners++;}
        static Settings get(){return new Settings();}
    }
    static void applyConfigChange(){}
    static int group(XposedModule m,ClassLoader loader,int id){hooks++;return 1;}
    static void log(XposedModule m,String text){} static void log(int level,String text){}
    PRODUCTION_INSTALL
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void reset(){HeartBattery.failed=false;sHooksInstalled=false;hooks=preferences=listeners=0;}
    public static void main(String[] args){
        XposedModule module=new XposedModule(); Loader main=new Loader(true),plugin=new Loader(false);
        reset(); install(module,main); install(module,plugin); install(module,main);
        check(!HeartBattery.failed,"A later HyperOS plugin loader disabled a successfully installed Heart hook");
        check(hooks==14 && preferences==1 && listeners==1,"Duplicate package-ready callback reinstalled hooks/listeners");
        reset(); install(module,plugin);
        check(!HeartBattery.failed && hooks==0 && preferences==0 && listeners==0,"Unsupported loader must skip without changing process state");
        install(module,main);
        check(hooks==14 && !HeartBattery.failed,"The later real SystemUI loader must still install");
        System.out.println("PASS: primary/plugin/repeated and plugin-before-primary class-loader sequences");
    }
}
'@
$source=$source.Replace('PRODUCTION_INSTALL',$method)
$file=Join-Path $out 'InstallLifecycleTest.java'
[IO.File]::WriteAllText($file,$source)
& "$jdk\javac.exe" -encoding UTF-8 -d $out $file
if($LASTEXITCODE){throw 'Lifecycle test compilation failed'}
& "$jdk\java.exe" -cp $out InstallLifecycleTest
if($LASTEXITCODE){throw 'Lifecycle tests failed'}

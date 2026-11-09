package io.github.yixing233.hyperduo.ui;
public final class RestartSettingsTest {
 public static void main(String[] args) {
  check(RestartTargetPolicy.command(false,false,false,false)==null);
  check(RestartTargetPolicy.matches("com.miui.securitycenter.power","com.miui.securitycenter"));
  check(RestartTargetPolicy.matches("com.miui.securitycenter:ui","com.miui.securitycenter"));
  check(!RestartTargetPolicy.matches("com.miui.securitycenterfake","com.miui.securitycenter"));
  check(RestartTargetPolicy.matches("com.android.settings:remote","com.android.settings"));
  String security=RestartTargetPolicy.command(false,false,false,true);
  check(security.contains("com.miui.securitycenter") && security.contains("ps -A") && !security.contains("force-stop"));
  String all=RestartTargetPolicy.command(true,true,true,true);
  check(all.contains("com.android.systemui") && all.contains("com.miui.home") && all.contains("com.android.settings") && all.contains("com.miui.securitycenter"));
  System.out.println("PASS: Settings and Security subprocess restart selection");
 }
 private static void check(boolean v){if(!v)throw new AssertionError();}
}

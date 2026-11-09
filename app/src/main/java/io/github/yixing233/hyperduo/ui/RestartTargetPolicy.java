// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo.ui;
/** Fixed process names only; absence of a Settings/Security process is not a root failure. */
final class RestartTargetPolicy {
 private RestartTargetPolicy() {}
 static boolean matches(String name,String pkg) {
  return name!=null && (name.equals(pkg)||name.startsWith(pkg+":")||name.startsWith(pkg+"."));
 }
 private static String killPackageProcesses(String pkg) {
  return "{ pids=$(ps -A -o PID,NAME | awk '$2 == \""+pkg+"\" || index($2,\""+pkg+":\")==1 || index($2,\""+pkg+".\")==1 {print $1}'); "
      +"for pid in $pids; do if ! kill \"$pid\" 2>/dev/null && [ -d \"/proc/$pid\" ]; then echo 'Could not stop "+pkg+" process'; exit 1; fi; done; }";
 }
 static String command(boolean systemUi,boolean launcher){
  if(systemUi && launcher)return "killall com.android.systemui && killall com.miui.home";
  if(systemUi)return "killall com.android.systemui";
  if(launcher)return "killall com.miui.home";
  return null;
 }
 static String command(boolean systemUi,boolean launcher,boolean settings){return command(systemUi,launcher,settings,false);}
 static String command(boolean systemUi,boolean launcher,boolean settings,boolean security){
  String result=command(systemUi,launcher);
  if(settings){String c=killPackageProcesses("com.android.settings");result=result==null?c:result+" && "+c;}
  if(security){String c=killPackageProcesses("com.miui.securitycenter");result=result==null?c:result+" && "+c;}
  return result;
 }
}

package io.github.yixing233.hyperduo.ui;
public final class RestartTargetPolicyTest {
 public static void main(String[] args) {
  check(RestartTargetPolicy.command(false,false)==null,"empty selection");
  check("killall com.android.systemui".equals(RestartTargetPolicy.command(true,false)),"systemui only");
  check("killall com.miui.home".equals(RestartTargetPolicy.command(false,true)),"launcher only");
  check("killall com.android.systemui && killall com.miui.home".equals(RestartTargetPolicy.command(true,true)),"both");
  System.out.println("PASS: restart target selection");
 }
 private static void check(boolean v,String n){if(!v)throw new AssertionError(n);}
}

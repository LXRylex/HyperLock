package io.github.yixing233.hyperduo;
public final class KeyboardInsetPolicyTest {
 public static void main(String[] args) {
  check(!KeyboardInsetPolicy.shouldAdjust(false,true,true,true,0),"opt-in");
  check(!KeyboardInsetPolicy.shouldAdjust(true,false,true,true,0),"keyboard hidden");
  check(!KeyboardInsetPolicy.shouldAdjust(true,true,false,true,0),"button navigation");
  check(!KeyboardInsetPolicy.shouldAdjust(true,true,true,false,0),"side navigation");
  check(!KeyboardInsetPolicy.shouldAdjust(true,true,true,true,1),"landscape");
  check(KeyboardInsetPolicy.shouldAdjust(true,true,true,true,0),"portrait keyboard");
  check(!KeyboardInsetPolicy.shouldAdjustSystem(false,true,true,0),"system-wide opt-in");
  check(!KeyboardInsetPolicy.shouldAdjustSystem(true,false,true,0),"system-wide button navigation");
  check(!KeyboardInsetPolicy.shouldAdjustSystem(true,true,false,0),"system-wide side navigation");
  check(!KeyboardInsetPolicy.shouldAdjustSystem(true,true,true,1),"system-wide landscape");
  check(KeyboardInsetPolicy.shouldAdjustSystem(true,true,true,0),"system-wide portrait");
  check(KeyboardInsetPolicy.compactHeight(130,2.75f)==28,"thin 10dp line");
  check(KeyboardInsetPolicy.compactHeight(12,2.75f)==12,"never increases inset");
  System.out.println("PASS: keyboard inset guards");
 }
 private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}


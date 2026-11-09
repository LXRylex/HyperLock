package io.github.yixing233.hyperduo;
public final class GestureSizeTest {
 public static void main(String[] args) {
  check(GestureStylePolicy.symbolHeight(40)==52,"30 percent larger");
  check(GestureStylePolicy.symbolHeight(0)==0,"invalid height");
  check(GestureStylePolicy.symbolWidth(52,3)==65,"wide inverted symbol");
  check(GestureStylePolicy.symbolWidth(52,1)==52,"square symbol");
  System.out.println("PASS: gesture symbol sizing");
 }
 private static void check(boolean v,String n){if(!v)throw new AssertionError(n);}
}

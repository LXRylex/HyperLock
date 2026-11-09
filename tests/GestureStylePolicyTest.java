package io.github.yixing233.hyperduo;
public final class GestureStylePolicyTest {
 public static void main(String[] args) {
  check(GestureStylePolicy.opacityAlpha(-5)==0,"clamp low");
  check(GestureStylePolicy.opacityAlpha(0)==0,"transparent");
  check(GestureStylePolicy.opacityAlpha(50)==128,"half opacity");
  check(GestureStylePolicy.opacityAlpha(100)==255,"opaque");
  check(GestureStylePolicy.opacityAlpha(120)==255,"clamp high");
  check(GestureStylePolicy.tint(0x80445566,0xffaabbcc)==0x80aabbcc,"preserve native alpha");
  check(GestureStylePolicy.mode(-1)==0,"invalid mode low");
  check(GestureStylePolicy.mode(3)==3,"image mode");
  check(GestureStylePolicy.mode(4)==0,"invalid mode high");
  check(GestureStylePolicy.shape(-1)==0,"invalid shape low");
  check(GestureStylePolicy.shape(6)==6,"last symbol supported");
  check(GestureStylePolicy.shape(7)==0,"invalid shape high");
  System.out.println("PASS: gesture opacity and tint policy");
 }
 private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}

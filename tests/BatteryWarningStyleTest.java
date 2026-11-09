package io.github.yixing233.hyperduo;
public final class BatteryWarningStyleTest {
 public static void main(String[] args) {
  BatteryWarningStyle d=BatteryWarningStyle.parse(null);
  check(!d.enabled && !d.image,"safe defaults");
  d.enabled=true; d.image=true; d.background=0x80876543; d.gotIt=0x40556677; d.batteryBody=0xff556677;
  BatteryWarningStyle round=BatteryWarningStyle.parse(d.encode());
  check(round.enabled && round.image && round.background==d.background && round.gotIt==d.gotIt && round.batteryBody==d.batteryBody,"round trip");
  d.titleText="Battery, low ♡"; d.messageText="Charge me\nplease!";
  round=BatteryWarningStyle.parse(d.encode());
  check(round.titleText.equals(d.titleText) && round.messageText.equals(d.messageText),"custom text Unicode round trip");
  check(BatteryWarningStyle.parse("1,1,0,-1,-1,-1,-1,-1").titleText.isEmpty(),"old settings remain compatible");
  check(!BatteryWarningStyle.parse("bad,input").enabled,"invalid falls back safely");
  check(BatteryWarningStyle.recolorPixel(0xffff4747,0xff201010,0xff00ff00)==0xff00ff00,"bright region");
  check(BatteryWarningStyle.recolorPixel(0x1aff271d,0xff0102ff,0xff00ff00)==0xff0102ff,"body region");
  check(BatteryWarningStyle.recolorPixel(0x00110000,0xffffffff,0xffffffff)==0,"transparent pixels");
  System.out.println("PASS: battery warning style and illustration colors");
 }
 private static void check(boolean x,String s){if(!x)throw new AssertionError(s);}
}

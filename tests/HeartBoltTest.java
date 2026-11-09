package io.github.yixing233.hyperduo;
public final class HeartBoltTest {
 private static int checks;
 private static void ok(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_HEART;s.glyphScale=100;
  s.heartPercentage=false;s.heartAnimation=false;s.heartAutoTint=false;s.heartCharging=0xff22cc77;s.heartFull=0xff7799ff;
  ok(!s.heartBolt,"bolt defaults off for existing installs");
  ok(!HeartBattery.shouldShowBolt(s,true),"disabled even when charging");
  s.heartBolt=true;
  ok(!HeartBattery.shouldShowBolt(s,false) && HeartBattery.shouldShowBolt(s,true),"bolt only while charging");
  ok(s.copy().heartBolt && TrioSettings.fromBundle(s.toBundle()).heartBolt,"setting survives copying and broadcast");
  TrioSettings other=s.copy();other.heartBolt=false;ok(!s.equals(other),"setting participates in equality");
  ok(s.applyKeyFrom(other,Prefs.KEY_HEART_BOLT) && !s.heartBolt,"setting updates live");s.heartBolt=true;
  ok(HeartBatteryLayout.measuredWidth(100,100,s,false)==100,"no unused slot when unplugged");
  ok(HeartBatteryLayout.measuredWidth(100,100,s,true)>100,"charging reserves bolt width");
  ok(HeartBatteryLayout.measuredWidth(100,0,s,true)==100,"unmeasured host unchanged");
  s.heartPercentage=true;s.heartPercentageRight=true;
  ok(HeartBatteryLayout.designWidth(s,true)>HeartBatteryLayout.designWidth(s,false),"percentage and bolt fit together");
  ok(HeartBatteryLayout.boltLeft(s)>210,"bolt sits after outside percentage");
  int width=(int)HeartBatteryLayout.designWidth(s,true);
  java.awt.image.BufferedImage img=new java.awt.image.BufferedImage(width,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  java.awt.Graphics2D g=img.createGraphics();HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),width,100,60,true,0xffffffff,s,false,new Object(),0);g.dispose();
  int painted=0;for(int y=0;y<100;y++)for(int x=210;x<width;x++)if((img.getRGB(x,y)>>>24)>0&&(img.getRGB(x,y)&0xffffff)==(s.heartCharging&0xffffff))painted++;
  ok(painted>25,"visible bolt follows charging color");
  s.heartBrightness=50;img=new java.awt.image.BufferedImage(width,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);g=img.createGraphics();HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),width,100,60,true,0xffffffff,s,false,new Object(),0);g.dispose();
  int dim=HeartBattery.dimColor(s.heartCharging,50), dimPixels=0;for(int y=0;y<100;y++)for(int x=210;x<width;x++)if(img.getRGB(x,y)==dim)dimPixels++;
  ok(dimPixels>10,"brightness applies to bolt");
  s.trioStyle=Prefs.STYLE_RING;ok(!HeartBattery.shouldShowBolt(s,true),"other styles unchanged");
  System.out.println("PASS: "+checks+" Heart charging bolt checks");
 }
}

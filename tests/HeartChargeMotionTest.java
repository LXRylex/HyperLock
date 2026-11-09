package io.github.yixing233.hyperduo;
public final class HeartChargeMotionTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        check(HeartChargeMotion.alpha(400)-HeartChargeMotion.alpha(1200)>=100,"charging pulse must have a clearly visible brightness range");
        check(Math.abs(HeartChargeMotion.surfaceY(20,37,0)-HeartChargeMotion.surfaceY(20,37,800))>7f,"liquid surface must move visibly");
        for(int level:new int[]{0,1,15,37,50,99,100})for(int x=0;x<=100;x+=10) {
            float a=HeartChargeMotion.surfaceY(x,level,0),b=HeartChargeMotion.surfaceY(x,level,1600);
            check(Math.abs(a-b)<.001f,"wave must loop without a jump");
            check(a>=6f && a<=90f,"wave stays within empty/full limits");
        }
        check(HeartChargeMotion.surfaceY(20,0,0)==90f && HeartChargeMotion.surfaceY(20,100,400)==6f,"0% and 100% keep exact endpoints");
        check(Math.abs(HeartChargeMotion.surfaceY(20,37,0)-HeartChargeMotion.surfaceY(20,37,50))<2f,"20 fps motion remains smooth");
        java.awt.image.BufferedImage a=frame(true,true,37,0),b=frame(true,true,37,400);
        check(different(a,b)>80,"actual animated renderer must change visible pixels");
        check(different(frame(false,true,37,0),frame(false,true,37,400))==0,"animation toggle off keeps straight static fill");
        check(different(frame(true,false,37,0),frame(true,false,37,400))==0,"unplugged battery stays still");
        check(different(frame(true,true,100,0),frame(true,true,100,400))==0,"full battery stays still");
        check(different(alwaysFrame(true,37,0),alwaysFrame(true,37,400))>80,"always animate visibly moves while unplugged");
        check(different(alwaysFrame(false,37,0),alwaysFrame(false,37,400))==0,"master animation off stops always mode");
        check(different(alwaysFrame(true,100,0),alwaysFrame(true,100,400))>20,"always mode visibly pulses at 100 percent");
        check(!HeartBattery.shouldAnimate(safe(false,true),false,37),"master animation controls always mode");
        check(!HeartBattery.shouldAnimate(safe(true,true),false,0),"empty battery stays empty");
        check((a.getRGB(0,0)>>>24)==0 && (b.getRGB(99,99)>>>24)==0,"wave remains clipped inside the heart");
        java.awt.image.BufferedImage strip=new java.awt.image.BufferedImage(480,145,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=strip.createGraphics();g.setColor(new java.awt.Color(0xff181a22,true));g.fillRect(0,0,480,145);
        for(int i=0;i<4;i++) {g.drawImage(frame(true,true,37,i*400),10+i*120,8,null);g.setColor(java.awt.Color.WHITE);g.drawString((i*400)+" ms",40+i*120,131);}
        g.dispose();javax.imageio.ImageIO.write(strip,"png",new java.io.File("work/heart-tests/charging-wave.png"));
        System.out.println("PASS: "+checks+" charging wave/pulse checks");
    }
    static TrioSettings safe(boolean enabled,boolean always){TrioSettings s=TrioSettings.defaults();s.heartAnimation=enabled;s.heartAnimateAlways=always;return s;}
    static java.awt.image.BufferedImage alwaysFrame(boolean animate,int level,long time){
        TrioSettings s=safe(animate,true);s.trioStyle=3;s.heartPercentage=false;s.glyphScale=100;
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),100,100,level,false,0xffffffff,s,true,new Object(),time);g.dispose();return image;
    }
    static java.awt.image.BufferedImage frame(boolean animate,boolean charging,int level,long time){
        TrioSettings s=TrioSettings.defaults();s.trioStyle=3;s.heartAnimation=animate;s.heartPercentage=false;s.glyphScale=100;
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();
        HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),100,100,level,charging,0xffffffff,s,true,new Object(),time);
        g.dispose();return image;
    }
    static int different(java.awt.image.BufferedImage a,java.awt.image.BufferedImage b){
        int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;return n;
    }
}

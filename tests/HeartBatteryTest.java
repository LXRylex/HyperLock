package io.github.yixing233.hyperduo;
public final class HeartBatteryTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(Prefs.normalizeTrioStyle(3)==3, "Heart style must survive normalization");
        check(Prefs.normalizeTrioStyle(4)==4, "Default must survive normalization");
        check(Prefs.normalizeTrioStyle(1)==0, "retired rectangle ID must remain retired");
        check(HeartBattery.fillTop(0)==90f && HeartBattery.fillTop(100)==6f, "fill endpoints");
        check(HeartBattery.fillTop(-25)==90f && HeartBattery.fillTop(900)==6f, "clamp invalid level");
        check(HeartBattery.fillTop(25)>HeartBattery.fillTop(75), "fill rises with level");
        TrioSettings s=TrioSettings.defaults(); s.trioStyle=3;
        s.heartNormal=0xff123456; s.heartCharging=0xff234567;
        s.heartLow=0xff345678; s.heartFull=0xff456789;
        s.heartPercentage=true; s.heartAnimation=true; s.heartAutoTint=false;
        check(HeartBattery.fillColor(s,70,false)==s.heartNormal, "normal color");
        check(HeartBattery.fillColor(s,5,false)==s.heartLow, "low color");
        check(HeartBattery.fillColor(s,5,true)==s.heartCharging, "charging outranks low");
        check(HeartBattery.fillColor(s,100,true)==s.heartFull, "full outranks charging");
        check(s.equals(s.copy()), "copy retains every Heart setting");
        TrioSettings roundTrip=TrioSettings.fromBundle(s.toBundle());
        check(roundTrip.heartNormal==s.heartNormal && roundTrip.heartPercentage && roundTrip.heartAnimation && !roundTrip.heartAutoTint, "bundle retains Heart settings");
        TrioSettings next=s.copy(); next.heartFull=0xffaabbcc;
        check(!s.equals(next), "Heart changes trigger UI recomposition");
        check(s.applyKeyFrom(next,Prefs.KEY_HEART_FULL) && s.heartFull==next.heartFull, "live key application");
        TrioAppearance a=TrioAppearance.of(s);
        check(a.glyph && !a.foldsMobile() && !a.ownsWifi() && !a.ownsType() && !a.perfAnywhere(), "Heart keeps native signal icons");
        s.signalMode=Prefs.SIGNAL_OUT_RING; s.stackedSignal=true;
        a=TrioAppearance.of(s);
        check(!a.stackedOut() && !a.foldsMobile(), "Heart must release inherited stacked mobile preferences");
        s.trioStyle=4;
        check(!TrioAppearance.of(s).glyph, "Default restores native drawing");
        s.trioStyle=0;
        check(TrioAppearance.of(s).glyph, "Ring still works");
        s.trioStyle=2;
        check(TrioAppearance.of(s).capsule, "Capsule still works");
        chargingOutline();
        raster();
        System.out.println("PASS: "+checks+" Heart battery checks");
    }
    private static void chargingOutline() {
        TrioSettings s=TrioSettings.defaults(); s.trioStyle=3; s.glyphScale=100;
        s.heartNormal=0xffffffff; s.heartCharging=0xffff3366; s.heartFull=0xff33cc77;
        s.heartPercentage=true; s.heartPercentageRight=true; s.heartAnimation=false; s.heartAutoTint=false;
        java.awt.image.BufferedImage unplugged=outlineFrame(s,28,false,0xff00ffff);
        java.awt.image.BufferedImage charging=outlineFrame(s,28,true,0xff00ffff);
        check(countInk(unplugged,0xffffffff,0,45)>20,"unplugged upper heart outline uses normal white");
        check(countInk(charging,0xffff3366,0,45)>20,"charging upper heart outline uses charging pink");
        check(countInk(charging,0xffffffff,0,45)==0,"charging outline no longer remains normal white");
        check(countInk(charging,0xffff3366,112,210)>20,"outside percentage follows charging outline");
        s.heartBrightness=50;
        int halfPink=HeartBattery.dimColor(s.heartCharging,50);
        java.awt.image.BufferedImage dimmed=outlineFrame(s,28,true,0xff00ffff);
        check(countInk(dimmed,halfPink,0,45)>20 && countInk(dimmed,halfPink,112,210)>20,"brightness dims the actual heart outline and outside percentage");
        s.heartBrightness=100;
        s.heartAutoTint=true;
        check(countInk(outlineFrame(s,28,true,0xff00ffff),0xff00ffff,0,45)>20,"automatic outline tint remains authoritative");
        s.heartAutoTint=false;
        check(countInk(outlineFrame(s,100,true,0xff00ffff),0xff33cc77,0,45)>20,"full battery uses full color while plugged in");
    }
    private static java.awt.image.BufferedImage outlineFrame(TrioSettings s,int level,boolean charging,int tint) {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(210,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();
        HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),210,100,level,charging,tint,s,true,new Object(),0);
        g.dispose();return image;
    }
    private static int countInk(java.awt.image.BufferedImage image,int color,int x0,int x1) {
        int found=0;
        for(int y=0;y<45;y++)for(int x=x0;x<x1;x++)if(image.getRGB(x,y)==color)found++;
        return found;
    }
    private static void raster() {
        TrioSettings s=TrioSettings.defaults(); s.trioStyle=3; s.glyphScale=100;
        s.heartNormal=0xffff6b9d; s.heartFull=s.heartNormal;
        s.heartPercentage=false; s.heartAnimation=false;
        int previous=-1; java.awt.image.BufferedImage empty=null;
        java.awt.image.BufferedImage sheet=new java.awt.image.BufferedImage(720,270,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D page=sheet.createGraphics();
        page.setColor(new java.awt.Color(0xff181a22,true)); page.fillRect(0,0,720,270);
        int[] levels={0,15,25,50,75,100};
        for(int i=0;i<levels.length;i++) {
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g=image.createGraphics();
            g.setColor(java.awt.Color.BLUE); g.fillRect(0,0,100,100);
            HeartBatteryRenderer.draw(new android.graphics.Canvas(g),100,100,levels[i],false,0xffffffff,s,true,new Object());
            g.dispose();
            check((image.getRGB(0,0)>>>24)==0,"Heart clears native pixels only inside its host");
            if(empty==null) empty=image;
            int fill=0;
            for(int y=0;y<100;y++) for(int x=0;x<100;x++)
                if((empty.getRGB(x,y)>>>24)==0 && (image.getRGB(x,y)&0xffffff)==(HeartBattery.fillColor(s,levels[i],false)&0xffffff) && (image.getRGB(x,y)>>>24)>0) fill++;
            check(fill>=previous,"filled area increases at "+levels[i]+"%");
            previous=fill;
            page.drawImage(image,i*120+10,18,null);
            page.setColor(java.awt.Color.WHITE); page.drawString(levels[i]+"%",i*120+47,138);
        }
        s.heartFull=Prefs.DEF_HEART_FULL;
        int[] special={5,63,100}; boolean[] charge={false,true,true};
        for(int i=0;i<3;i++) {
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(100,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g=image.createGraphics(); s.heartPercentage=true;
            HeartBatteryRenderer.draw(new android.graphics.Canvas(g),100,100,special[i],charge[i],0xffffffff,s,false,new Object());
            g.dispose(); page.drawImage(image,i*240+60,150,null);
            page.setColor(java.awt.Color.WHITE); page.drawString(new String[]{"Low","Charging","Full"}[i],i*240+93,263);
        }
        page.dispose();
        try { javax.imageio.ImageIO.write(sheet,"png",new java.io.File("work/heart-tests/heart-preview.png")); }
        catch(java.io.IOException e) { throw new RuntimeException(e); }
    }
}

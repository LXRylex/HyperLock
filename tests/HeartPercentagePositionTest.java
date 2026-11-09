package io.github.yixing233.hyperduo;
public final class HeartPercentagePositionTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        TrioSettings s=TrioSettings.defaults();s.trioStyle=3;s.heartPercentage=true;
        check(!s.heartPercentageRight,"existing installs retain inside placement");
        s.heartPercentageRight=true;
        check(HeartBatteryLayout.measuredWidth(79,57,s)>=120,"outside digits reserve their own width");
        check(s.equals(s.copy()),"position survives snapshot copying");
        check(TrioSettings.fromBundle(s.toBundle()).heartPercentageRight,"position survives settings broadcast");
        TrioSettings inside=s.copy();inside.heartPercentageRight=false;
        check(!s.equals(inside),"position changes refresh UI");
        check(s.applyKeyFrom(inside,Prefs.KEY_HEART_PERCENTAGE_RIGHT) && !s.heartPercentageRight,"live position update");
        check(HeartBatteryLayout.measuredWidth(79,57,s)==79,"inside preserves native icon width");
        s.heartPercentageRight=true;s.heartPercentage=false;
        check(HeartBatteryLayout.measuredWidth(79,57,s)==79,"hidden percentage does not reserve text width");
        check(HeartBatteryLayout.measuredWidth(79,0,s)==79,"unmeasured host remains unchanged");
        s.heartPercentage=true;s.heartAnimation=false;s.glyphScale=100;
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(210,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=image.createGraphics();
        HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(g),210,100,0,false,0xffffffff,s,false,new Object(),0);g.dispose();
        int digits=0,insideInk=0;
        for(int y=25;y<75;y++)for(int x=112;x<208;x++)if((image.getRGB(x,y)>>>24)>0)digits++;
        for(int y=30;y<55;y++)for(int x=35;x<65;x++)if((image.getRGB(x,y)>>>24)>0)insideInk++;
        check(digits>100,"numbers render on the physical right");
        check(insideInk==0,"no number remains inside heart in outside mode");
        java.awt.image.BufferedImage strip=new java.awt.image.BufferedImage(630,130,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        g=strip.createGraphics();g.setColor(new java.awt.Color(0xff181a22,true));g.fillRect(0,0,630,130);
        int[] levels={9,65,100};
        for(int i=0;i<3;i++) {
            image=new java.awt.image.BufferedImage(210,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D cell=image.createGraphics();
            HeartBatteryRenderer.drawAtTime(new android.graphics.Canvas(cell),210,100,levels[i],false,0xffffffff,s,false,new Object(),0);cell.dispose();
            int edge=0;for(int y=0;y<100;y++)if((image.getRGB(209,y)>>>24)>0)edge++;
            check(edge==0,"1/2/3 digit text stays within allocated width");
            g.drawImage(image,i*210,0,null);
        }
        g.dispose();javax.imageio.ImageIO.write(strip,"png",new java.io.File("work/heart-tests/percentage-right.png"));
        System.out.println("PASS: "+checks+" percentage placement checks");
    }
}

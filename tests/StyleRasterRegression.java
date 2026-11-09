package io.github.yixing233.hyperduo;
public final class StyleRasterRegression {
    public static void main(String[] args) throws Exception {
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        int frames=0;
        for(int style:new int[]{0,2}) for(int level:new int[]{0,5,15,50,75,100})
            for(boolean charging:new boolean[]{false,true}) for(int ink:new int[]{0xffffffff,0xff000000}) {
                TrioSettings s=TrioSettings.defaults(); s.trioStyle=style;
                java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(180,180,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g=image.createGraphics();
                TrioRenderer.drawInto(new android.graphics.Canvas(g),180,180,level,charging,false,level<20,3,4,ink,s);
                g.dispose();
                for(int y=0;y<180;y++) for(int x=0;x<180;x++) {
                    int pixel=image.getRGB(x,y);
                    digest.update((byte)(pixel>>>24)); digest.update((byte)(pixel>>>16));
                    digest.update((byte)(pixel>>>8)); digest.update((byte)pixel);
                }
                frames++;
            }
        System.out.println(frames+" frames "+java.util.HexFormat.of().formatHex(digest.digest()));
    }
}

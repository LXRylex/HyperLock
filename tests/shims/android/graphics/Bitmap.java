package android.graphics;
public final class Bitmap {
    public enum Config { ARGB_8888 }
    public final java.awt.image.BufferedImage image;
    private Bitmap(int w,int h) { image=new java.awt.image.BufferedImage(w,h,java.awt.image.BufferedImage.TYPE_INT_ARGB); }
    public static Bitmap createBitmap(int w,int h,Config c) { return new Bitmap(w,h); }
    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }
    public void eraseColor(int color) {
        java.awt.Graphics2D g=image.createGraphics();
        try { g.setComposite(java.awt.AlphaComposite.Src); g.setColor(new java.awt.Color(color,true)); g.fillRect(0,0,getWidth(),getHeight()); }
        finally { g.dispose(); }
    }
}

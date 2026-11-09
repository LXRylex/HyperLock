// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
/** Versioned, fail-safe low-battery warning appearance. Colors use ARGB. */
public final class BatteryWarningStyle {
    public boolean enabled, image;
    public int background=0xff202020, gotIt=0xff424242, saver=0xff4984f5;
    public int batteryBody=0xff372020, batteryFill=0xffff4040;
    public String titleText="", messageText="";
    public static BatteryWarningStyle parse(String value) {
        BatteryWarningStyle fallback=new BatteryWarningStyle();
        if(value==null || value.length()>8192) return fallback;
        try {
            String[] x=value.split(",",-1);
            if((x.length!=8 && x.length!=10) || !"1".equals(x[0]) || !("0".equals(x[1])||"1".equals(x[1])) || !("0".equals(x[2])||"1".equals(x[2]))) return fallback;
            BatteryWarningStyle s=new BatteryWarningStyle();
            s.enabled="1".equals(x[1]);s.image="1".equals(x[2]);
            s.background=Integer.parseInt(x[3]);s.gotIt=Integer.parseInt(x[4]);s.saver=Integer.parseInt(x[5]);
            s.batteryBody=Integer.parseInt(x[6]);s.batteryFill=Integer.parseInt(x[7]);
            if(x.length==10) {
                s.titleText=new String(java.util.Base64.getDecoder().decode(x[8]),java.nio.charset.StandardCharsets.UTF_8);
                s.messageText=new String(java.util.Base64.getDecoder().decode(x[9]),java.nio.charset.StandardCharsets.UTF_8);
                if(s.titleText.length()>256 || s.messageText.length()>512)return fallback;
            }
            return s;
        } catch(RuntimeException ignored){return fallback;}
    }
    public String encode(){return "1,"+(enabled?1:0)+","+(image?1:0)+","+background+","+gotIt+","+saver+","+batteryBody+","+batteryFill+","+encodeText(titleText)+","+encodeText(messageText);}
    private static String encodeText(String text){return java.util.Base64.getEncoder().encodeToString((text==null?"":text).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public static int recolorPixel(int pixel,int body,int fill) {
        int alpha=(pixel>>>24)&255;if(alpha==0)return 0;
        boolean bright=alpha>128;
        int chosen=bright?fill:body;
        int coverage=bright?alpha:Math.min(255,(alpha*255+13)/26);
        int out=(coverage*((chosen>>>24)&255)+127)/255;
        return (out<<24)|(chosen&0x00ffffff);
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
public final class ControlCenterImagePolicy {
    private ControlCenterImagePolicy(){}
    public static boolean validSlot(String s){return "battery_warning".equals(s)||"gesture".equals(s)||"mobile".equals(s)||"airplane".equals(s)||"wifi".equals(s)||"bluetooth".equals(s)||"music".equals(s)||"brightness".equals(s)||"volume".equals(s);}
    public static String tileSlot(String spec){if("wifi".equals(spec)||"internet".equals(spec))return "wifi";if("cell".equals(spec))return "mobile";if("airplane".equals(spec))return "airplane";if("bt".equals(spec)||"bluetooth".equals(spec))return "bluetooth";return null;}
    public static java.util.List<String> cardSpecs(java.util.List<String> original,boolean enabled){
        if(!enabled || original==null)return original;
        java.util.ArrayList<String> copy=new java.util.ArrayList<>(original);
        if(!copy.contains("bt") && !copy.contains("bluetooth"))copy.add("bt");
        if(!copy.contains("airplane"))copy.add("airplane");
        return copy;
    }
    /** White native active tiles need an inactive-style backdrop for light icons. */
    public static boolean needsDarkTileBackground(boolean active,boolean enabled,int color){
        if(!active || !enabled)return false;
        double r=linear((color>>16)&255),g=linear((color>>8)&255),b=linear(color&255);
        return .2126*r+.7152*g+.0722*b>0.30;
    }
    private static double linear(int c){double v=c/255.0;return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);}
    public static int brightness(int value){return Math.max(0,Math.min(100,value));}
}

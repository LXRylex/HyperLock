package io.github.yixing233.hyperduo;

public final class NotificationColorPolicyTest {
    public static void main(String[] args) {
        check(!NotificationColorPolicy.drawBackground(false, 0xffcc4488), "disabled background");
        check(!NotificationColorPolicy.drawBackground(true, 0x00cc4488), "transparent background");
        check(NotificationColorPolicy.drawBackground(true, 0x80cc4488), "visible background");
        check(NotificationColorPolicy.keepAlpha(0x36ffffff, 0xff123456) == 0x36123456, "picker preserves opacity");
        check(NotificationColorPolicy.tintMediaBackground("media_bg", true, 0xff000000), "opaque media background");
        check(!NotificationColorPolicy.tintMediaBackground("media_bg", true, 0x00000000), "transparent media background");
        check(!NotificationColorPolicy.tintMediaBackground("album_art_image", true, 0xff000000), "preserve album art");
        check(!NotificationColorPolicy.tintMediaBackground("media_bg", false, 0xff000000), "disabled media tint");
        java.lang.reflect.Method privateWidth = NotificationColorPolicy.findWidthGetter(PrivateWidth.class);
        check(privateWidth != null && NotificationColorPolicy.readWidth(privateWidth, new PrivateWidth(), 99) == 42, "private HyperOS width method");
        check(NotificationColorPolicy.readWidth(NotificationColorPolicy.findWidthGetter(NoWidth.class), new NoWidth(), 99) == 99, "missing width falls back to view width");
        System.out.println("PASS: notification color policy");
    }
    private static void check(boolean v, String name) { if (!v) throw new AssertionError(name); }
    private static final class PrivateWidth { private int getActualWidth() { return 42; } }
    private static final class NoWidth { }
}

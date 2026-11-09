package io.github.yixing233.hyperduo;
public final class SettingsColorChannelsTest {
 public static void main(String[] args) {
  int c=0x80643219;
  check(SettingsColorChannels.channel(c,16)==100,"red");
  check(SettingsColorChannels.channel(c,8)==50,"green");
  check(SettingsColorChannels.channel(c,0)==25,"blue");
  check(SettingsColorChannels.withChannel(c,8,300)==0x8064ff19,"clamp and preserve channels");
  check(SettingsColorChannels.withOpacity(c,0)==0x00643219,"transparent");
  check(SettingsColorChannels.withOpacity(c,100)==0xff643219,"opaque");
  check(SettingsColorChannels.opacity(SettingsColorChannels.withOpacity(c,50))==50,"half");
  check(SettingsColorChannels.applyOpacity(0x80445566, 50)==0x40445566,"native alpha scales without tint");
  check(SettingsColorChannels.applyOpacity(0xffabcdef, 0)==0x00abcdef,"native transparent");
  check(SettingsColorChannels.applyOpacity(0x80445566, 100)==0x80445566,"native unchanged");
  System.out.println("PASS: Settings RGB and opacity");
 }
 private static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
}


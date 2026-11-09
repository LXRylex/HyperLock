package io.github.yixing233.hyperduo;
public final class SettingsColorTest {
 public static void main(String[] args) {
  TrioSettings s=TrioSettings.defaults();
  if(s.settingsColorsEnabled) throw new AssertionError("must default off");
  s.settingsColorsEnabled=true; s.settingsCardColor=0xff9b6484;
  TrioSettings copy=s.copy(), delivered=TrioSettings.fromBundle(s.toBundle());
  if(!copy.settingsColorsEnabled || copy.settingsCardColor!=s.settingsCardColor || !delivered.settingsColorsEnabled || delivered.settingsCardColor!=s.settingsCardColor) throw new AssertionError("settings must survive copy and reload");
  s.batteryWarningStyle="1,1,1,-1,-2,-3,-4,-5";
  if(!s.batteryWarningStyle.equals(s.copy().batteryWarningStyle) || !s.batteryWarningStyle.equals(TrioSettings.fromBundle(s.toBundle()).batteryWarningStyle)) throw new AssertionError("warning style transfer");
  System.out.println("PASS: Settings color and warning style transfer");
 }
}


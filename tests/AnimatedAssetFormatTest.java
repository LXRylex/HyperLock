package io.github.yixing233.hyperduo;
import java.util.Arrays;
public class AnimatedAssetFormatTest {
 public static void main(String[] args){
  byte[] gif={71,73,70,56,57,97,2,0,2,0};
  byte[] wrapped=AnimatedAssetFormat.pack(gif,20,10,2,1,10,5);
  AnimatedAssetFormat.Asset a=AnimatedAssetFormat.parse(wrapped);
  check(a!=null && Arrays.equals(gif,a.bytes),"bytes survive");
  check(a.left==10000 && a.top==10000 && a.right==60000 && a.bottom==60000,"crop coordinates");
  check(AnimatedAssetFormat.parse(new byte[]{1,2,3})==null,"PNG and invalid input remain untouched");
  check(AnimatedAssetFormat.parse(Arrays.copyOf(wrapped,wrapped.length-1))==null,"truncation rejected");
  byte[] large=new byte[12_550_535]; System.arraycopy(gif,0,large,0,gif.length);
  check(AnimatedAssetFormat.parse(AnimatedAssetFormat.pack(large,24,24,0,0,24,24))!=null,"12 MB GIF supported");
  System.out.println("PASS: animated asset format and crop");
 }
 private static void check(boolean value,String what){if(!value)throw new AssertionError(what);}
}


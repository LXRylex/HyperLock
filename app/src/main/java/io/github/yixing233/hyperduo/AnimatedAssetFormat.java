// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import java.nio.ByteBuffer;
import java.util.Arrays;
/** Bounded container holding the original GIF and a normalized, non-destructive crop. */
public final class AnimatedAssetFormat {
 private static final int MAGIC=0x484c4731, MAX=16*1024*1024;
 private AnimatedAssetFormat(){}
 public static final class Asset {
  public final int left,top,right,bottom; public final byte[] bytes;
  Asset(int l,int t,int r,int b,byte[] data){left=l;top=t;right=r;bottom=b;bytes=data;}
 }
 public static byte[] pack(byte[] gif,int width,int height,int left,int top,int w,int h){
  if(gif==null||gif.length>MAX-24||gif.length<10||gif[0]!='G'||gif[1]!='I'||gif[2]!='F'||width<=0||height<=0||left<0||top<0||w<=0||h<=0||left+w>width||top+h>height)throw new IllegalArgumentException("Invalid GIF crop");
  ByteBuffer b=ByteBuffer.allocate(24+gif.length);b.putInt(MAGIC).putInt((int)(100000L*left/width)).putInt((int)(100000L*top/height)).putInt((int)(100000L*(left+w)/width)).putInt((int)(100000L*(top+h)/height)).putInt(gif.length).put(gif);return b.array();
 }
 public static Asset parse(byte[] data){
  if(data==null||data.length<34||data.length>MAX)return null;
  ByteBuffer b=ByteBuffer.wrap(data);if(b.getInt()!=MAGIC)return null;
  int l=b.getInt(),t=b.getInt(),r=b.getInt(),bottom=b.getInt(),n=b.getInt();
  if(l<0||t<0||r>100000||bottom>100000||l>=r||t>=bottom||n!=b.remaining()||n<10)return null;
  byte[] bytes=Arrays.copyOfRange(data,24,data.length);if(bytes[0]!='G'||bytes[1]!='I'||bytes[2]!='F')return null;
  return new Asset(l,t,r,bottom,bytes);
 }
}

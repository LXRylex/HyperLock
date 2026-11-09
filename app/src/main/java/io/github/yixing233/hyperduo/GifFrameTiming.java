// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import java.util.ArrayList;
/** Reads per-frame GIF delays so playback is not artificially capped. */
public final class GifFrameTiming {
 private final int[] ends;private final int duration;
 private GifFrameTiming(ArrayList<Integer> frames){ends=new int[frames.size()];int sum=0;for(int i=0;i<ends.length;i++){sum+=frames.get(i);ends[i]=sum;}duration=sum;}
 public int duration(){return duration;}
 public int nextDelay(long time){int phase=(int)(Math.max(0,time)%duration);for(int end:ends)if(phase<end)return Math.max(1,end-phase);return 1;}
 public static GifFrameTiming parse(byte[] data){
  if(data==null||data.length<13||data[0]!='G'||data[1]!='I'||data[2]!='F')return null;
  try {
   int packed=data[10]&255, i=13;
   if((packed&128)!=0)i+=3*(1<<((packed&7)+1));
   int delay=100;ArrayList<Integer> frames=new ArrayList<>();long sum=0;
   while(i<data.length){
    int kind=data[i++]&255;
    if(kind==0x3b)break;
    if(kind==0x21){
     int label=data[i++]&255;
     if(label==0xf9){int n=data[i++]&255;if(n!=4||i+4>=data.length)return null;int hundredths=(data[i+1]&255)|((data[i+2]&255)<<8);delay=hundredths<2?100:hundredths*10;i+=4;if((data[i++]&255)!=0)return null;}
     else i=skip(data,i);
    } else if(kind==0x2c){
     if(i+9>data.length)return null;int flags=data[i+8]&255;i+=9;if((flags&128)!=0)i+=3*(1<<((flags&7)+1));
     if(i>=data.length)return null;i++;i=skip(data,i);
     if(frames.size()>=10000 || (sum+=delay)>Integer.MAX_VALUE)return null;frames.add(delay);delay=100;
    } else return null;
    if(i<0||i>data.length)return null;
   }
   return frames.isEmpty()?null:new GifFrameTiming(frames);
  } catch(RuntimeException ignored){return null;}
 }
 private static int skip(byte[] data,int index){while(true){if(index>=data.length)throw new IllegalArgumentException();int n=data[index++]&255;if(n==0)return index;index+=n;if(index>data.length)throw new IllegalArgumentException();}}
}

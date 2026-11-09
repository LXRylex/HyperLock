package io.github.yixing233.hyperduo;
import java.util.Base64;
public class GifFrameTimingTest {
 public static void main(String[] args){
  byte[] gif=Base64.getDecoder().decode("R0lGODlhGAAQAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQAHgAAACwAAAAAGAAQAAAIIgABCBxIsKDBgwgTKlzIsKHDhxAjSpxIsaLFixgzatxoMCAAIfkEAR4AAQAsAAAAABgAEACBAAD/AAAAAAAAAAAACCIAAQgcSLCgwYMIEypcyLChw4cQI0qcSLGixYsYM2rcaDAgADs=");
  GifFrameTiming t=GifFrameTiming.parse(gif);
  check(t!=null && t.duration()==600,"two frame duration");
  check(t.nextDelay(0)==300 && t.nextDelay(299)==1 && t.nextDelay(300)==300 && t.nextDelay(600)==300,"follow GIF frame boundaries");
  byte[] varied=Base64.getDecoder().decode("R0lGODlhBAAEAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQABAAAACwAAAAABAAEAAAICQABCBxIsCCAgAAh+QQBDQABACwAAAAABAAEAIEAgAAAAAAAAAAAAAAICQABCBxIsCCAgAAh+QQBBwABACwAAAAABAAEAIEAAP8AAAAAAAAAAAAICQABCBxIsCCAgAA7");
  GifFrameTiming variable=GifFrameTiming.parse(varied);
  check(variable!=null && variable.duration()==240 && variable.nextDelay(0)==40 && variable.nextDelay(40)==130 && variable.nextDelay(170)==70,"variable frame durations preserved");
  check(GifFrameTiming.parse(new byte[]{1,2,3})==null,"invalid is safe");
  System.out.println("PASS: GIF original frame timing");
 }
 private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
}

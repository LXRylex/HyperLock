// SPDX-License-Identifier: GPL-3.0-only
package io.github.yixing233.hyperduo;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
/** Fixed private images: SystemUI and our app, plus only the gesture image for the Xiaomi launcher. */
public final class ControlCenterImageProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        List<String> parts=uri.getPathSegments();
        if(!"r".equals(mode)||parts.size()!=2||!"image".equals(parts.get(0))||!ControlCenterImagePolicy.validSlot(parts.get(1)))throw new FileNotFoundException("Unsupported image");
        int uid=Binder.getCallingUid();
        if(uid!=Process.SYSTEM_UID && uid!=Process.myUid()) {
            boolean allowed=false;
            if("gesture".equals(parts.get(1))) try {
                allowed=uid==getContext().getPackageManager().getApplicationInfo("com.miui.home",0).uid;
            } catch (Exception ignored) { }
            if(!allowed)throw new SecurityException("Image unavailable to caller");
        }
        return ParcelFileDescriptor.open(new File(new File(getContext().getFilesDir(),"cc-images"),parts.get(1)+".png"),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        int uid=Binder.getCallingUid();
        if (uid!=Process.SYSTEM_UID && uid!=Process.myUid()) throw new SecurityException("Unavailable to caller");
        android.os.Bundle result=new android.os.Bundle();
        if ("media_boost_enabled".equals(method)) {
            result.putBoolean("enabled",getContext().getSharedPreferences("media_boost_settings",0).getBoolean("enabled",false));
            return result;
        }
        if (!"charging_sound_volume".equals(method)) return super.call(method,arg,extras);
        int volume=getContext().getSharedPreferences("charging_sound_settings",0).getInt("volume",100);
        result.putInt("volume",Math.max(0,Math.min(300,volume)));
        return result;
    }
    @Override public String getType(Uri u){return "image/png";}
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}

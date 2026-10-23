package cn.local.phonesleep;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c, Intent i){
  if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && c.getSharedPreferences("link",0).getBoolean("enabled",false))
   try { c.startForegroundService(new Intent(c, LinkService.class)); } catch(Exception ignored){}
 }
}

package cn.local.phonesleep;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
public class SleepAccess extends AccessibilityService {
 public static volatile SleepAccess instance;
 protected void onServiceConnected(){instance=this;}
 public void onAccessibilityEvent(AccessibilityEvent e){}
 public void onInterrupt(){}
 public void onDestroy(){instance=null;super.onDestroy();}
}

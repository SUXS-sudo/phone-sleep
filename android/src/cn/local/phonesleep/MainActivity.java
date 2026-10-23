package cn.local.phonesleep;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.security.*;
public class MainActivity extends Activity {
 final int BG=Color.rgb(244,247,252),INK=Color.rgb(27,39,61),MUTED=Color.rgb(115,129,149),BLUE=Color.rgb(53,104,238),PALE=Color.rgb(234,240,255);
 TextView state,description,code,permission,authorizationHint;Button connect,authorize;android.content.SharedPreferences prefs;Handler handler=new Handler();
 boolean flowActive,flowWaiting;int attempted;
 final Runnable continueFlow=()->{if(flowActive&&!flowWaiting)advanceAuthorization();};
 final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 static void init(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("link",0);
  if(!p.contains("key")){byte[] k=new byte[16];new SecureRandom().nextBytes(k);p.edit().putString("key",hex(k)).putString("id",java.util.UUID.randomUUID().toString()).apply();}
  if(p.getInt("schema",0)<3){try{boolean cleared=c.getSharedPreferences("pin",0).edit().clear().commit();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias("phonesleep.pin.v1"))store.deleteEntry("phonesleep.pin.v1");if(cleared)p.edit().putInt("schema",3).apply();}catch(Exception ignored){}}
 }
 int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
 void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
 LinearLayout card(LinearLayout parent){LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(22),dp(22),dp(22),dp(22));c.setBackground(background(Color.WHITE,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(16);parent.addView(c,lp);return c;}
 Button button(String title,boolean primary,Runnable action){Button b=new Button(this);b.setText(title);b.setTextSize(15);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?Color.WHITE:BLUE);b.setBackground(background(primary?BLUE:PALE,12));b.setStateListAnimator(null);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setOnClickListener(v->action.run());return b;}
 void addButton(LinearLayout p,Button b){p.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));}
 public void onCreate(Bundle saved){super.onCreate(saved);init(this);prefs=getSharedPreferences("link",0);if(saved!=null){flowActive=saved.getBoolean("flowActive");flowWaiting=saved.getBoolean("flowWaiting");attempted=saved.getInt("attempted");}
  getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(24),dp(28),dp(24),dp(28));scroll.addView(root);setContentView(scroll);
  root.addView(text("一键息屏",32,INK,true));gap(root,8);root.addView(text("连接一次，以后只需轻按一下。",14,MUTED,false));gap(root,28);
  LinearLayout connection=card(root);state=text("等待开启连接",21,INK,true);connection.addView(state);gap(connection,10);description=text("开启后，电脑会在同一 Wi-Fi 下自动找到手机。",14,MUTED,false);connection.addView(description);gap(connection,22);
  connect=button("开始授权并连接",true,()->{if(LinkService.running){prefs.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,LinkService.class));handler.postDelayed(()->refresh(),300);}else beginAuthorization();});addButton(connection,connect);
  LinearLayout pairing=card(root);pairing.addView(text("连接码",18,INK,true));gap(pairing,6);pairing.addView(text("首次在电脑输入，之后无需重复配对。",13,MUTED,false));gap(pairing,18);
  code=text("••••  ••••  ••••  ••••",17,BLUE,true);code.setTypeface(Typeface.MONOSPACE);code.setTextIsSelectable(true);code.setGravity(Gravity.CENTER);code.setPadding(dp(8),dp(18),dp(8),dp(18));code.setBackground(background(PALE,12));pairing.addView(code,new LinearLayout.LayoutParams(-1,-2));gap(pairing,14);
  LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(0);pairing.addView(buttons,new LinearLayout.LayoutParams(-1,dp(46)));
  Button show=button("显示连接码",false,()->{String k=prefs.getString("key","");code.setText(k.substring(0,8)+"\n"+k.substring(8,16)+"\n"+k.substring(16,24)+"\n"+k.substring(24));});LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.rightMargin=dp(8);buttons.addView(show,left);
  buttons.addView(button("复制",false,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("连接码",prefs.getString("key","")));Toast.makeText(this,"已复制连接码",0).show();}),new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout setup=card(root);setup.addView(text("授权助手",18,INK,true));gap(setup,10);permission=text("自动检查所需权限",13,MUTED,false);setup.addView(permission);gap(setup,14);
  authorize=button("开始授权",false,()->beginAuthorization());addButton(setup,authorize);gap(setup,12);
  authorizationHint=text("自动打开系统确认页，已授权的步骤会跳过。息屏授权采用系统锁屏；下次唤醒可能需要输入密码。",12,MUTED,false);setup.addView(authorizationHint);
  Button background=button("vivo 后台运行指引",false,()->backgroundGuide());addButton(root,background);gap(root,12);Button more=button("更多设置",false,()->more());addButton(root,more);gap(root,20);TextView footer=text("局域网直连 · 无会员 · 无广告\n仅用于息屏，不读取屏幕内容",12,MUTED,false);footer.setGravity(Gravity.CENTER);root.addView(footer);
 }
 void appDetails(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){Toast.makeText(this,"请在手机设置中打开本应用的信息页",0).show();}}
 void backgroundGuide(){startActivity(new Intent(this,BackgroundSetupActivity.class));}
 void more(){new AlertDialog.Builder(this).setTitle("更多设置").setItems(new String[]{"无障碍息屏授权","vivo 自启动 / 后台设置","重置连接码"},(dialog,index)->{if(index==0)openAccessibility(false);else if(index==1)backgroundGuide();else new AlertDialog.Builder(this).setTitle("重置连接码？").setMessage("已配对电脑将断开，需要输入新的连接码。").setPositiveButton("重置",(d,w)->{byte[] k=new byte[16];new SecureRandom().nextBytes(k);prefs.edit().putString("key",hex(k)).apply();code.setText("已重置，请重新显示连接码");}).setNegativeButton("取消",null).show();}).show();}
 boolean canLock(){
  if(SleepAccess.instance!=null||((DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE)).isAdminActive(new ComponentName(this,SleepAdmin.class)))return true;
  android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
  for(android.accessibilityservice.AccessibilityServiceInfo info:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)){android.content.pm.ServiceInfo service=info.getResolveInfo().serviceInfo;if(getPackageName().equals(service.packageName)&&SleepAccess.class.getName().equals(service.name))return true;}return false;
 }
 boolean notificationsReady(){return Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0;}
 boolean batteryReady(){return ((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());}
 void beginAuthorization(){if(flowActive)return;flowActive=true;flowWaiting=false;attempted=0;advanceAuthorization();}
 void scheduleAuthorization(){handler.removeCallbacks(continueFlow);handler.postDelayed(continueFlow,450);}
 void advanceAuthorization(){
  if(!flowActive||flowWaiting||isFinishing())return;
  int step=AuthorizationPlan.next(canLock(),notificationsReady(),batteryReady(),attempted);attempted|=step;
  if(step==AuthorizationPlan.STOP){flowActive=false;authorizationHint.setText("息屏权限尚未确认。本次已停止，点击「开始授权」可重试。也可在更多设置中选择无障碍授权。");refresh();return;}
  if(step==AuthorizationPlan.DONE){flowActive=false;startConnection();authorizationHint.setText(notificationsReady()&&batteryReady()?"系统授权已完成，连接已开启。":"连接已开启。部分通知或后台选项未确认，可稍后检查。");refresh();if(BackgroundSetupActivity.isVivo()&&!BackgroundSetupActivity.complete(this))handler.postDelayed(()->{if(!isFinishing())startActivity(new Intent(this,BackgroundSetupActivity.class).putExtra("autoStart",true));},500);return;}
  flowWaiting=true;refresh();
  if(step==AuthorizationPlan.LOCK){permission.setText("1 / 3 · 请在系统页面确认息屏授权");Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,new ComponentName(this,SleepAdmin.class));i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"仅申请关闭并锁定屏幕的权限，不读取数据。激活后下次唤醒可能需要输入密码。");try{startActivityForResult(i,210);}catch(Exception e){openAccessibility(true);}return;}
  if(step==AuthorizationPlan.NOTIFICATIONS){permission.setText("2 / 3 · 请确认连接通知");try{requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},211);}catch(Exception e){flowWaiting=false;scheduleAuthorization();}return;}
  permission.setText("3 / 3 · 请确认允许后台连接");
  try{startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())),210);}catch(Exception e){flowWaiting=false;authorizationHint.setText("系统未提供快捷后台确认入口，可稍后在更多设置中检查。");scheduleAuthorization();}
 }
 void openAccessibility(boolean fromFlow){Intent direct=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,SleepAccess.class));try{if(fromFlow)startActivityForResult(direct,210);else startActivity(direct);}catch(Exception e){try{Intent general=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);if(fromFlow)startActivityForResult(general,210);else startActivity(general);}catch(Exception ignored){if(fromFlow){flowWaiting=false;scheduleAuthorization();}Toast.makeText(this,"请在系统无障碍设置中启用一键息屏",1).show();}}}
 void startConnection(){try{prefs.edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,LinkService.class));handler.postDelayed(()->refresh(),500);}catch(Exception e){prefs.edit().putBoolean("enabled",false).apply();Toast.makeText(this,"连接未启动，请重新打开应用后再试",1).show();}}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==210&&flowActive){flowWaiting=false;scheduleAuthorization();}}
 public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==211&&flowActive){flowWaiting=false;scheduleAuthorization();}}
 protected void onSaveInstanceState(Bundle out){out.putBoolean("flowActive",flowActive);out.putBoolean("flowWaiting",flowWaiting);out.putInt("attempted",attempted);super.onSaveInstanceState(out);}
 protected void onResume(){super.onResume();handler.removeCallbacks(refreshLoop);handler.post(refreshLoop);if(flowActive&&!flowWaiting)scheduleAuthorization();if(prefs!=null&&prefs.getBoolean("enabled",false)&&(!LinkService.running||!LinkService.discoveryReady||!LinkService.commandsReady))startConnection();}
 protected void onPause(){handler.removeCallbacks(refreshLoop);super.onPause();}
 void refresh(){if(state==null)return;boolean ready=canLock(),healthy=LinkService.running&&LinkService.discoveryReady&&LinkService.commandsReady;state.setText(healthy?"●  连接服务已就绪":(LinkService.running?"正在恢复连接…":"等待开启连接"));state.setTextColor(healthy?BLUE:INK);description.setText(healthy?"看视频时断连，请检查下方 vivo 后台运行指引。":"点击下方按钮，确认系统授权后自动连接。");connect.setText(LinkService.running?"暂停连接":(ready?"开启连接":"开始授权并连接"));connect.setEnabled(!flowActive);authorize.setEnabled(!flowActive);authorize.setText(flowActive?"等待你确认…":(ready?"检查授权":"开始授权"));if(!flowActive)permission.setText((ready?"息屏 ✓":"息屏待确认")+"  ·  "+(notificationsReady()?"通知 ✓":"通知待确认")+"  ·  "+(batteryReady()?"省电豁免 ✓":"省电豁免待确认"));permission.setTextColor(ready?Color.rgb(36,151,113):MUTED);}
}

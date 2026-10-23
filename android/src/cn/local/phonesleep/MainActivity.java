package cn.local.phonesleep;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.security.*;

/**
 * 手机端主界面与按需授权向导。
 *
 * 授权策略：进入时先检查，只对确实缺失的项目打开系统页面；用户拒绝后本轮不再重复弹窗，
 * 但「开始授权」按钮始终可再次点击重试。系统不允许应用自行授予的权限只给最短操作路径。
 */
public class MainActivity extends Activity {
 private static final int REQUEST_SYSTEM=210,REQUEST_NOTIFICATIONS=211;

 TextView state,description,code,permission,authorizationHint,deviceLine,compatNote;
 TextView connect,authorize,background;
 android.content.SharedPreferences prefs;
 Handler handler=new Handler();
 VendorProfile profile;
 boolean flowActive,flowWaiting;
 int attempted;

 final Runnable continueFlow=()->{if(flowActive&&!flowWaiting)advanceAuthorization();};
 final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};

 static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}

 static void init(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("link",0);
  if(!p.contains("key")){byte[] k=new byte[16];new SecureRandom().nextBytes(k);p.edit().putString("key",hex(k)).putString("id",java.util.UUID.randomUUID().toString()).apply();}
  if(p.getInt("schema",0)<3){try{boolean cleared=c.getSharedPreferences("pin",0).edit().clear().commit();KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias("phonesleep.pin.v1"))store.deleteEntry("phonesleep.pin.v1");if(cleared)p.edit().putInt("schema",3).apply();}catch(Exception ignored){}}
 }

 /** 只撤销本应用自己的旧设备管理员授权，不触碰其他应用。 */
 static void retireLegacyAdmin(Context c){try{DevicePolicyManager manager=(DevicePolicyManager)c.getSystemService(DEVICE_POLICY_SERVICE);ComponentName own=new ComponentName(c,SleepAdmin.class);if(manager.isAdminActive(own))manager.removeActiveAdmin(own);}catch(Exception ignored){}}

 int dp(float v){return Style.dp(this,v);}

 public void onCreate(Bundle saved){
  super.onCreate(saved);
  init(this);retireLegacyAdmin(this);
  prefs=getSharedPreferences("link",0);
  profile=VendorProfile.detect();
  if(saved!=null){flowActive=saved.getBoolean("flowActive");flowWaiting=saved.getBoolean("flowWaiting");attempted=saved.getInt("attempted");}

  getWindow().setStatusBarColor(Style.BG);
  getWindow().setNavigationBarColor(Style.BG);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  scroll.setBackgroundColor(Style.BG);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(dp(20),dp(28),dp(20),dp(32));
  scroll.addView(root);
  setContentView(scroll);

  // 标题区：字号收敛到 22sp，副标题与标题之间留足间距。
  Style.add(root,Style.title(this,"一键息屏"));
  Style.gap(root,6);
  deviceLine=Style.body(this,profile.describe());
  Style.add(root,deviceLine);
  Style.gap(root,24);

  // 连接卡片
  LinearLayout connection=Style.card(this);
  state=Style.text(this,"等待开启连接",18,Style.INK,true);
  Style.add(connection,state);
  Style.gap(connection,8);
  description=Style.body(this,"开启后，电脑会在同一 Wi-Fi 下自动找到手机。");
  Style.add(connection,description);
  Style.gap(connection,20);
  connect=Style.button(this,"开始授权并连接",true,()->{
   if(LinkService.running){prefs.edit().putBoolean("enabled",false).apply();stopService(new Intent(this,LinkService.class));handler.postDelayed(()->refresh(),300);}
   else beginAuthorization();});
  Style.addButton(connection,connect);
  root.addView(connection,Style.cardParams(this,16));

  // 连接码卡片
  LinearLayout pairing=Style.card(this);
  Style.add(pairing,Style.heading(this,"连接码"));
  Style.gap(pairing,6);
  Style.add(pairing,Style.caption(this,"首次在电脑输入，之后无需重复配对。"));
  Style.gap(pairing,16);
  code=Style.text(this,"••••  ••••  ••••  ••••",13,Style.GREEN_DEEP,true);
  code.setTypeface(Typeface.MONOSPACE);
  code.setTextIsSelectable(true);
  code.setGravity(Gravity.CENTER);
  code.setPadding(dp(10),dp(16),dp(10),dp(16));
  code.setBackground(Style.round(Style.PALE,12,this));
  Style.add(pairing,code);
  Style.gap(pairing,14);
  LinearLayout codeButtons=Style.row(this);
  TextView show=Style.button(this,"显示连接码",false,()->{
   String k=prefs.getString("key","");
   code.setText(k.substring(0,8)+"\n"+k.substring(8,16)+"\n"+k.substring(16,24)+"\n"+k.substring(24));});
  Style.addRowButton(codeButtons,show,10);
  TextView copy=Style.button(this,"复制",false,()->{
   ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("连接码",prefs.getString("key","")));
   Toast.makeText(this,"已复制连接码",Toast.LENGTH_SHORT).show();});
  Style.addRowButtonLast(codeButtons,copy);
  Style.add(pairing,codeButtons);
  root.addView(pairing,Style.cardParams(this,16));

  // 授权卡片
  LinearLayout setup=Style.card(this);
  Style.add(setup,Style.heading(this,"系统授权"));
  Style.gap(setup,8);
  permission=Style.body(this,"自动检查所需权限");
  Style.add(setup,permission);
  Style.gap(setup,16);
  authorize=Style.button(this,"开始授权",false,()->beginAuthorization());
  Style.addButton(setup,authorize);
  Style.gap(setup,14);
  authorizationHint=Style.caption(this,"使用无障碍息屏，保留系统正常的指纹解锁流程。只关闭屏幕，不读取屏幕内容。");
  Style.add(setup,authorizationHint);
  root.addView(setup,Style.cardParams(this,16));

  // 品牌化的后台设置入口
  background=Style.button(this,backgroundTitle(),false,()->backgroundGuide());
  Style.add(root,background);
  Style.gap(root,12);

  // 华为等仍在 Android 兼容层的鸿蒙系统：明确标注兼容范围
  compatNote=Style.caption(this,compatText());
  compatNote.setVisibility(profile.platform==VendorProfile.PLATFORM_HARMONY_COMPAT?View.VISIBLE:View.GONE);
  if(profile.platform==VendorProfile.PLATFORM_HARMONY_COMPAT){Style.add(root,compatNote);Style.gap(root,12);}

  TextView more=Style.button(this,"更多设置",false,()->more());
  Style.add(root,more);
  Style.gap(root,22);
  TextView footer=Style.caption(this,"局域网直连 · 无会员 · 无广告\n仅用于息屏，不读取屏幕内容");
  footer.setGravity(Gravity.CENTER);
  Style.add(root,footer);
 }

 String backgroundTitle(){
  if(profile.isGeneric())return "后台运行设置（通用路径）";
  return "后台运行设置（"+profile.brandLabel+"）";
 }

 String compatText(){
  return "当前系统："+profile.systemLabel+"\n"+VendorProfile.NOTE_HARMONY_COMPAT;
 }

 void appDetails(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception e){Toast.makeText(this,"请在手机设置中打开本应用的信息页",Toast.LENGTH_SHORT).show();}}
 void backgroundGuide(){startActivity(new Intent(this,BackgroundSetupActivity.class));}

 void repairFingerprint(){
  retireLegacyAdmin(this);
  new AlertDialog.Builder(this)
   .setTitle("恢复正常的指纹解锁流程")
   .setMessage("新版仅使用无障碍息屏，不再使用设备管理员锁屏。\n\n当前这次请先输入一次锁屏密码，再启用本应用的无障碍服务。\n\n如果旧管理员授权尚未取消，请在系统设备管理员设置中仅取消「一键息屏」。重启等系统要求验证密码的情况仍需输入密码。")
   .setPositiveButton("打开无障碍授权",(d,w)->openAccessibility(false))
   .setNegativeButton("知道了",null).show();
 }

 void more(){
  String[] items={"无障碍息屏授权","后台运行设置（"+profile.brandLabel+"）","修复息屏后无法指纹解锁","重置连接码"};
  new AlertDialog.Builder(this).setTitle("更多设置").setItems(items,(dialog,index)->{
   if(index==0)openAccessibility(false);
   else if(index==1)backgroundGuide();
   else if(index==2)repairFingerprint();
   else new AlertDialog.Builder(this).setTitle("重置连接码？").setMessage("已配对电脑将断开，需要输入新的连接码。")
    .setPositiveButton("重置",(d,w)->{byte[] k=new byte[16];new SecureRandom().nextBytes(k);prefs.edit().putString("key",hex(k)).apply();code.setText("已重置，请重新显示连接码");})
    .setNegativeButton("取消",null).show();
  }).show();
 }

 // 以实际绑定为准；强行停止后，系统开关可能仍开启，但服务尚未恢复。
 boolean canLock(){return SleepAccess.instance!=null;}

 boolean notificationsReady(){return Build.VERSION.SDK_INT<33||checkSelfPermission("android.permission.POST_NOTIFICATIONS")==0;}
 boolean batteryReady(){return ((PowerManager)getSystemService(POWER_SERVICE)).isIgnoringBatteryOptimizations(getPackageName());}

 void beginAuthorization(){if(flowActive)return;flowActive=true;flowWaiting=false;attempted=0;advanceAuthorization();}
 void scheduleAuthorization(){handler.removeCallbacks(continueFlow);handler.postDelayed(continueFlow,450);}

 void advanceAuthorization(){
  if(!flowActive||flowWaiting||isFinishing())return;
  int step=AuthorizationPlan.next(canLock(),notificationsReady(),batteryReady(),attempted);
  attempted|=step;
  if(step==AuthorizationPlan.STOP){
   flowActive=false;
   refresh();return;
  }
  if(step==AuthorizationPlan.DONE){
   flowActive=false;startConnection();
   refresh();
   if(profile.isChineseRom()&&!BackgroundSetupActivity.complete(this,profile))
    handler.postDelayed(()->{if(!isFinishing())new AlertDialog.Builder(this).setTitle("连接已开启").setMessage("建议再检查"+profile.brandLabel+"的后台设置，减少看视频时断连。也可以稍后在「后台运行设置」里完成。").setPositiveButton("继续检查",(d,w)->startActivity(new Intent(this,BackgroundSetupActivity.class).putExtra("autoStart",true))).setNegativeButton("稍后设置",null).show();},500);
   return;
  }
  flowWaiting=true;refresh();
  if(step==AuthorizationPlan.LOCK){permission.setText("1 / 3 · 请启用无障碍息屏服务");openAccessibility(true);return;}
  if(step==AuthorizationPlan.NOTIFICATIONS){
   permission.setText("2 / 3 · 请确认连接通知");
   try{requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQUEST_NOTIFICATIONS);}
   catch(Exception e){flowWaiting=false;scheduleAuthorization();}
   return;
  }
  permission.setText("3 / 3 · 请确认允许后台连接");
  try{startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,android.net.Uri.parse("package:"+getPackageName())),REQUEST_SYSTEM);}
  catch(Exception e){flowWaiting=false;authorizationHint.setText("系统未提供快捷后台确认入口，可在「更多设置」中按路径手动检查。");scheduleAuthorization();}
 }

 void openAccessibility(boolean fromFlow){
  Intent direct=new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,SleepAccess.class));
  try{if(fromFlow)startActivityForResult(direct,REQUEST_SYSTEM);else startActivity(direct);}
  catch(Exception e){
   try{Intent general=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);if(fromFlow)startActivityForResult(general,REQUEST_SYSTEM);else startActivity(general);}
   catch(Exception ignored){if(fromFlow){flowWaiting=false;scheduleAuthorization();}Toast.makeText(this,"请在系统无障碍设置中启用一键息屏",Toast.LENGTH_LONG).show();}
  }
 }

 void startConnection(){
  try{prefs.edit().putBoolean("enabled",true).apply();startForegroundService(new Intent(this,LinkService.class));handler.postDelayed(()->refresh(),500);}
  catch(Exception e){prefs.edit().putBoolean("enabled",false).apply();Toast.makeText(this,"连接未启动，请重新打开应用后再试",Toast.LENGTH_LONG).show();}
 }

 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==REQUEST_SYSTEM&&flowActive){flowWaiting=false;scheduleAuthorization();}
 }

 public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
  super.onRequestPermissionsResult(request,permissions,results);
  if(request==REQUEST_NOTIFICATIONS&&flowActive){flowWaiting=false;scheduleAuthorization();}
 }

 protected void onSaveInstanceState(Bundle out){out.putBoolean("flowActive",flowActive);out.putBoolean("flowWaiting",flowWaiting);out.putInt("attempted",attempted);super.onSaveInstanceState(out);}

 protected void onResume(){
  super.onResume();retireLegacyAdmin(this);
  handler.removeCallbacks(refreshLoop);handler.post(refreshLoop);
  if(flowActive&&!flowWaiting)scheduleAuthorization();
  if(prefs!=null&&prefs.getBoolean("enabled",false)&&(!LinkService.running||!LinkService.discoveryReady||!LinkService.commandsReady))startConnection();
 }

 protected void onPause(){handler.removeCallbacks(refreshLoop);super.onPause();}

 void refresh(){
  if(state==null)return;
  boolean ready=canLock(),healthy=LinkService.running&&LinkService.discoveryReady&&LinkService.commandsReady;
  state.setText(healthy?"●  连接服务已就绪":(LinkService.running?"正在恢复连接…":"等待开启连接"));
  state.setTextColor(healthy?Style.OK:Style.INK);
  description.setText(healthy?"看视频时断连，请检查下方的后台运行设置。":"点击下方按钮，确认系统授权后自动连接。");
  connect.setText(LinkService.running?"暂停连接":(ready?"开启连接":"开始授权并连接"));
  connect.setEnabled(!flowActive);
  authorize.setEnabled(!flowActive);
  authorize.setText(flowActive?"等待你确认…":(ready?"重新检查授权":"开始授权"));
  if(!flowActive){
   boolean notified=notificationsReady(),battery=batteryReady();
   permission.setText((ready?"息屏 ✓":"息屏待确认")+"  ·  "+(notified?"通知 ✓":"通知待确认")+"  ·  "+(battery?"省电豁免 ✓":"省电豁免待确认"));
   // 提示语必须跟着实际授权状态走：授权都齐了还说「部分未确认」会和上面那行自相矛盾。
   if(!ready)authorizationHint.setText("请启用本应用的无障碍息屏服务。若已开启仍未生效，请关闭后重新开启；若开关被限制，在应用信息右上角允许受限设置后重试。");
   else if(!notified||!battery)authorizationHint.setText("息屏权限已就绪。通知与省电豁免用于后台连接，可点「重新检查授权」补上，也可稍后处理。");
   else authorizationHint.setText("使用无障碍息屏，保留系统正常的指纹解锁流程。只关闭屏幕，不读取屏幕内容。");
  }
  permission.setTextColor(ready?Style.OK:Style.MUTED);
 }
}

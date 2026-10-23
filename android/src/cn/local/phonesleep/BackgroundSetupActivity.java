package cn.local.phonesleep;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/**
 * 后台保活设置助手（按品牌）。
 *
 * 三条原则：
 * 1. 每一步都先确认入口在当前设备上真的可用（能解析到系统应用且已导出），不可用就退到通用路径；
 * 2. 系统能检测的项目（如电池优化白名单）直接判定并跳过，不让用户重复确认；
 * 3. 系统检测不到的项目由用户自己确认，「已确认」明确表示“用户确认完成”，不伪装成系统检测结果。
 */
public class BackgroundSetupActivity extends Activity {
 private static final String PREFS="background_confirmed";
 private static final int REQUEST_BASE=300;

 VendorProfile profile;
 VendorGuide.Step[] steps;
 SharedPreferences confirmed;
 TextView[] states;
 TextView next,progress;
 boolean flow,waiting;
 Handler handler=new Handler();

 static boolean complete(Context c,VendorProfile profile){
  SharedPreferences prefs=c.getSharedPreferences(PREFS,0);
  if(!profile.brandLabel.equals(prefs.getString("brand","")))return false;
  VendorGuide.Step[] steps=VendorGuide.steps(profile);
  for(int i=0;i<steps.length;i++)if(!isSatisfied(c,prefs,steps[i],i))return false;
  return true;
 }

 /** 系统能判定的项目自动通过，其余看用户确认。 */
 static boolean isSatisfied(Context c,SharedPreferences prefs,VendorGuide.Step step,int index){
  if(systemVerified(c,step))return true;
  return prefs.getBoolean("step"+index,false);
 }

 /** 只有电池优化白名单是系统可检测的，其余后台策略各厂商不对外暴露。 */
 static boolean systemVerified(Context c,VendorGuide.Step step){
  for(VendorGuide.Target t:step.targets)
   if(VendorGuide.ACTION_IGNORE_BATTERY.equals(t.action))
    try{return ((PowerManager)c.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(c.getPackageName());}catch(Exception ignored){return false;}
  return false;
 }

 int dp(float v){return Style.dp(this,v);}

 public void onCreate(Bundle saved){
  super.onCreate(saved);
  profile=VendorProfile.detect();
  steps=VendorGuide.steps(profile);
  confirmed=getSharedPreferences(PREFS,0);
  if(!profile.brandLabel.equals(confirmed.getString("brand",""))){
   confirmed.edit().clear().putString("brand",profile.brandLabel).apply();
  }
  if(saved!=null){flow=saved.getBoolean("flow");waiting=saved.getBoolean("waiting");}

  getWindow().setStatusBarColor(Style.BG);
  getWindow().setNavigationBarColor(Style.BG);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

  ScrollView scroll=new ScrollView(this);
  scroll.setBackgroundColor(Style.BG);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setPadding(dp(20),dp(28),dp(20),dp(32));
  scroll.addView(root);
  setContentView(scroll);

  Style.add(root,Style.title(this,"保持后台连接"));
  Style.gap(root,6);
  Style.add(root,Style.body(this,profile.brandLabel+" · "+profile.systemLabel));
  Style.gap(root,8);
  Style.add(root,Style.caption(this,"按步骤打开对应页面，完成设置后回到本页确认。入口不可用时会给出通用路径。"));
  Style.gap(root,20);

  progress=Style.caption(this,"");
  states=new TextView[steps.length];
  for(int i=0;i<steps.length;i++){
   final int step=i;
   LinearLayout card=Style.card(this);
   Style.add(card,Style.heading(this,(i+1)+". "+steps[i].title));
   Style.gap(card,8);
   states[i]=Style.caption(this,"");
   Style.add(card,states[i]);
   Style.gap(card,10);
   Style.add(card,Style.body(this,steps[i].hint));
   Style.gap(card,16);
   TextView open=Style.button(this,"打开设置",false,()->{flow=false;openStep(step);});
   Style.addButton(card,open);
   root.addView(card,Style.cardParams(this,14));
  }

  next=Style.button(this,"继续设置",true,()->{flow=true;advance();});
  Style.add(root,next);
  Style.gap(root,12);
  Style.add(root,Style.button(this,"重新检查",false,()->{confirmed.edit().clear().putString("brand",profile.brandLabel).apply();flow=false;refresh();}));
  Style.gap(root,18);
  Style.add(root,progress);
  Style.gap(root,8);
  Style.add(root,Style.caption(this,"「你已确认」表示你确认系统里已经设置好，不是系统自动检测的结果。设置变化时可以点「重新检查」。"));

  refresh();
  if(saved==null&&getIntent().getBooleanExtra("autoStart",false)){flow=true;handler.postDelayed(()->advance(),400);}
 }

 void refresh(){
  int done=0;
  for(int i=0;i<steps.length;i++){
   boolean system=systemVerified(this,steps[i]);
   boolean user=confirmed.getBoolean("step"+i,false);
   if(system||user)done++;
   if(system){states[i].setText("✓  系统已确认完成，已跳过");states[i].setTextColor(Style.OK);}
   else if(user){states[i].setText("✓  你已确认设置完成");states[i].setTextColor(Style.OK);}
   else{states[i].setText("待确认");states[i].setTextColor(Style.MUTED);}
  }
  progress.setText("已完成 "+done+" / "+steps.length+" 项");
  progress.setTextColor(done==steps.length?Style.OK:Style.MUTED);
  next.setText(done==steps.length?"完成，返回连接页面":"按顺序继续设置");
 }

 void advance(){
  if(waiting||isFinishing())return;
  boolean[] state=new boolean[steps.length];
  for(int i=0;i<steps.length;i++)state[i]=isSatisfied(this,confirmed,steps[i],i);
  int step=BackgroundPlan.next(state);
  if(step<0){flow=false;Toast.makeText(this,"后台设置已全部确认",Toast.LENGTH_SHORT).show();finish();return;}
  openStep(step);
 }

 /** 把候选入口变成 Intent；显式组件带上各厂商读取的包名参数。 */
 Intent intentFor(VendorGuide.Target target){
  if(target.isAction()){
   if("__manual__".equals(target.action))return null;
   Intent intent=new Intent(target.action);
   if(VendorGuide.ACTION_APP_DETAILS.equals(target.action)||VendorGuide.ACTION_IGNORE_BATTERY.equals(target.action)||VendorGuide.ACTION_VIEW_POWER_DETAIL.equals(target.action))
    intent.setData(Uri.parse("package:"+getPackageName()));
   return intent;
  }
  Intent intent=new Intent().setComponent(new ComponentName(target.packageName,target.className));
  return intent
   .putExtra("packageName",getPackageName())
   .putExtra("package_name",getPackageName())
   .putExtra("pkg_name",getPackageName())
   .putExtra(Intent.EXTRA_PACKAGE_NAME,getPackageName())
   .putExtra("uid",android.os.Process.myUid());
 }

 /**
  * 入口可用性检查：必须能解析到、必须已导出、必须是系统应用、且调用方持有其声明权限。
  * 任何一项不满足都视为不可用，交给下一个候选或通用路径，绝不直接 startActivity 赌运气。
  */
 boolean available(Intent intent){
  try{
   ResolveInfo resolved=getPackageManager().resolveActivity(intent,0);
   if(resolved==null||resolved.activityInfo==null)return false;
   ActivityInfo info=resolved.activityInfo;
   if(!info.exported)return false;
   if((info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;
   if(info.permission!=null&&checkSelfPermission(info.permission)!=PackageManager.PERMISSION_GRANTED)return false;
   return true;
  }catch(Exception e){return false;}
 }

 boolean launch(Intent intent,int step){
  try{
   ResolveInfo resolved=getPackageManager().resolveActivity(intent,0);
   intent.setComponent(new ComponentName(resolved.activityInfo.packageName,resolved.activityInfo.name));
   waiting=true;
   startActivityForResult(intent,REQUEST_BASE+step);
   return true;
  }catch(Exception e){waiting=false;return false;}
 }

 void openStep(int step){
  if(waiting)return;
  final VendorGuide.Step current=steps[step];
  for(VendorGuide.Target target:current.targets){
   Intent intent=intentFor(target);
   if(intent==null)break;
   if(available(intent)&&launch(intent,step))return;
  }
  waiting=false;
  showManualPath(current,step);
 }

 /** 所有候选入口都不可用时，给出可以照着做的通用路径。 */
 void showManualPath(VendorGuide.Step step,int index){
  flow=false;
  new AlertDialog.Builder(this)
   .setTitle("请在系统设置中手动完成")
   .setMessage(step.title+"\n\n"+step.manualPath+"\n\n本机未提供可直接打开的入口，请按上面的路径手动查找。完成后再回来确认。")
   .setPositiveButton("我已完成",(d,w)->mark(index))
   .setNeutralButton("再试一次",(d,w)->openStep(index))
   .setNegativeButton("稍后处理",null)
   .show();
 }

 void mark(int step){
  confirmed.edit().putBoolean("step"+step,true).apply();
  refresh();
  if(flow)handler.postDelayed(()->advance(),250);
 }

 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request<REQUEST_BASE||request>=REQUEST_BASE+steps.length)return;
  waiting=false;
  final int step=request-REQUEST_BASE;
  handler.postDelayed(()->{
   if(isFinishing())return;
   if(systemVerified(this,steps[step])){refresh();if(flow)advance();return;}
   new AlertDialog.Builder(this)
    .setTitle("是否已设置好？")
    .setMessage(steps[step].confirmPrompt+"\n\n只有确认完成后，才会记住并跳过这一步。")
    .setPositiveButton("已设置好",(d,w)->mark(step))
    .setNegativeButton("还没完成",(d,w)->flow=false)
    .setNeutralButton("再打开",(d,w)->openStep(step))
    .show();
  },250);
 }

 protected void onSaveInstanceState(Bundle out){out.putBoolean("flow",flow);out.putBoolean("waiting",waiting);super.onSaveInstanceState(out);}

 protected void onResume(){super.onResume();if(states!=null)refresh();}
}
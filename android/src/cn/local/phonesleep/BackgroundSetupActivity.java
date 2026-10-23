package cn.local.phonesleep;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;
public class BackgroundSetupActivity extends Activity {
 static final String[] KEYS={"background","autostart","recent"};
 static final String[] TITLES={"允许后台运行","允许自启动","最近任务加锁"};
 static final String[] HINTS={"为「一键息屏」选择允许后台运行 / 后台高耗电，或不限制。","找到「一键息屏」，开启自启动。","打开最近任务，在「一键息屏」卡片菜单中选择锁定，然后返回本页。"};
 final int BG=Color.rgb(244,247,252),INK=Color.rgb(27,39,61),MUTED=Color.rgb(115,129,149),BLUE=Color.rgb(53,104,238);
 android.content.SharedPreferences confirmed;TextView[] states=new TextView[3];Button next;boolean flow,waiting;int pending=-1;Handler handler=new Handler();
 static boolean isVivo(){String s=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);return s.contains("vivo")||s.contains("iqoo");}
 static boolean complete(Context c){android.content.SharedPreferences p=c.getSharedPreferences("background_confirmed",0);return BackgroundPlan.next(p.getBoolean(KEYS[0],false),p.getBoolean(KEYS[1],false),p.getBoolean(KEYS[2],false))<0;}
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
 GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
 void gap(LinearLayout p,int n){p.addView(new android.view.View(this),new LinearLayout.LayoutParams(1,dp(n)));}
 Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(BLUE);b.setBackground(bg(Color.rgb(234,240,255)));b.setStateListAnimator(null);b.setOnClickListener(v->action.run());return b;}
 public void onCreate(Bundle saved){super.onCreate(saved);confirmed=getSharedPreferences("background_confirmed",0);if(saved!=null){flow=saved.getBoolean("flow");waiting=saved.getBoolean("waiting");pending=saved.getInt("pending",-1);}getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(24),dp(28),dp(24),dp(28));scroll.addView(root);setContentView(scroll);
  TextView title=text("保持后台连接",28,INK);title.setTypeface(null,Typeface.BOLD);root.addView(title);gap(root,10);root.addView(text("按步骤打开对应页面，完成设置后返回确认。不同系统版本的页面可能不同。",14,MUTED));gap(root,22);
  for(int i=0;i<3;i++){final int step=i;LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(20),dp(20),dp(20),dp(20));card.setBackground(bg(Color.WHITE));root.addView(card,new LinearLayout.LayoutParams(-1,-2));card.addView(text((i+1)+". "+TITLES[i],18,INK));gap(card,8);states[i]=text("待确认",12,MUTED);card.addView(states[i]);gap(card,10);card.addView(text(HINTS[i],13,MUTED));gap(card,16);card.addView(button(i==2?"查看加锁步骤":"打开设置",()->{flow=false;openStep(step);}),new LinearLayout.LayoutParams(-1,dp(46)));gap(root,14);}
  next=button("继续设置",()->{flow=true;advance();});root.addView(next,new LinearLayout.LayoutParams(-1,dp(50)));gap(root,14);root.addView(button("重新检查这三项",()->{confirmed.edit().clear().apply();flow=false;refresh();}),new LinearLayout.LayoutParams(-1,dp(44)));gap(root,16);root.addView(text("“已确认”表示你确认已完成，不代表系统自动检测。设置发生变化时，可重新检查。",12,MUTED));refresh();
  if(saved==null&&getIntent().getBooleanExtra("autoStart",false)){flow=true;handler.postDelayed(()->advance(),400);}
 }
 void refresh(){for(int i=0;i<3;i++)states[i].setText(confirmed.getBoolean(KEYS[i],false)?"✓  你已确认设置完成":"待确认");next.setText(complete(this)?"完成，返回连接页面":"按顺序继续设置");}
 void advance(){if(waiting||isFinishing())return;int step=BackgroundPlan.next(confirmed.getBoolean(KEYS[0],false),confirmed.getBoolean(KEYS[1],false),confirmed.getBoolean(KEYS[2],false));if(step<0){flow=false;Toast.makeText(this,"后台设置已由你确认完成",0).show();finish();return;}openStep(step);}
 Intent extras(Intent i){return i.putExtra("packageName",getPackageName()).putExtra("package_name",getPackageName()).putExtra("pkg_name",getPackageName()).putExtra(Intent.EXTRA_PACKAGE_NAME,getPackageName()).putExtra("uid",android.os.Process.myUid());}
 boolean launch(Intent i,int step){try{ResolveInfo r=getPackageManager().resolveActivity(i,0);if(r==null||r.activityInfo==null||!r.activityInfo.exported)return false;ActivityInfo a=r.activityInfo;if((a.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)==0)return false;if(a.permission!=null&&checkSelfPermission(a.permission)!=PackageManager.PERMISSION_GRANTED)return false;i.setComponent(new ComponentName(a.packageName,a.name));waiting=true;pending=step;startActivityForResult(i,300+step);return true;}catch(Exception e){waiting=false;return false;}}
 void openStep(int step){if(waiting)return;if(step==2){new AlertDialog.Builder(this).setTitle("最近任务加锁").setMessage(HINTS[2]+"\n\n通常从屏幕底部上滑并停留，找到本应用卡片，点击卡片菜单中的锁定。此项没有统一的自动跳转入口。").setPositiveButton("我已完成",(d,w)->mark(step)).setNegativeButton("稍后处理",(d,w)->flow=false).setOnCancelListener(d->flow=false).show();return;}
  Toast.makeText(this,HINTS[step],1).show();List<Intent> candidates=new ArrayList<>();
  if(step==0){candidates.add(extras(new Intent().setComponent(new ComponentName("com.vivo.abe","com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"))));candidates.add(extras(new Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL",Uri.parse("package:"+getPackageName()))));}
  else{candidates.add(extras(new Intent().setComponent(new ComponentName("com.vivo.permissionmanager","com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))));candidates.add(extras(new Intent().setComponent(new ComponentName("com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))));candidates.add(extras(new Intent().setComponent(new ComponentName("com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))));}
  for(Intent i:candidates)if(launch(i,step))return;
  if(launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())),step)){Toast.makeText(this,"已打开应用信息，请寻找「"+TITLES[step]+"」相关选项",1).show();return;}
  flow=false;new AlertDialog.Builder(this).setTitle("请手动打开系统设置").setMessage(HINTS[step]+"\n\n系统未提供可打开的快捷入口，请在手机设置中搜索“"+(step==0?"后台耗电管理":"自启动")+"”。").setPositiveButton("知道了",null).show();
 }
 void mark(int step){confirmed.edit().putBoolean(KEYS[step],true).apply();refresh();if(flow)handler.postDelayed(()->advance(),250);}
 protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request<300||request>301)return;waiting=false;final int step=request-300;pending=-1;handler.postDelayed(()->{if(isFinishing())return;new AlertDialog.Builder(this).setTitle("是否已设置好？").setMessage(HINTS[step]+"\n\n只有确认完成后，才会记住并跳过这一步。").setPositiveButton("已设置好",(d,w)->mark(step)).setNegativeButton("还没完成",(d,w)->{flow=false;refresh();}).setNeutralButton("再打开",(d,w)->openStep(step)).setOnCancelListener(d->flow=false).show();},250);}
 protected void onSaveInstanceState(Bundle out){out.putBoolean("flow",flow);out.putBoolean("waiting",waiting);out.putInt("pending",pending);super.onSaveInstanceState(out);}
}

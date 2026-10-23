package cn.local.phonesleep;

import android.os.Build;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 品牌与系统识别。
 *
 * classify() 只接收字符串、不触碰任何 Android API，因此可以用注入的设备信息做纯逻辑测试。
 * 只有 detect() 读取真实设备，它把系统属性取出来交给 classify()。
 */
final class VendorProfile {
 public static final int GENERIC=0,VIVO=1,XIAOMI=2,OPPO=3,HUAWEI=4,HONOR=5;
 /** 系统平台：普通 Android / 华为系仍带 Android 兼容层的鸿蒙。 */
 public static final int PLATFORM_ANDROID=0,PLATFORM_HARMONY_COMPAT=1;

 public final int brand,platform;
 public final String brandLabel,systemLabel,manufacturer,brandRaw,model,platformNote;

 VendorProfile(int brand,int platform,String brandLabel,String systemLabel,String manufacturer,String brandRaw,String model,String platformNote){
  this.brand=brand;this.platform=platform;this.brandLabel=brandLabel;this.systemLabel=systemLabel;
  this.manufacturer=manufacturer;this.brandRaw=brandRaw;this.model=model;this.platformNote=platformNote;
 }

 boolean isGeneric(){return brand==GENERIC;}
 boolean isChineseRom(){return brand==VIVO||brand==XIAOMI||brand==OPPO||brand==HUAWEI||brand==HONOR;}

 /** 一句话描述当前设备，用于界面副标题。 */
 String describe(){return brandLabel+" · "+safe(model)+"\n"+systemLabel;}

 /**
  * 纯逻辑分类。props 为系统属性表（可为空），键名见 KEYS。
  */
 static VendorProfile classify(String manufacturer,String brand,String display,String model,int sdk,Map<String,String> props){
  if(props==null)props=new HashMap<String,String>();
  String m=lower(manufacturer)+" "+lower(brand);
  String miui=get(props,"ro.miui.ui.version.name"),hyper=get(props,"ro.mi.os.version.name");
  String emui=get(props,"ro.build.version.emui"),harmony=get(props,"hw_sc.build.platform.version"),harmonyVer=get(props,"ro.build.version.harmony");
  String coloros=get(props,"ro.build.version.opporom"),oplus=get(props,"ro.build.version.oplusrom"),oplusDisplay=get(props,"ro.build.version.oplusrom.display"),realme=get(props,"ro.build.version.realmeui");
  String origin=get(props,"ro.vivo.os.version"),funtouch=get(props,"ro.vivo.rom.version");
  String magic=get(props,"ro.build.version.magic"),magicOs=get(props,"ro.build.version.magicui");

  if(m.contains("vivo")||m.contains("iqoo")){
   String sys=!empty(origin)?join("OriginOS",origin):(!empty(funtouch)?join("Funtouch OS",funtouch):"vivo 定制系统");
   return new VendorProfile(VIVO,PLATFORM_ANDROID,"vivo / iQOO",sys+"（API "+(sdk)+"）",manufacturer,brand,model,NOTE_ANDROID);
  }
  if(m.contains("xiaomi")||m.contains("redmi")||m.contains("poco")||m.contains("blackshark")){
   String sys=!empty(hyper)?join("HyperOS",hyper):(!empty(miui)?join("MIUI",miui):"小米定制系统");
   return new VendorProfile(XIAOMI,PLATFORM_ANDROID,(m.contains("redmi")?"Redmi":(m.contains("poco")?"POCO":"小米")),sys+"（API "+(sdk)+"）",manufacturer,brand,model,NOTE_ANDROID);
  }
  if(m.contains("oppo")||m.contains("realme")||m.contains("oneplus")||m.contains("one plus")){
   String name=m.contains("realme")?"realme":(m.contains("oneplus")||m.contains("one plus")?"OnePlus":"OPPO");
   String sys;
   if(m.contains("realme"))sys=!empty(realme)?join("realme UI",realme):"realme UI";
   else if(m.contains("oneplus"))sys=!empty(oplusDisplay)?join("ColorOS",oplusDisplay):(!empty(oplus)?join("OxygenOS / ColorOS",oplus):"ColorOS");
   else sys=!empty(coloros)?join("ColorOS",coloros):(!empty(oplus)?join("ColorOS",oplus):"ColorOS");
   return new VendorProfile(OPPO,PLATFORM_ANDROID,name,sys+"（API "+(sdk)+"）",manufacturer,brand,model,NOTE_ANDROID);
  }
  if(m.contains("huawei")){
   boolean h=!empty(harmony)||!empty(harmonyVer);
   String sys=h?join("HarmonyOS",firstNonEmpty(harmony,harmonyVer)):(!empty(emui)?join("EMUI",emui):"华为定制系统");
   return new VendorProfile(HUAWEI,h?PLATFORM_HARMONY_COMPAT:PLATFORM_ANDROID,"华为",sys+"（Android API "+sdk+(h?" 兼容层）":"）"),manufacturer,brand,model,h?NOTE_HARMONY_COMPAT:NOTE_ANDROID);
  }
  if(m.contains("honor")){
   boolean h=!empty(harmony)||!empty(harmonyVer);
   String sys=h?join("MagicOS",firstNonEmpty(harmony,harmonyVer)):(!empty(magicOs)?join("MagicOS",magicOs):(!empty(magic)?join("MagicOS",magic):"荣耀定制系统"));
   return new VendorProfile(HONOR,h?PLATFORM_HARMONY_COMPAT:PLATFORM_ANDROID,"荣耀",sys+"（Android API "+(sdk)+"）",manufacturer,brand,model,h?NOTE_HARMONY_COMPAT:NOTE_ANDROID);
  }
  String systemName="Android · API "+sdk;
  return new VendorProfile(GENERIC,PLATFORM_ANDROID,"其他品牌",systemName,manufacturer,brand,model,NOTE_ANDROID);
 }

 static final String NOTE_ANDROID="运行标准 Android 系统。";
 static final String NOTE_HARMONY_COMPAT="本版本面向可直接运行 Android APK 的设备；HarmonyOS NEXT 等原生鸿蒙平台不在支持范围，需要另行评估原生版本。系统属性只用于显示参考，不能证明所有功能兼容。";
 static final String NOTE_GENERIC_FALLBACK="系统未提供可打开的厂商设置入口，请按下方通用路径手动查找。";

 /** detect() 读取的系统属性键。 */
 static final String[] KEYS={
  "ro.miui.ui.version.name","ro.mi.os.version.name","ro.build.version.emui","hw_sc.build.platform.version","ro.build.version.harmony",
  "ro.build.version.opporom","ro.build.version.oplusrom","ro.build.version.oplusrom.display","ro.build.version.realmeui",
  "ro.vivo.os.version","ro.vivo.rom.version","ro.build.version.magic","ro.build.version.magicui"
 };

 static VendorProfile detect(){
  Map<String,String> props=new HashMap<String,String>();
  // 一次读取，避免在界面线程为每个属性分别启动进程；只保留识别系统需要的键。
  try{
   Process process=new ProcessBuilder("getprop").redirectErrorStream(true).start();
   try(BufferedReader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
    String line;while((line=reader.readLine())!=null){for(String key:KEYS){String prefix="["+key+"]: [";if(line.startsWith(prefix)&&line.endsWith("]")){props.put(key,line.substring(prefix.length(),line.length()-1));break;}}}
   }finally{process.destroy();}
  }catch(Exception ignored){}
  String model=empty(Build.MODEL)?"未知型号":Build.MODEL;
  return classify(safe(Build.MANUFACTURER),safe(Build.BRAND),safe(Build.DISPLAY),model,Build.VERSION.SDK_INT,props);
 }

 /**
  * 读取只读系统属性。android.os.SystemProperties 对 targetSdk 28+ 属于隐藏 API，
  * 反射会被拦截，因此走 getprop 命令。
  */
 static String getprop(String key){
  BufferedReader reader=null;
  try{
   Process process=new ProcessBuilder(new String[]{"getprop",key}).redirectErrorStream(true).start();
   reader=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"));
   String line=reader.readLine();
   process.waitFor();
   return line==null?"":line.trim();
  }catch(Exception e){return "";}
  finally{if(reader!=null)try{reader.close();}catch(Exception ignored){}}
 }

 static String firstNonEmpty(String a,String b){return empty(a)?b:a;}
 static boolean empty(String s){return s==null||s.length()==0;}
 static String safe(String s){return s==null?"":s;}
 static String lower(String s){return safe(s).toLowerCase(Locale.ROOT);}
 static String get(Map<String,String> m,String k){String v=m.get(k);return v==null?"":v;}
 static String join(String name,String version){return empty(version)?name:name+" "+version;}
}

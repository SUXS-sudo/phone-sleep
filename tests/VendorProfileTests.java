package cn.local.phonesleep;

import java.util.HashMap;
import java.util.Map;

/**
 * 品牌与系统识别的逻辑测试。
 *
 * 这里注入的是构造出来的设备信息，不是真实厂商系统。
 * 通过只说明「给定的设备信息会被分类成什么」，不能说明真机上的设置页、保活和指纹行为。
 */
class VendorProfileTests {
 static int passed;

 static void check(boolean ok,String label){
  if(!ok)throw new AssertionError("FAIL "+label);
  passed++;
 }

 static Map<String,String> props(String... pairs){
  Map<String,String> m=new HashMap<String,String>();
  for(int i=0;i+1<pairs.length;i+=2)m.put(pairs[i],pairs[i+1]);
  return m;
 }

 static VendorProfile make(String manufacturer,String brand,String display,String model,int sdk,Map<String,String> props){
  return VendorProfile.classify(manufacturer,brand,display,model,sdk,props);
 }

 public static void main(String[] args){
  Map<String,String> none=new HashMap<String,String>();

  // ---- vivo / iQOO ----
  VendorProfile vivo=make("vivo","vivo","PD2302","X100 Pro",34,props("ro.vivo.os.version","2.0"));
  check(vivo.brand==VendorProfile.VIVO,"vivo 被识别为 VIVO");
  check(vivo.brandLabel.contains("vivo"),"vivo 品牌名");
  check(vivo.systemLabel.contains("OriginOS"),"vivo 系统名取 OriginOS: "+vivo.systemLabel);
  check(vivo.systemLabel.contains("34"),"vivo 系统名带 Android 版本");

  VendorProfile iqoo=make("vivo","iQOO","V2301A","iQOO 12",34,none);
  check(iqoo.brand==VendorProfile.VIVO,"iQOO 归入 vivo");
  check(iqoo.brandLabel.contains("iQOO"),"iQOO 品牌名");

  // ---- 小米 / Redmi / POCO ----
  VendorProfile hyper=make("Xiaomi","Xiaomi","UKQ1","23127PN0CC",34,props("ro.mi.os.version.name","OS1.0.3.0"));
  check(hyper.brand==VendorProfile.XIAOMI,"小米识别为 XIAOMI");
  check(hyper.systemLabel.contains("HyperOS"),"HyperOS 优先于 MIUI: "+hyper.systemLabel);

  VendorProfile miui=make("Xiaomi","Redmi","TKQ1","Redmi Note 13",33,props("ro.miui.ui.version.name","V14.0.5.0"));
  check(miui.brand==VendorProfile.XIAOMI,"Redmi 归入 XIAOMI");
  check(miui.brandLabel.equals("Redmi"),"Redmi 品牌名显示 Redmi");
  check(miui.systemLabel.contains("MIUI"),"MIUI 版本: "+miui.systemLabel);

  VendorProfile poco=make("Xiaomi","POCO","","POCO F5",33,none);
  check(poco.brand==VendorProfile.XIAOMI,"POCO 归入 XIAOMI");
  check(poco.brandLabel.equals("POCO"),"POCO 品牌名");

  // ---- OPPO / realme / OnePlus ----
  VendorProfile oppo=make("OPPO","OPPO","PGEM10","Find X6",34,props("ro.build.version.oplusrom.display","ColorOS 14.0.0"));
  check(oppo.brand==VendorProfile.OPPO,"OPPO 识别为 OPPO");
  check(oppo.systemLabel.contains("ColorOS"),"ColorOS: "+oppo.systemLabel);

  VendorProfile realme=make("realme","realme","RMX3708","GT Neo5",33,props("ro.build.version.realmeui","4.0"));
  check(realme.brand==VendorProfile.OPPO,"realme 归入 OPPO");
  check(realme.brandLabel.equals("realme"),"realme 品牌名");
  check(realme.systemLabel.contains("realme UI"),"realme UI: "+realme.systemLabel);

  VendorProfile oneplus=make("OnePlus","OnePlus","CPH2449","OnePlus 11",33,props("ro.build.version.oplusrom","13.1.0"));
  check(oneplus.brand==VendorProfile.OPPO,"OnePlus 归入 OPPO");
  check(oneplus.brandLabel.equals("OnePlus"),"OnePlus 品牌名");

  // ---- 华为：鸿蒙兼容层 与 纯 Android 要分开 ----
  VendorProfile harmony=make("HUAWEI","HUAWEI","ALN-AL00","Mate 60 Pro",34,props("hw_sc.build.platform.version","4.0.0"));
  check(harmony.brand==VendorProfile.HUAWEI,"华为识别为 HUAWEI");
  check(harmony.platform==VendorProfile.PLATFORM_HARMONY_COMPAT,"有鸿蒙属性时标为兼容层");
  check(harmony.systemLabel.contains("HarmonyOS"),"系统名取 HarmonyOS: "+harmony.systemLabel);
  check(harmony.systemLabel.contains("兼容层"),"明确标注 Android 兼容层");
  check(harmony.platformNote.contains("NEXT"),"兼容说明必须点出 HarmonyOS NEXT 不在支持范围");

  VendorProfile emui=make("HUAWEI","HUAWEI","ELE-L29","P30",29,props("ro.build.version.emui","EmotionUI_10.0.0"));
  check(emui.brand==VendorProfile.HUAWEI,"EMUI 设备归入 HUAWEI");
  check(emui.platform==VendorProfile.PLATFORM_ANDROID,"只有 EMUI 属性时视为普通 Android");
  check(emui.systemLabel.contains("EMUI"),"EMUI 系统名: "+emui.systemLabel);

  VendorProfile honor=make("HONOR","HONOR","ANY-AN00","Magic5",33,props("ro.build.version.magicui","7.1"));
  check(honor.brand==VendorProfile.HONOR,"荣耀识别为 HONOR");
  check(honor.systemLabel.contains("MagicOS"),"MagicOS: "+honor.systemLabel);

  // ---- 未知品牌回退 ----
  VendorProfile generic=make("Google","google","TQ3A","sdk_gphone64_x86_64",33,none);
  check(generic.brand==VendorProfile.GENERIC,"未知品牌归入 GENERIC");
  check(generic.isGeneric(),"generic 判断成立");
  check(!generic.isChineseRom(),"通用品牌不当作定制系统");
  check(generic.systemLabel.contains("Android"),"通用系统名保留 Android: "+generic.systemLabel);

  // ---- 大小写与脏数据 ----
  VendorProfile upper=make("VIVO","VIVO","","V2301",34,none);
  check(upper.brand==VendorProfile.VIVO,"厂商名大小写不敏感");
  VendorProfile nulls=make(null,null,null,null,34,null);
  check(nulls.brand==VendorProfile.GENERIC,"全空输入回退到 GENERIC 且不抛异常");
  check(!nulls.describe().isEmpty(),"描述文本在脏数据下仍可生成");

  // ---- 识别结果只由输入决定 ----
  VendorProfile again=make("Xiaomi","Xiaomi","UKQ1","23127PN0CC",34,props("ro.mi.os.version.name","OS1.0.3.0"));
  check(again.systemLabel.equals(hyper.systemLabel),"同样的设备信息得到同样的分类");

  System.out.println("PASS VendorProfileTests ("+passed+" checks)");
 }
}
package cn.local.phonesleep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台设置引导表的逻辑测试。
 *
 * v1.6 的缺陷是只有 vivo 的入口，其他品牌全落到 vivo 的 Intent。
 * 这里把「每个品牌只能用自己品牌的入口」固化成断言。
 */
class VendorGuideTests {
 static int passed;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError("FAIL "+label);passed++;}

 static Map<String,String> none(){return new HashMap<String,String>();}

 static VendorProfile profile(int brand){
  switch(brand){
   case VendorProfile.VIVO:return VendorProfile.classify("vivo","vivo","","X100",34,none());
   case VendorProfile.XIAOMI:return VendorProfile.classify("Xiaomi","Xiaomi","","14",34,none());
   case VendorProfile.OPPO:return VendorProfile.classify("OPPO","OPPO","","Find X6",34,none());
   case VendorProfile.HUAWEI:return VendorProfile.classify("HUAWEI","HUAWEI","","Mate 60",34,none());
   case VendorProfile.HONOR:return VendorProfile.classify("HONOR","HONOR","","Magic5",34,none());
   default:return VendorProfile.classify("Google","google","","sdk_gphone",33,none());
  }
 }

 static List<String> packagesOf(VendorGuide.Step[] steps){
  List<String> list=new ArrayList<String>();
  for(VendorGuide.Step s:steps)for(VendorGuide.Target t:s.targets)if(!t.isAction())list.add(t.packageName);
  return list;
 }

 static boolean contains(List<String> list,String value){
  for(String s:list)if(s.equals(value))return true;
  return false;
 }

 static boolean startsWithPackage(List<String> list,String prefix){
  for(String s:list)if(s.startsWith(prefix))return true;
  return false;
 }

 /** 每一步都必须给出可照做的通用路径和确认问题，否则入口不可用时用户会卡住。 */
 static void assertUsable(String brand,VendorGuide.Step[] steps){
  check(steps.length>=2,brand+" 至少有 2 个步骤");
  for(int i=0;i<steps.length;i++){
   VendorGuide.Step s=steps[i];
   check(s.title!=null&&!s.title.isEmpty(),brand+" 步骤 "+i+" 有标题");
   check(s.hint!=null&&s.hint.length()>8,brand+" 步骤 "+i+" 有说明");
   check(s.manualPath!=null&&s.manualPath.length()>8,brand+" 步骤 "+i+" 有可照做的通用路径");
   check(s.confirmPrompt!=null&&s.confirmPrompt.length()>4,brand+" 步骤 "+i+" 有确认问题");
   check(s.targets.length>=1,brand+" 步骤 "+i+" 至少有一个候选入口");
  }
 }

 public static void main(String[] args){
  VendorGuide.Step[] vivo=VendorGuide.steps(profile(VendorProfile.VIVO));
  VendorGuide.Step[] xiaomi=VendorGuide.steps(profile(VendorProfile.XIAOMI));
  VendorGuide.Step[] oppo=VendorGuide.steps(profile(VendorProfile.OPPO));
  VendorGuide.Step[] huawei=VendorGuide.steps(profile(VendorProfile.HUAWEI));
  VendorGuide.Step[] honor=VendorGuide.steps(profile(VendorProfile.HONOR));
  VendorGuide.Step[] generic=VendorGuide.steps(profile(VendorProfile.GENERIC));

  assertUsable("vivo",vivo);
  assertUsable("小米",xiaomi);
  assertUsable("OPPO",oppo);
  assertUsable("华为",huawei);
  assertUsable("荣耀",honor);
  assertUsable("通用",generic);

  List<String> vivoPkgs=packagesOf(vivo),xiaomiPkgs=packagesOf(xiaomi),oppoPkgs=packagesOf(oppo),huaweiPkgs=packagesOf(huawei),honorPkgs=packagesOf(honor),genericPkgs=packagesOf(generic);

  // 各品牌必须命中自己的包
  check(contains(vivoPkgs,"com.vivo.permissionmanager"),"vivo 使用 vivo 的自启动入口");
  check(contains(vivoPkgs,"com.vivo.abe"),"vivo 使用 vivo 的后台耗电入口");
  check(contains(xiaomiPkgs,"com.miui.powerkeeper"),"小米使用小米的省电策略入口");
  check(contains(xiaomiPkgs,"com.miui.securitycenter"),"小米使用小米的自启动入口");
  check(contains(oppoPkgs,"com.coloros.safecenter")||contains(oppoPkgs,"com.oplus.safecenter"),"OPPO 使用 ColorOS 自启动入口");
  check(contains(oppoPkgs,"com.coloros.oppoguardelf")||contains(oppoPkgs,"com.oplus.battery"),"OPPO 使用 ColorOS 耗电管理入口");
  check(contains(huaweiPkgs,"com.huawei.systemmanager"),"华为使用华为的启动管理入口");
  check(contains(honorPkgs,"com.hihonor.systemmanager"),"荣耀使用荣耀的启动管理入口");
  check(!contains(honorPkgs,"com.huawei.systemmanager"),"荣耀不混入华为包名");

  // 关键的回归断言：品牌之间不能互相串入口（v1.6 的实际缺陷）
  check(!startsWithPackage(xiaomiPkgs,"com.vivo"),"小米步骤里没有 vivo 入口");
  check(!startsWithPackage(xiaomiPkgs,"com.coloros"),"小米步骤里没有 OPPO 入口");
  check(!startsWithPackage(oppoPkgs,"com.vivo"),"OPPO 步骤里没有 vivo 入口");
  check(!startsWithPackage(oppoPkgs,"com.miui"),"OPPO 步骤里没有小米入口");
  check(!startsWithPackage(huaweiPkgs,"com.vivo"),"华为步骤里没有 vivo 入口");
  check(!startsWithPackage(huaweiPkgs,"com.miui"),"华为步骤里没有小米入口");
  check(!startsWithPackage(vivoPkgs,"com.miui")&&!startsWithPackage(vivoPkgs,"com.coloros"),"vivo 步骤里没有别家入口");

  // 未知品牌不得给出任何厂商专属入口，只能走标准 Android 路径
  check(!startsWithPackage(genericPkgs,"com.vivo"),"通用步骤不含 vivo 专属入口");
  check(!startsWithPackage(genericPkgs,"com.miui"),"通用步骤不含小米专属入口");
  check(!startsWithPackage(genericPkgs,"com.coloros"),"通用步骤不含 OPPO 专属入口");
  check(!startsWithPackage(genericPkgs,"com.huawei"),"通用步骤不含华为专属入口");
  check(generic.length<vivo.length,"通用路径步骤少于定制系统");

  // 每一步都要有兜底：最后一个候选要么是应用信息页，要么是需要手动的项目
  assertFallback("vivo",vivo);assertFallback("小米",xiaomi);assertFallback("OPPO",oppo);
  assertFallback("华为",huawei);assertFallback("荣耀",honor);assertFallback("通用",generic);

  // 最近任务加锁在三家定制系统里都保留，且没有伪造成可自动跳转的入口
  for(VendorGuide.Step[] set:new VendorGuide.Step[][]{vivo,xiaomi,oppo,huawei,honor}){
   VendorGuide.Step last=set[set.length-1];
   check(last.title.contains("最近任务"),"定制系统最后一步是最近任务加锁");
   check(last.targets.length==1&&"__manual__".equals(last.targets[0].action),"加锁步骤标记为手动，不假装能自动跳转");
  }

  // 系统可检测的电池优化入口必须排在候选里，便于自动跳过
  check(hasBatteryAction(huawei),"华为步骤包含可检测的电池优化入口");
  check(hasBatteryAction(generic),"通用步骤包含可检测的电池优化入口");

  System.out.println("PASS VendorGuideTests ("+passed+" checks)");
 }

 static boolean hasBatteryAction(VendorGuide.Step[] steps){
  for(VendorGuide.Step s:steps)for(VendorGuide.Target t:s.targets)if(VendorGuide.ACTION_IGNORE_BATTERY.equals(t.action))return true;
  return false;
 }

 /** 每个步骤的最后一个候选都应该是通用兜底，保证前面全部不可用时仍有去处。 */
 static void assertFallback(String brand,VendorGuide.Step[] steps){
  for(int i=0;i<steps.length;i++){
   VendorGuide.Target[] targets=steps[i].targets;
   VendorGuide.Target last=targets[targets.length-1];
   boolean fallback=last.isAction()&&(VendorGuide.ACTION_APP_DETAILS.equals(last.action)||VendorGuide.ACTION_VIEW_POWER_DETAIL.equals(last.action)||"__manual__".equals(last.action));
   check(fallback,brand+" 步骤 "+i+" 以通用兜底入口收尾");
  }
 }
}
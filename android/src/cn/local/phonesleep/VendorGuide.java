package cn.local.phonesleep;

/**
 * 各品牌的后台保活设置引导。
 *
 * 这里只描述“要去哪个页面”，不构造 Intent、不查询包管理器，
 * 所以可以脱离设备对内容和顺序做逻辑测试。
 */
final class VendorGuide {

 /** 一个候选入口：显式组件（包名+类名）或系统 action（action 非空）。 */
 static final class Target {
  final String packageName,className,action,label;
  Target(String packageName,String className,String action,String label){
   this.packageName=packageName;this.className=className;this.action=action;this.label=label;
  }
  static Target of(String pkg,String cls,String label){return new Target(pkg,cls,null,label);}
  static Target action(String action,String label){return new Target(null,null,action,label);}
  boolean isAction(){return action!=null;}
 }

 /** 一个后台设置步骤。manualPath 是入口不可用时的通用路径，必须能照着做。 */
 static final class Step {
  final String title,hint,manualPath,confirmPrompt;
  final Target[] targets;
  Step(String title,String hint,String manualPath,String confirmPrompt,Target[] targets){
   this.title=title;this.hint=hint;this.manualPath=manualPath;this.confirmPrompt=confirmPrompt;this.targets=targets;
  }
 }

 static final String ACTION_APP_DETAILS="android.settings.APPLICATION_DETAILS_SETTINGS";
 static final String ACTION_IGNORE_BATTERY="android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
 static final String ACTION_VIEW_POWER_DETAIL="android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL";

 static Step[] steps(VendorProfile profile){
  switch(profile.brand){
   case VendorProfile.VIVO:return vivo();
   case VendorProfile.XIAOMI:return xiaomi();
   case VendorProfile.OPPO:return oppo();
   case VendorProfile.HUAWEI:return huawei("com.huawei.systemmanager");
   case VendorProfile.HONOR:return huawei("com.hihonor.systemmanager");
   default:return generic();
  }
 }

 /** 最近任务加锁：所有定制系统都有，但没有可靠的跳转入口，只能手动。 */
 static Step recentLock(){
  return new Step("最近任务加锁",
   "打开最近任务，在本应用卡片上点菜单或下拉，选择锁定 / 加锁，然后回到本页。",
   "从屏幕底部上滑并停留 → 找到「一键息屏」卡片 → 点卡片右上角菜单或下拉卡片 → 选择锁定。",
   "是否已经在最近任务里锁定「一键息屏」？",
   new Target[]{Target.action("__manual__","最近任务（无自动入口）")});
 }

 static Step[] vivo(){
  return new Step[]{
   new Step("允许后台运行",
    "在本应用中允许后台高耗电 / 后台运行，或不限制后台。",
    "设置 → 电池 → 后台高耗电 → 找到「一键息屏」→ 允许后台高耗电。",
    "是否已允许「一键息屏」后台高耗电？",
    new Target[]{
     Target.of("com.vivo.abe","com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity","后台高耗电"),
     Target.action(ACTION_VIEW_POWER_DETAIL,"耗电详情"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   new Step("允许自启动",
    "在自启动管理中打开「一键息屏」的开关。",
    "设置 → 应用与权限 → 权限管理 → 自启动 → 找到「一键息屏」→ 打开。",
    "是否已开启「一键息屏」的自启动？",
    new Target[]{
     Target.of("com.vivo.permissionmanager","com.vivo.permissionmanager.activity.BgStartUpManagerActivity","自启动管理"),
     Target.of("com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.BgStartUpManager","自启动管理"),
     Target.of("com.iqoo.secure","com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity","自启动白名单"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   recentLock()};
 }

 static Step[] xiaomi(){
  return new Step[]{
   new Step("省电策略改为无限制",
    "把本应用的省电策略改成「无限制」，避免息屏一段时间后被切断连接。",
    "设置 → 应用设置 → 应用管理 → 一键息屏 → 省电策略 → 无限制。",
    "是否已把省电策略改为「无限制」？",
    new Target[]{
     Target.of("com.miui.powerkeeper","com.miui.powerkeeper.ui.HiddenAppsConfigActivity","省电策略"),
     Target.of("com.miui.powerkeeper","com.miui.powerkeeper.ui.HiddenAppsContainerManagementActivity","后台运行管理"),
     Target.action(ACTION_VIEW_POWER_DETAIL,"耗电详情"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   new Step("允许自启动",
    "在自启动管理中允许「一键息屏」自启动。",
    "设置 → 应用设置 → 应用管理 → 一键息屏 → 自启动 → 打开。",
    "是否已开启「一键息屏」的自启动？",
    new Target[]{
     Target.of("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity","自启动管理"),
     Target.of("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity","权限管理"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   recentLock()};
 }

 static Step[] oppo(){
  return new Step[]{
   new Step("允许后台运行",
    "在耗电管理里允许「一键息屏」后台运行，并关掉智能省电限制。",
    "设置 → 电池 → 耗电管理（应用耗电管理）→ 一键息屏 → 允许后台运行。",
    "是否已允许「一键息屏」后台运行？",
    new Target[]{
     Target.of("com.coloros.oppoguardelf","com.coloros.powermanager.fuelgaue.PowerUsageModelActivity","耗电管理"),
     Target.of("com.oplus.battery","com.oplus.powermanager.fuelgaue.PowerUsageModelActivity","耗电管理"),
     Target.of("com.coloros.oppoguardelf","com.coloros.powermanager.fuelgaue.PowerConsumptionActivity","耗电详情"),
     Target.action(ACTION_VIEW_POWER_DETAIL,"耗电详情"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   new Step("允许自启动",
    "在自启动管理中允许「一键息屏」自启动。",
    "设置 → 应用管理 → 自启动管理 → 找到「一键息屏」→ 打开。",
    "是否已开启「一键息屏」的自启动？",
    new Target[]{
     Target.of("com.coloros.safecenter","com.coloros.safecenter.permission.startup.StartupAppListActivity","自启动管理"),
     Target.of("com.coloros.safecenter","com.coloros.safecenter.startupapp.StartupAppListActivity","自启动管理"),
     Target.of("com.oplus.safecenter","com.oplus.safecenter.startupapp.StartupAppListActivity","自启动管理"),
     Target.of("com.oppo.safe","com.oppo.safe.permission.startup.StartupAppListActivity","自启动管理"),
     Target.of("com.oneplus.security","com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity","自启动管理"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   recentLock()};
 }

 /** 华为与荣耀的入口结构一致，只是包名前缀不同；各自只使用自己的包，不互相串。 */
 static Step[] huawei(String pkg){
  return new Step[]{
   new Step("应用启动管理",
    "把本应用从「自动管理」改为「手动管理」，并打开自启动、关联启动、后台活动三项。",
    "设置 → 应用和服务 → 应用启动管理 → 找到「一键息屏」→ 关闭自动管理 → 打开全部三项开关。",
    "是否已改为手动管理并打开三项开关？",
    new Target[]{
     Target.of(pkg,pkg+".appcontrol.activity.StartupAppControlActivity","应用启动管理"),
     Target.of(pkg,pkg+".startupmgr.ui.StartupNormalAppListActivity","应用启动管理"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   new Step("忽略电池优化",
    "允许本应用忽略电池优化，避免息屏后被冻结。",
    "设置 → 电池 → 更多电池设置 → 忽略电池优化 → 允许「一键息屏」。",
    "是否已允许「一键息屏」忽略电池优化？",
    new Target[]{
     Target.action(ACTION_IGNORE_BATTERY,"忽略电池优化"),
     Target.of(pkg,pkg+".optimize.process.ProtectActivity","受保护应用"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   recentLock()};
 }

 /** 未知品牌：只给标准 Android 能做的最短路径。 */
 static Step[] generic(){
  return new Step[]{
   new Step("加入电池优化白名单",
    "允许本应用在后台持续运行。部分系统不提供快捷入口，可按下方路径手动查找。",
    "设置 → 电池 → 电池优化（或「后台限制」）→ 找到「一键息屏」→ 选择不优化。",
    "是否已允许「一键息屏」忽略电池优化？",
    new Target[]{
     Target.action(ACTION_IGNORE_BATTERY,"忽略电池优化"),
     Target.action(ACTION_VIEW_POWER_DETAIL,"耗电详情"),
     Target.action(ACTION_APP_DETAILS,"应用信息")}),
   new Step("解除后台限制",
    "在应用信息页确认没有开启「限制后台活动」或「省电模式」。",
    "设置 → 应用 → 一键息屏 → 电池 / 后台限制 → 选择不受限制。",
    "是否已确认本应用没有再被限制后台活动？",
    new Target[]{
     Target.action(ACTION_APP_DETAILS,"应用信息")})};
 }
}
package cn.local.phonesleep;

import java.util.HashMap;

/**
 * 后台设置步骤推进规则的测试。
 * 重点：步骤数量随品牌变化，推进和「全部完成」判断都不能写死三步。
 */
class BackgroundPlanTests {
 static int passed;
 static void check(boolean ok,String label){if(!ok)throw new AssertionError("FAIL "+label);passed++;}
 static void eq(int actual,int expected,String label){check(actual==expected,label+"（实际 "+actual+"，期望 "+expected+"）");}

 public static void main(String[] args){
  // 三步（vivo / 小米 / OPPO / 华为 / 荣耀）
  eq(BackgroundPlan.next(new boolean[]{false,false,false}),0,"空进度从第一步开始");
  eq(BackgroundPlan.next(new boolean[]{true,false,false}),1,"已确认第一步则到第二步");
  eq(BackgroundPlan.next(new boolean[]{true,true,false}),2,"已确认前两步则到第三步");
  eq(BackgroundPlan.next(new boolean[]{true,true,true}),-1,"全部确认后结束");
  eq(BackgroundPlan.next(new boolean[]{true,false,true}),1,"中间缺项会回到该步而不是跳过");

  // 两步（通用品牌）
  eq(BackgroundPlan.next(new boolean[]{false,false}),0,"两步计划从第一步开始");
  eq(BackgroundPlan.next(new boolean[]{true,false}),1,"两步计划推进到第二步");
  eq(BackgroundPlan.next(new boolean[]{true,true}),-1,"两步计划全部确认后结束");

  // 边界
  eq(BackgroundPlan.next(new boolean[0]),-1,"空计划直接结束");
  eq(BackgroundPlan.next(null),-1,"空引用不抛异常");

  // 计数
  eq(BackgroundPlan.done(new boolean[]{false,false,false}),0,"计数：零项");
  eq(BackgroundPlan.done(new boolean[]{true,false,true}),2,"计数：跳过中间项");
  eq(BackgroundPlan.done(new boolean[]{true,true,true}),3,"计数：全部");
  eq(BackgroundPlan.done(null),0,"计数：空引用不抛异常");

  // 与品牌引导表对齐：计划长度必须等于该品牌的步骤数
  VendorProfile vivo=VendorProfile.classify("vivo","vivo","","X100",34,new HashMap<String,String>());
  VendorProfile generic=VendorProfile.classify("Google","google","","sdk_gphone",33,new HashMap<String,String>());
  int vivoSteps=VendorGuide.steps(vivo).length,genericSteps=VendorGuide.steps(generic).length;
  eq(BackgroundPlan.next(new boolean[vivoSteps]),0,"vivo 计划长度与引导表一致");
  boolean[] doneVivo=new boolean[vivoSteps];
  for(int i=0;i<doneVivo.length;i++)doneVivo[i]=true;
  eq(BackgroundPlan.next(doneVivo),-1,"vivo 全部确认后结束");
  eq(BackgroundPlan.next(new boolean[genericSteps]),0,"通用计划长度与引导表一致");

  System.out.println("PASS BackgroundPlanTests ("+passed+" checks)");
 }
}
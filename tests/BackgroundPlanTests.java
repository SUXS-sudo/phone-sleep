package cn.local.phonesleep;
class BackgroundPlanTests {
 static void check(int actual,int expected){if(actual!=expected)throw new AssertionError(actual+" != "+expected);}
 public static void main(String[] args){check(BackgroundPlan.next(false,false,false),0);check(BackgroundPlan.next(true,false,false),1);check(BackgroundPlan.next(true,true,false),2);check(BackgroundPlan.next(true,true,true),-1);check(BackgroundPlan.next(false,true,true),0);check(BackgroundPlan.next(true,false,true),1);System.out.println("PASS background setup order and confirmed-step skipping");}
}

package cn.local.phonesleep;
class AuthorizationPlanTests {
 static void check(int actual,int expected,String label){if(actual!=expected)throw new AssertionError(label+": "+actual);System.out.println("PASS "+label);}
 public static void main(String[] args){
  check(AuthorizationPlan.next(false,false,false,0),AuthorizationPlan.LOCK,"first missing required permission");
  check(AuthorizationPlan.next(false,false,false,1),AuthorizationPlan.STOP,"required permission denial stops flow");
  check(AuthorizationPlan.next(true,false,false,0),AuthorizationPlan.NOTIFICATIONS,"granted lock permission skipped");
  check(AuthorizationPlan.next(true,true,false,0),AuthorizationPlan.BATTERY,"granted notification permission skipped");
  check(AuthorizationPlan.next(true,false,false,2),AuthorizationPlan.BATTERY,"notification denial is not repeated");
  check(AuthorizationPlan.next(true,false,false,6),AuthorizationPlan.DONE,"optional denials finish without a loop");
  check(AuthorizationPlan.next(true,true,true,0),AuthorizationPlan.DONE,"fully authorized flow opens no settings");
  check(AuthorizationPlan.next(false,true,true,7),AuthorizationPlan.STOP,"revoked required permission stops flow");
 }
}

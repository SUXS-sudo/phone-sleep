package cn.local.phonesleep;
final class AuthorizationPlan {
 static final int LOCK=1,NOTIFICATIONS=2,BATTERY=4,DONE=8,STOP=16;
 static int next(boolean lock,boolean notifications,boolean battery,int attempted){
  if(!lock)return (attempted&LOCK)==0?LOCK:STOP;
  if(!notifications&&(attempted&NOTIFICATIONS)==0)return NOTIFICATIONS;
  if(!battery&&(attempted&BATTERY)==0)return BATTERY;
  return DONE;
 }
}

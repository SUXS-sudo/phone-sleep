package cn.local.phonesleep;

/**
 * 后台设置步骤的推进规则：按顺序返回第一个用户尚未确认的步骤，全部确认后返回 -1。
 * 步骤数量随品牌变化，所以这里用数组长度决定，不再写死三步。
 */
final class BackgroundPlan {
 static int next(boolean[] confirmed){
  if(confirmed==null)return -1;
  for(int i=0;i<confirmed.length;i++)if(!confirmed[i])return i;
  return -1;
 }

 /** 已确认的步骤数量，用于界面显示进度。 */
 static int done(boolean[] confirmed){
  if(confirmed==null)return 0;
  int n=0;
  for(boolean value:confirmed)if(value)n++;
  return n;
 }
}
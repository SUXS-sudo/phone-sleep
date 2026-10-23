package cn.local.phonesleep;
final class BackgroundPlan {
 static int next(boolean background,boolean autoStart,boolean recentLock){if(!background)return 0;if(!autoStart)return 1;if(!recentLock)return 2;return -1;}
}

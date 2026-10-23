package cn.local.phonesleep;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
public class LinkService extends Service {
 public static volatile boolean running=false;
 volatile boolean active; DatagramSocket udp; ServerSocket tcp; WifiManager.MulticastLock wifi;
 ExecutorService clients; final Set<String> seen=Collections.synchronizedSet(new LinkedHashSet<String>());
 public void onCreate(){super.onCreate();MainActivity.init(this);
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("link","电脑息屏连接",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,"link").setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("一键息屏 · 连接已开启").setContentText("电脑按一下快捷键，让手机息屏").setContentIntent(pi).setOngoing(true).build();
  startForeground(1,n);active=true;running=true;
  WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifi=wm.createMulticastLock("phone-sleep-discovery");wifi.setReferenceCounted(false);wifi.acquire();
  clients=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),new ThreadPoolExecutor.AbortPolicy());
  new Thread(()->discovery(),"discovery").start();new Thread(()->listen(),"commands").start();
 }
 public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
 public IBinder onBind(Intent i){return null;}
 String id(){return getSharedPreferences("link",0).getString("id","");}
 void discovery(){try{udp=new DatagramSocket(null);udp.setReuseAddress(true);udp.bind(new InetSocketAddress(47651));byte[] b=new byte[128];
  while(active){DatagramPacket p=new DatagramPacket(b,b.length);udp.receive(p);String s=new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8");if(!"PHONE_SLEEP_DISCOVER_V1".equals(s))continue;
   String name=android.util.Base64.encodeToString(Build.MODEL.getBytes("UTF-8"),android.util.Base64.NO_WRAP);byte[] reply=("PHONE_SLEEP_V1|"+id()+"|47652|"+name).getBytes("UTF-8");udp.send(new DatagramPacket(reply,reply.length,p.getAddress(),p.getPort()));}
 }catch(Exception e){if(active)android.util.Log.e("PhoneSleep","Discovery failed",e);}}
 void listen(){try{tcp=new ServerSocket(47652);while(active){final Socket s=tcp.accept();s.setSoTimeout(2500);try{clients.execute(()->handle(s));}catch(RejectedExecutionException e){s.close();}}}catch(Exception e){if(active)android.util.Log.e("PhoneSleep","Command server failed",e);}}
 static String line(InputStream in)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();int c;while((c=in.read())!=-1&&c!='\n'){if(b.size()>=512)throw new IOException("oversize");b.write(c);}return b.toString("UTF-8").trim();}
 static byte[] unhex(String s){if(s.length()%2!=0)throw new IllegalArgumentException();byte[] r=new byte[s.length()/2];for(int i=0;i<r.length;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return r;}
 String authenticate(String request)throws Exception{
  String[] p=request.split("\\|",-1);if(p.length!=5||!p[0].equals(id())||!p[1].matches("[a-f0-9]{32}")||!(p[3].equals("SLEEP")||p[3].equals("PING")))return "DENIED";
  long ts=Long.parseLong(p[2]);if(Math.abs(System.currentTimeMillis()/1000-ts)>60)return "CLOCK";
  Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(unhex(getSharedPreferences("link",0).getString("key","")),"HmacSHA256"));
  byte[] expected=m.doFinal((p[0]+"\n"+p[1]+"\n"+p[2]+"\n"+p[3]).getBytes("UTF-8"));if(!MessageDigest.isEqual(expected,unhex(p[4])))return "DENIED";
  synchronized(seen){if(seen.contains(p[1]))return "REPLAY";seen.add(p[1]);if(seen.size()>512)seen.remove(seen.iterator().next());}
  if(p[3].equals("PING"))return canLock()?"READY":"NO_PERMISSION";
  final CountDownLatch latch=new CountDownLatch(1);final boolean[] success={false};new Handler(Looper.getMainLooper()).post(()->{try{if(SleepAccess.instance!=null){success[0]=SleepAccess.instance.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);}else{DevicePolicyManager d=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);if(d.isAdminActive(new ComponentName(this,SleepAdmin.class))){d.lockNow();success[0]=true;}}}catch(Exception ignored){}finally{latch.countDown();}});
  latch.await(1500,TimeUnit.MILLISECONDS);return success[0]?"OK":"NO_PERMISSION";
 }
 boolean canLock(){return SleepAccess.instance!=null||((DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE)).isAdminActive(new ComponentName(this,SleepAdmin.class));}
 void handle(Socket s){try{String request=line(s.getInputStream());String result=authenticate(request);String[] parts=request.split("\\|",-1);String nonce=parts.length>1?parts[1]:"";Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(unhex(getSharedPreferences("link",0).getString("key","")),"HmacSHA256"));String sig=MainActivity.hex(m.doFinal((nonce+"\n"+result).getBytes("UTF-8")));s.getOutputStream().write((result+"|"+sig+"\n").getBytes("UTF-8"));s.getOutputStream().flush();}catch(Exception ignored){}finally{try{s.close();}catch(Exception ignored){}}}
 public void onDestroy(){active=false;running=false;if(udp!=null)udp.close();try{if(tcp!=null)tcp.close();}catch(Exception ignored){}if(clients!=null)clients.shutdownNow();if(wifi!=null&&wifi.isHeld())wifi.release();super.onDestroy();}
}

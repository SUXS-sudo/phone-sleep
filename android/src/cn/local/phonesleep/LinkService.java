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
 public static volatile boolean discoveryReady=false,commandsReady=false;
 volatile boolean active; DatagramSocket udp; ServerSocket tcp; WifiManager.MulticastLock wifi;
 Thread discoveryThread,commandThread;
 ExecutorService clients; final Set<String> seen=Collections.synchronizedSet(new LinkedHashSet<String>());
 public void onCreate(){super.onCreate();MainActivity.init(this);
  NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("link","电脑息屏连接",NotificationManager.IMPORTANCE_LOW));
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder builder=new Notification.Builder(this,"link").setSmallIcon(android.R.drawable.ic_lock_idle_lock).setContentTitle("一键息屏 · 连接已开启").setContentText("保持后台运行，电脑快捷键可随时息屏").setContentIntent(pi).setOngoing(true).setShowWhen(false).setCategory(Notification.CATEGORY_SERVICE);
  if(Build.VERSION.SDK_INT>=31)builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
  Notification n=builder.build();
  startForeground(1,n);active=true;running=true;
  WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifi=wm.createMulticastLock("phone-sleep-discovery");wifi.setReferenceCounted(false);wifi.acquire();
  clients=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8),new ThreadPoolExecutor.AbortPolicy());
  ensureWorkers();
 }
 synchronized void ensureWorkers(){if(!active)return;if(discoveryThread==null||!discoveryThread.isAlive()){discoveryThread=new Thread(()->discovery(),"discovery");discoveryThread.start();}if(commandThread==null||!commandThread.isAlive()){commandThread=new Thread(()->listen(),"commands");commandThread.start();}}
 public int onStartCommand(Intent i,int flags,int id){if(!getSharedPreferences("link",0).getBoolean("enabled",false)){stopSelf();return START_NOT_STICKY;}ensureWorkers();return START_STICKY;}
 public IBinder onBind(Intent i){return null;}
 String id(){return getSharedPreferences("link",0).getString("id","");}
 void waitToRetry(){try{Thread.sleep(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
 void discovery(){while(active){DatagramSocket socket=null;try{socket=new DatagramSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(47651));socket.setSoTimeout(1500);udp=socket;discoveryReady=true;byte[] b=new byte[128];
  while(active){DatagramPacket p=new DatagramPacket(b,b.length);try{socket.receive(p);}catch(SocketTimeoutException expected){continue;}String s=new String(p.getData(),p.getOffset(),p.getLength(),"UTF-8");if(!"PHONE_SLEEP_DISCOVER_V1".equals(s))continue;
   String name=android.util.Base64.encodeToString(Build.MODEL.getBytes("UTF-8"),android.util.Base64.NO_WRAP);byte[] reply=("PHONE_SLEEP_V1|"+id()+"|47652|"+name).getBytes("UTF-8");socket.send(new DatagramPacket(reply,reply.length,p.getAddress(),p.getPort()));}
 }catch(Exception e){if(active)android.util.Log.w("PhoneSleep","Discovery restarting",e);}finally{discoveryReady=false;if(socket!=null)socket.close();if(udp==socket)udp=null;}if(active)waitToRetry();}}
 void listen(){while(active){ServerSocket server=null;try{server=new ServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(47652),8);server.setSoTimeout(1500);tcp=server;commandsReady=true;while(active){Socket accepted;try{accepted=server.accept();}catch(SocketTimeoutException expected){continue;}final Socket s=accepted;try{s.setSoTimeout(2500);s.setTcpNoDelay(true);clients.execute(()->handle(s));}catch(Exception rejected){s.close();}}}catch(Exception e){if(active)android.util.Log.w("PhoneSleep","Command server restarting",e);}finally{commandsReady=false;try{if(server!=null)server.close();}catch(Exception ignored){}if(tcp==server)tcp=null;}if(active)waitToRetry();}}
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
 public void onDestroy(){active=false;running=false;discoveryReady=false;commandsReady=false;if(udp!=null)udp.close();try{if(tcp!=null)tcp.close();}catch(Exception ignored){}if(clients!=null)clients.shutdownNow();if(discoveryThread!=null)discoveryThread.interrupt();if(commandThread!=null)commandThread.interrupt();if(wifi!=null&&wifi.isHeld())wifi.release();super.onDestroy();}
}

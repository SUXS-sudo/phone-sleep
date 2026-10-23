using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;
class ConnectionRecoveryTests {
 static readonly byte[] Key=Protocol.Unhex("000102030405060708090a0b0c0d0e0f");
 static readonly string Id="18a1e8e5-cee8-44f4-b52c-e2a6242e9566";
 static bool alive=true;static int sleeps;static TcpListener listener;
 static void Assert(bool value,string name){if(!value)throw new Exception(name);Console.WriteLine("PASS "+name);}
 static void Serve(){while(alive){try{using(var c=listener.AcceptTcpClient()){c.ReceiveTimeout=1500;var stream=c.GetStream();var reader=new StreamReader(stream,Encoding.UTF8);string request=reader.ReadLine();if(request==null)continue;string[] p=request.Split('|');if(p.Length!=5)continue;if(p[4]!=Protocol.Sign(Key,p[0]+"\n"+p[1]+"\n"+p[2]+"\n"+p[3]))throw new Exception("invalid request MAC");if(p[3]=="SLEEP"&&Interlocked.Increment(ref sleeps)==1)continue;string result=p[3]=="PING"?"READY":"OK";byte[] reply=Encoding.UTF8.GetBytes(result+"|"+Protocol.Sign(Key,p[1]+"\n"+result)+"\n");stream.Write(reply,0,reply.Length);}}catch{if(!alive)return;}}}
 static bool WaitFor(Func<bool> predicate,int ms){var until=DateTime.UtcNow.AddMilliseconds(ms);while(DateTime.UtcNow<until){Application.DoEvents();if(predicate())return true;Thread.Sleep(20);}return false;}
 [STAThread]static void Main(){
  var legacy=ProtectedData.Protect(Encoding.UTF8.GetBytes(Id+"\n"+Protocol.Hex(Key)),null,DataProtectionScope.CurrentUser);
  var old=PairingRecord.Decode(legacy);Assert(old.Id==Id&&old.Endpoint==null&&Protocol.Hex(old.Key)==Protocol.Hex(Key),"existing pairing format preserved");
  var endpoint=new Device{Id=Id,Name="test phone",Address=IPAddress.Loopback,Port=47652};
  var restored=PairingRecord.Decode(new PairingRecord{Id=Id,Key=Key,Endpoint=endpoint}.Encode());Assert(restored.Endpoint.Address.Equals(IPAddress.Loopback)&&restored.Endpoint.Port==47652,"last known endpoint survives restart");
  string config=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"paired-device.dat");if(File.Exists(config))throw new Exception("Test directory must not contain a pairing file");
  listener=new TcpListener(IPAddress.Loopback,47652);listener.Start();var worker=new Thread(Serve){IsBackground=true};worker.Start();File.WriteAllBytes(config,new PairingRecord{Id=Id,Key=Key,Endpoint=endpoint}.Encode());
  Application.EnableVisualStyles();try{using(var form=new MainForm(false)){form.Opacity=0;form.ShowInTaskbar=false;form.Show();
   var status=(Label)typeof(MainForm).GetField("status",BindingFlags.Instance|BindingFlags.NonPublic).GetValue(form);
   var pill=(Label)typeof(MainForm).GetField("pill",BindingFlags.Instance|BindingFlags.NonPublic).GetValue(form);
   Assert(WaitFor(()=>pill.Text.Contains("手机在线"),4000),"authenticated heartbeat restores connection without broadcast discovery");
   typeof(MainForm).GetMethod("SleepPhone",BindingFlags.Instance|BindingFlags.NonPublic).Invoke(form,null);
   Assert(WaitFor(()=>status.Text.StartsWith("已息屏"),6000),"sleep command recovers after one dropped connection");Assert(sleeps==2,"sleep retry is bounded to one additional attempt");form.Close();
  }}finally{alive=false;listener.Stop();worker.Join(1000);File.Delete(config);}
 }
}

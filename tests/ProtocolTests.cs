using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
class ProtocolTests {
 static void Assert(bool value,string label){if(!value)throw new Exception(label);Console.WriteLine("PASS "+label);}
 public static void Main(){
  byte[] key=Protocol.Unhex("000102030405060708090a0b0c0d0e0f");
  Assert(Protocol.Hex(key)=="000102030405060708090a0b0c0d0e0f","connection code round trip");
  Assert(new Hotkey(0x76,0).Valid(),"single function key accepted");
  Assert(!new Hotkey(0x41,0).Valid(),"bare typing key rejected");
  Assert(!new Hotkey(0x41,4).Valid(),"shift-only typing key rejected");
  Assert(new Hotkey(0x41,2).Valid(),"Ctrl combination accepted");
  Assert(Hotkey.Parse("118,0").ToString()=="F7","hotkey settings round trip");
  Assert(Hotkey.Parse("65,3").ToString()=="Ctrl + Alt + A","modifier display");
  byte[] rfc=new byte[20];for(int i=0;i<20;i++)rfc[i]=0x0b;
  Assert(Protocol.Sign(rfc,"Hi There")=="b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7","RFC 4231 HMAC-SHA256 vector");
  TestServer(key,false);TestServer(key,true);
 }
 static void TestServer(byte[] key,bool forge){
  var listener=new TcpListener(IPAddress.Loopback,0);listener.Start();Exception error=null;
  var d=new Device{Id=Guid.NewGuid().ToString(),Address=IPAddress.Loopback,Port=((IPEndPoint)listener.LocalEndpoint).Port};
  var thread=new Thread(()=>{try{using(var c=listener.AcceptTcpClient()){var s=c.GetStream();var reader=new StreamReader(s,Encoding.UTF8);string[] p=reader.ReadLine().Split('|');Assert(p.Length==5&&p[0]==d.Id&&p[3]=="PING","request framing");Assert(p[4]==Protocol.Sign(key,p[0]+"\n"+p[1]+"\n"+p[2]+"\n"+p[3]),"request authentication");string sig=forge?new string('0',64):Protocol.Sign(key,p[1]+"\nREADY");byte[] result=Encoding.UTF8.GetBytes("READY|"+sig+"\n");s.Write(result,0,result.Length);}}catch(Exception e){error=e;}finally{listener.Stop();}});thread.Start();
  if(forge){bool rejected=false;try{Protocol.Send(d,key,"PING");}catch(IOException){rejected=true;}Assert(rejected,"forged server response rejected");}
  else Assert(Protocol.Send(d,key,"PING")=="READY","authenticated request and response");
  thread.Join();if(error!=null)throw error;
 }
}

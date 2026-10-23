using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

class Device {
 public string Id,Name; public IPAddress Address; public int Port; public DateTime Seen,Verified;
 public override string ToString(){return Name+"  ("+Address+")";}
}
class Protocol {
 public static byte[] Unhex(string text){byte[] b=new byte[text.Length/2];for(int i=0;i<b.Length;i++)b[i]=Convert.ToByte(text.Substring(2*i,2),16);return b;}
 public static string Hex(byte[] b){return BitConverter.ToString(b).Replace("-","").ToLowerInvariant();}
 public static string Sign(byte[] key,string text){using(var h=new HMACSHA256(key))return Hex(h.ComputeHash(Encoding.UTF8.GetBytes(text)));}
 public static string Send(Device d,byte[] key,string command){
  string nonce=Guid.NewGuid().ToString("N");string ts=((long)(DateTime.UtcNow-new DateTime(1970,1,1,0,0,0,DateTimeKind.Utc)).TotalSeconds).ToString(System.Globalization.CultureInfo.InvariantCulture);
  string payload=d.Id+"\n"+nonce+"\n"+ts+"\n"+command;
  string request=d.Id+"|"+nonce+"|"+ts+"|"+command+"|"+Sign(key,payload)+"\n";
  using(var client=new TcpClient()){
   var connect=client.BeginConnect(d.Address,d.Port,null,null);
   using(connect.AsyncWaitHandle){if(!connect.AsyncWaitHandle.WaitOne(1200))throw new IOException("手机连接超时");client.EndConnect(connect);}
   client.ReceiveTimeout=2200;client.SendTimeout=1200;client.NoDelay=true;
   var stream=client.GetStream();byte[] b=Encoding.UTF8.GetBytes(request);stream.Write(b,0,b.Length);
   string response;using(var reader=new StreamReader(stream,Encoding.UTF8))response=reader.ReadLine();
   if(response==null||response.Length>256)throw new IOException("手机未返回有效结果");string[] parts=response.Split('|');
   if(parts.Length!=2||!String.Equals(parts[1],Sign(key,nonce+"\n"+parts[0]),StringComparison.Ordinal))throw new InvalidDataException("连接码不正确，或手机已重置配对");
   return parts[0];
  }
 }
}
class PairingRecord {
 public string Id;public byte[] Key;public Device Endpoint;
 public static PairingRecord Decode(byte[] encrypted){
  string[] p=Encoding.UTF8.GetString(ProtectedData.Unprotect(encrypted,null,DataProtectionScope.CurrentUser)).Split('\n');Guid id;
  if(p.Length<2||!Guid.TryParse(p[0],out id)||p[1].Length!=32)throw new InvalidDataException("无效配对信息");
  var record=new PairingRecord{Id=p[0],Key=Protocol.Unhex(p[1])};IPAddress ip;int port;
  if(p.Length>=5&&IPAddress.TryParse(p[2],out ip)&&int.TryParse(p[3],out port)&&port==47652)record.Endpoint=new Device{Id=p[0],Name=Encoding.UTF8.GetString(Convert.FromBase64String(p[4])),Address=ip,Port=port};
  return record;
 }
 public byte[] Encode(){
  string text=Id+"\n"+Protocol.Hex(Key);
  if(Endpoint!=null)text+="\n"+Endpoint.Address+"\n"+Endpoint.Port+"\n"+Convert.ToBase64String(Encoding.UTF8.GetBytes(Endpoint.Name??"我的手机"));
  return ProtectedData.Protect(Encoding.UTF8.GetBytes(text),null,DataProtectionScope.CurrentUser);
 }
}
class Hotkey {
 public int Key,Modifiers;
 public Hotkey(int key,int modifiers){Key=key;Modifiers=modifiers;}
 public bool Valid(){return Key>=0x30&&Key<=0xfe&&Key!=0x5b&&Key!=0x5c&&(Modifiers&~7)==0&&(Modifiers%4!=0||(Key>=0x70&&Key<=0x87));}
 public override string ToString(){return ((Modifiers&2)!=0?"Ctrl + ":"")+((Modifiers&1)!=0?"Alt + ":"")+((Modifiers&4)!=0?"Shift + ":"")+((Keys)Key).ToString();}
 public string Encode(){return Key+","+Modifiers;}
 public static Hotkey Parse(string value){string[] p=value.Split(',');var h=new Hotkey(int.Parse(p[0]),int.Parse(p[1]));if(!h.Valid())throw new FormatException();return h;}
}

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class Theme {
 public static Color Ink=Color.FromArgb(27,39,61),Muted=Color.FromArgb(115,129,149),Blue=Color.FromArgb(53,104,238),Background=Color.FromArgb(244,247,252),Pale=Color.FromArgb(234,240,255);
 public static GraphicsPath Round(RectangleF r,float radius){float d=radius*2;var p=new GraphicsPath();p.AddArc(r.X,r.Y,d,d,180,90);p.AddArc(r.Right-d,r.Y,d,d,270,90);p.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);p.AddArc(r.X,r.Bottom-d,d,d,90,90);p.CloseFigure();return p;}
 public static Label Label(string text,float size,Color color,int x,int y,int w,int h,bool bold=false){return new Label{Text=text,Font=new Font("Microsoft YaHei UI",size,bold?FontStyle.Bold:FontStyle.Regular),ForeColor=color,BackColor=Color.Transparent,Location=new Point(x,y),Size=new Size(w,h)};}
}
class Card:Panel {
 public Card(){DoubleBuffered=true;BackColor=Theme.Background;}
 protected override void OnPaint(PaintEventArgs e){base.OnPaint(e);e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;using(var p=Theme.Round(new RectangleF(0,0,Width-1,Height-1),18)){using(var brush=new SolidBrush(Color.White))e.Graphics.FillPath(brush,p);using(var pen=new Pen(Color.FromArgb(228,234,244)))e.Graphics.DrawPath(pen,p);}}
}
class SoftButton:Button {
 public bool Primary;bool hover;
 public SoftButton(){SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer,true);Cursor=Cursors.Hand;FlatStyle=FlatStyle.Flat;FlatAppearance.BorderSize=0;Font=new Font("Microsoft YaHei UI",10,FontStyle.Bold);}
 protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
 protected override void OnMouseLeave(EventArgs e){hover=false;Invalidate();base.OnMouseLeave(e);}
 protected override void OnPaint(PaintEventArgs e){e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;Color c=Primary?(Enabled?(hover?Color.FromArgb(40,87,213):Theme.Blue):Color.FromArgb(187,201,229)):(hover?Color.FromArgb(223,232,251):Theme.Pale);using(var p=Theme.Round(new RectangleF(0,0,Width-1,Height-1),12))using(var b=new SolidBrush(c))e.Graphics.FillPath(b,p);TextRenderer.DrawText(e.Graphics,Text,Font,ClientRectangle,Primary?Color.White:Theme.Blue,TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis);if(Focused)ControlPaint.DrawFocusRectangle(e.Graphics,new Rectangle(5,5,Width-10,Height-10));}
}
class PhoneGlyph:Control {
 public PhoneGlyph(){DoubleBuffered=true;Size=new Size(70,92);}
 protected override void OnPaint(PaintEventArgs e){e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;using(var p=Theme.Round(new RectangleF(9,3,50,80),11))using(var pen=new Pen(Theme.Blue,3))e.Graphics.DrawPath(pen,p);using(var pen=new Pen(Theme.Blue,3)){e.Graphics.DrawLine(pen,27,12,41,12);e.Graphics.DrawLine(pen,29,73,39,73);}using(var b=new SolidBrush(Theme.Pale))e.Graphics.FillEllipse(b,20,30,28,28);using(var pen=new Pen(Theme.Blue,2)){e.Graphics.DrawArc(pen,27,38,14,14,-45,270);e.Graphics.DrawLine(pen,34,34,34,43);}}
}
class MainForm:Form {
 [DllImport("user32.dll")]static extern bool RegisterHotKey(IntPtr h,int id,uint mod,uint key);
 [DllImport("user32.dll")]static extern bool UnregisterHotKey(IntPtr h,int id);
 [DllImport("user32.dll")]static extern bool ReleaseCapture();
 [DllImport("user32.dll")]static extern IntPtr SendMessage(IntPtr h,int msg,IntPtr w,IntPtr l);
 [DllImport("dwmapi.dll")]static extern int DwmSetWindowAttribute(IntPtr h,int attr,ref int value,int length);
 readonly object gate=new object();readonly Dictionary<string,Device> devices=new Dictionary<string,Device>();
 ComboBox list=new ComboBox();TextBox code=new TextBox();Label status,connection,pill,keyBadge,pairedName,pairedState;Panel pairedView=new Panel();
 Label codeLabel;SoftButton pair,sleep;NotifyIcon tray;System.Windows.Forms.Timer timer=new System.Windows.Forms.Timer();
 volatile bool active=true;int pairBusy,sleepBusy;string pairedId="";byte[] key;bool sleepRegistered,pairingMode;
 Hotkey sleepKey=new Hotkey(0x77,0);
 readonly string config=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"paired-device.dat");
 readonly string hotkeyConfig=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"hotkeys.txt");
 public MainForm(bool hidden){
  AutoScaleMode=AutoScaleMode.None;Text="一键息屏";ClientSize=new Size(760,600);BackColor=Theme.Background;ForeColor=Theme.Ink;Font=new Font("Microsoft YaHei UI",10);FormBorderStyle=FormBorderStyle.None;StartPosition=FormStartPosition.CenterScreen;MaximizeBox=false;DoubleBuffered=true;
  try{Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);}catch{Icon=SystemIcons.Application;}
  var title=Theme.Label("一键息屏",25,Theme.Ink,36,29,380,46,true);Controls.Add(title);Controls.Add(Theme.Label("轻按一下，让手机休息。",11,Theme.Muted,38,82,430,28));
  MouseEventHandler drag=(s,e)=>{if(e.Button==MouseButtons.Left){ReleaseCapture();SendMessage(Handle,0xA1,(IntPtr)2,IntPtr.Zero);}};title.MouseDown+=drag;MouseDown+=drag;
  var minimize=Button("−",652,25,36,30,false);minimize.Click+=(s,e)=>Hide();Controls.Add(minimize);
  var close=Button("×",696,25,30,30,false);close.Click+=(s,e)=>Close();Controls.Add(close);
  pill=Theme.Label("●  等待连接",10,Theme.Muted,550,83,175,26);pill.TextAlign=ContentAlignment.MiddleRight;Controls.Add(pill);
  var deviceCard=new Card{Location=new Point(36,132),Size=new Size(436,286)};Controls.Add(deviceCard);
  deviceCard.Controls.Add(Theme.Label("手机连接",14,Theme.Ink,24,22,380,30,true));connection=Theme.Label("同一 Wi-Fi 下，自动寻找你的手机。",9,Theme.Muted,24,57,388,25);deviceCard.Controls.Add(connection);
  list.DropDownStyle=ComboBoxStyle.DropDownList;list.FlatStyle=FlatStyle.Flat;list.BackColor=Theme.Background;list.Font=new Font("Microsoft YaHei UI",10);list.SetBounds(24,89,388,30);deviceCard.Controls.Add(list);
  codeLabel=Theme.Label("连接码 · 只需输入一次",9,Theme.Muted,24,135,388,22);deviceCard.Controls.Add(codeLabel);
  var inputFrame=new Panel{BackColor=Theme.Background,Location=new Point(24,162),Size=new Size(388,35)};deviceCard.Controls.Add(inputFrame);
  code.BorderStyle=BorderStyle.None;code.BackColor=Theme.Background;code.Font=new Font("Consolas",11);code.UseSystemPasswordChar=true;code.SetBounds(10,7,368,25);inputFrame.Controls.Add(code);
  pair=Button("配对这台手机",24,213,388,46,true);pair.Click+=(s,e)=>Pair();deviceCard.Controls.Add(pair);
  pairedView.SetBounds(24,89,388,176);pairedView.BackColor=Color.White;deviceCard.Controls.Add(pairedView);pairedView.Controls.Add(new PhoneGlyph{Location=new Point(6,10)});
  pairedName=Theme.Label("我的手机",16,Theme.Ink,100,13,280,34,true);pairedView.Controls.Add(pairedName);pairedState=Theme.Label("正在自动连接…",10,Theme.Muted,100,56,278,28);pairedView.Controls.Add(pairedState);
  pairedView.Controls.Add(Theme.Label("已记住此设备，无需重复配对",9,Theme.Muted,2,117,286,26));var change=new LinkLabel{Text="更换手机",Location=new Point(286,119),Size=new Size(98,24),LinkColor=Theme.Blue,ActiveLinkColor=Theme.Blue,BackColor=Color.White,TextAlign=ContentAlignment.TopRight};change.Click+=(s,e)=>{pairingMode=true;pairedView.Visible=false;list.Visible=true;codeLabel.Visible=true;inputFrame.Visible=true;pair.Visible=true;};pairedView.Controls.Add(change);
  var shortcutCard=new Card{Location=new Point(488,132),Size=new Size(236,286)};Controls.Add(shortcutCard);shortcutCard.Controls.Add(Theme.Label("息屏快捷键",14,Theme.Ink,24,22,192,30,true));shortcutCard.Controls.Add(Theme.Label("任何窗口，按一下即可",9,Theme.Muted,24,57,192,25));
  keyBadge=Theme.Label("F8",28,Theme.Blue,24,99,188,78,true);keyBadge.BackColor=Theme.Pale;keyBadge.TextAlign=ContentAlignment.MiddleCenter;shortcutCard.Controls.Add(keyBadge);var settings=Button("更改快捷键",24,213,188,46,false);settings.Click+=(s,e)=>ShortcutDialog();shortcutCard.Controls.Add(settings);
  sleep=Button("立即息屏",36,440,688,74,true);sleep.Font=new Font("Microsoft YaHei UI",17,FontStyle.Bold);sleep.Click+=(s,e)=>SleepPhone();Controls.Add(sleep);
  status=Theme.Label("正在寻找手机…",9,Theme.Muted,38,540,478,44);Controls.Add(status);var hide=Button("收起到托盘",568,538,156,36,false);hide.Click+=(s,e)=>Hide();Controls.Add(hide);
  var menu=new ContextMenuStrip();menu.Items.Add("立即息屏",null,(s,e)=>SleepPhone());menu.Items.Add("打开设置",null,(s,e)=>ShowWindow());menu.Items.Add("退出",null,(s,e)=>Close());tray=new NotifyIcon{Icon=Icon,Text="一键息屏",Visible=true,ContextMenuStrip=menu};tray.DoubleClick+=(s,e)=>ShowWindow();
  try{if(File.Exists(config)){string[] p=Encoding.UTF8.GetString(ProtectedData.Unprotect(File.ReadAllBytes(config),null,DataProtectionScope.CurrentUser)).Split('\n');if(p.Length!=2||p[1].Length!=32)throw new FormatException();pairedId=p[0];key=Protocol.Unhex(p[1]);status.Text="配对已保存，等待手机上线。";}}catch{status.Text="请重新输入手机连接码。";}
  pairingMode=key==null;
  try{if(File.Exists(hotkeyConfig)){string[] p=File.ReadAllLines(hotkeyConfig);sleepKey=Hotkey.Parse(p.Length>1?p[1]:p[0]);if(p.Length>1)File.WriteAllLines(hotkeyConfig,new[]{sleepKey.Encode()});}}catch{sleepKey=new Hotkey(0x77,0);}
  sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)sleepKey.Modifiers,(uint)sleepKey.Key);UpdateKeyLabel();if(!sleepRegistered)status.Text="快捷键被占用，点击「更改快捷键」换一个。";
  if(!pairingMode){list.Visible=false;codeLabel.Visible=false;inputFrame.Visible=false;pair.Visible=false;pairedView.Visible=true;}else pairedView.Visible=false;
  timer.Interval=900;timer.Tick+=(s,e)=>RefreshDevices();timer.Start();new Thread(Discover){IsBackground=true,Name="Phone discovery"}.Start();
  FormClosing+=(s,e)=>{active=false;timer.Stop();if(sleepRegistered)UnregisterHotKey(Handle,1);tray.Visible=false;tray.Dispose();};if(hidden&&key!=null)Shown+=(s,e)=>Hide();
  Shown+=(s,e)=>{try{int rounded=2;DwmSetWindowAttribute(Handle,33,ref rounded,4);}catch{}};
 }
 static SoftButton Button(string text,int x,int y,int w,int h,bool primary){return new SoftButton{Text=text,Location=new Point(x,y),Size=new Size(w,h),Primary=primary};}
 void ShowWindow(){Show();WindowState=FormWindowState.Normal;Activate();}
 protected override void WndProc(ref Message m){if(m.Msg==0x0312&&m.WParam.ToInt32()==1)SleepPhone();if(m.Msg==0x8001)ShowWindow();if(m.Msg==0x8002)SleepPhone();base.WndProc(ref m);}
 void UpdateKeyLabel(){keyBadge.Text=sleepKey.ToString();keyBadge.Font=new Font("Microsoft YaHei UI",keyBadge.Text.Length>9?12:28,FontStyle.Bold);tray.Text="一键息屏 · "+sleepKey;}
 bool ApplyKey(Hotkey next){
  if(!next.Valid())return false;if(sleepRegistered)UnregisterHotKey(Handle,1);sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)next.Modifiers,(uint)next.Key);
  if(!sleepRegistered)return false;sleepKey=next;UpdateKeyLabel();return true;
 }
 void ShortcutDialog(){
  if(sleepRegistered)UnregisterHotKey(Handle,1);sleepRegistered=false;
  using(var d=new Form{Text="更改息屏快捷键",ClientSize=new Size(444,242),BackColor=Theme.Background,Font=Font,FormBorderStyle=FormBorderStyle.FixedDialog,MaximizeBox=false,MinimizeBox=false,StartPosition=FormStartPosition.CenterParent}){
   Hotkey next=new Hotkey(sleepKey.Key,sleepKey.Modifiers);d.Controls.Add(Theme.Label("选一个顺手的按键",16,Theme.Ink,24,20,390,34,true));d.Controls.Add(Theme.Label("点击下方输入框，再按下你想使用的快捷键。",9,Theme.Muted,24,62,390,25));
   var box=new TextBox{ReadOnly=true,Text=next.ToString(),Font=new Font("Microsoft YaHei UI",16),Location=new Point(24,101),Size=new Size(396,38),BackColor=Color.White,TextAlign=HorizontalAlignment.Center};d.Controls.Add(box);
   var error=Theme.Label("可单用 F1–F24，或使用 Ctrl / Alt 组合键。",9,Theme.Muted,24,154,396,24);d.Controls.Add(error);
   box.KeyDown+=(s,e)=>{e.Handled=true;e.SuppressKeyPress=true;if(e.KeyCode==Keys.Menu||e.KeyCode==Keys.ControlKey||e.KeyCode==Keys.ShiftKey)return;var h=new Hotkey((int)e.KeyCode,(e.Alt?1:0)|(e.Control?2:0)|(e.Shift?4:0));if(!h.Valid()){error.Text="请使用功能键，或加上 Ctrl / Alt。";return;}next=h;box.Text=next.ToString();error.Text="";};
   var save=Button("保存快捷键",252,190,168,36,true);save.Click+=(s,e)=>{if(!ApplyKey(next)){error.Text="这个快捷键被占用了，请换一个。";return;}try{File.WriteAllLines(hotkeyConfig,new[]{sleepKey.Encode()});status.Text="快捷键已更新，下次启动也会记住。";}catch{status.Text="当前已生效，但保存失败，请检查文件夹权限。";}d.Close();};d.Controls.Add(save);d.ShowDialog(this);
  }
  if(!sleepRegistered)sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)sleepKey.Modifiers,(uint)sleepKey.Key);
 }
 void UpdateStatus(string text){if(!IsDisposed&&IsHandleCreated)try{BeginInvoke((Action)(()=>status.Text=text));}catch(InvalidOperationException){}}
 void RefreshDevices(){
  if(list.DroppedDown)return;string selected=list.SelectedItem==null?pairedId:((Device)list.SelectedItem).Id;
  lock(gate){list.BeginUpdate();list.Items.Clear();foreach(var d in devices.Values)if((DateTime.UtcNow-d.Seen).TotalSeconds<15)list.Items.Add(d);for(int i=0;i<list.Items.Count;i++)if(((Device)list.Items[i]).Id==selected)list.SelectedIndex=i;if(list.SelectedIndex<0&&list.Items.Count>0)list.SelectedIndex=0;list.EndUpdate();
   Device phone;bool found=key!=null&&devices.TryGetValue(pairedId,out phone);phone=found?devices[pairedId]:null;bool online=phone!=null&&(DateTime.UtcNow-phone.Seen).TotalSeconds<15;
   pill.Text=online?"●  手机在线":"●  等待连接";pill.ForeColor=online?Color.FromArgb(36,151,113):Theme.Muted;sleep.Enabled=online;
   if(key!=null&&!pairingMode){pairedView.Visible=true;pairedName.Text=phone==null?"已配对的手机":phone.Name;pairedState.Text=online?"已连接 · 随时可以息屏":"等待手机连接同一 Wi-Fi";pairedState.ForeColor=online?Color.FromArgb(36,151,113):Theme.Muted;}
  }
 }
 void Discover(){try{using(var udp=new UdpClient(0)){udp.EnableBroadcast=true;udp.Client.ReceiveTimeout=300;DateTime next=DateTime.MinValue;while(active){
  if(DateTime.UtcNow>=next){next=DateTime.UtcNow.AddSeconds(2);var targets=new HashSet<string>{"255.255.255.255"};foreach(var nic in NetworkInterface.GetAllNetworkInterfaces()){if(nic.OperationalStatus!=OperationalStatus.Up)continue;foreach(var address in nic.GetIPProperties().UnicastAddresses){if(address.Address.AddressFamily!=AddressFamily.InterNetwork||address.IPv4Mask==null)continue;byte[] ip=address.Address.GetAddressBytes(),mask=address.IPv4Mask.GetAddressBytes();for(int i=0;i<4;i++)ip[i]=(byte)(ip[i]|~mask[i]);targets.Add(new IPAddress(ip).ToString());}}
   byte[] query=Encoding.UTF8.GetBytes("PHONE_SLEEP_DISCOVER_V1");foreach(string ip in targets)try{udp.Send(query,query.Length,new IPEndPoint(IPAddress.Parse(ip),47651));}catch(SocketException){}
  }
  try{var ep=new IPEndPoint(IPAddress.Any,0);byte[] reply=udp.Receive(ref ep);string[] p=Encoding.UTF8.GetString(reply).Split('|');Guid id;int port;if(p.Length!=4||p[0]!="PHONE_SLEEP_V1"||!Guid.TryParse(p[1],out id)||!int.TryParse(p[2],out port)||port!=47652)continue;string name=Encoding.UTF8.GetString(Convert.FromBase64String(p[3]));if(name.Length>80)continue;lock(gate){if(devices.Count>64)devices.Clear();devices[p[1]]=new Device{Id=p[1],Name=name,Address=ep.Address,Port=port,Seen=DateTime.UtcNow};}}catch(SocketException){}catch(FormatException){}
 }}}catch{UpdateStatus("自动发现暂不可用，请检查 Wi-Fi 和防火墙权限。");}}
 void Pair(){Device d=list.SelectedItem as Device;if(d==null){status.Text="还没发现手机，请在手机开启连接。";return;}string text=code.Text.Replace("-","").Replace(" ","").Trim().ToLowerInvariant();byte[] candidate;try{if(text.Length!=32)throw new FormatException();candidate=Protocol.Unhex(text);}catch{status.Text="请输入手机显示的完整连接码。";return;}if(Interlocked.Exchange(ref pairBusy,1)!=0)return;pair.Enabled=false;
  ThreadPool.QueueUserWorkItem(_=>{try{string result=Protocol.Send(d,candidate,"PING");if(result!="READY"&&result!="NO_PERMISSION")throw new IOException("手机返回："+result);File.WriteAllBytes(config,ProtectedData.Protect(Encoding.UTF8.GetBytes(d.Id+"\n"+text),null,DataProtectionScope.CurrentUser));pairedId=d.Id;key=candidate;
   BeginInvoke((Action)(()=>{code.Clear();pairingMode=false;list.Visible=false;codeLabel.Visible=false;code.Parent.Visible=false;pair.Visible=false;pairedView.Visible=true;}));UpdateStatus(result=="READY"?"配对成功，按快捷键就能息屏。":"配对成功，请在手机授予息屏权限。");
  }catch(Exception e){UpdateStatus(e.Message);}finally{Interlocked.Exchange(ref pairBusy,0);if(!IsDisposed)try{BeginInvoke((Action)(()=>pair.Enabled=true));}catch{}}});
 }
 void SleepPhone(){if(key==null){status.Text="先配对手机，就可以一键息屏。";ShowWindow();return;}Device d;lock(gate){if(!devices.TryGetValue(pairedId,out d)){UpdateStatus("等待手机上线，请检查手机连接和 Wi-Fi。");return;}}if(Interlocked.Exchange(ref sleepBusy,1)!=0)return;byte[] current=key;
  ThreadPool.QueueUserWorkItem(_=>{var watch=Stopwatch.StartNew();try{string result=Protocol.Send(d,current,"SLEEP");if(result=="OK")UpdateStatus("已息屏 · "+watch.ElapsedMilliseconds+" 毫秒");else if(result=="NO_PERMISSION")UpdateStatus("请在手机授予「一键息屏」的息屏权限。");else if(result=="CLOCK")UpdateStatus("请将电脑和手机时间设为自动同步。");else UpdateStatus("手机返回："+result);}catch(Exception e){UpdateStatus(e.Message);}finally{Interlocked.Exchange(ref sleepBusy,0);}});
 }
}
static class Program {
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern IntPtr FindWindow(string className,string title);
 [DllImport("user32.dll")]static extern bool PostMessage(IntPtr h,uint msg,IntPtr w,IntPtr l);
 [STAThread]static void Main(string[] args){bool created;using(var mutex=new Mutex(true,"Local\\PhoneSleepDesktopV1",out created)){if(!created){PostMessage(FindWindow(null,"一键息屏"),Array.IndexOf(args,"--sleep")>=0?0x8002u:0x8001u,IntPtr.Zero,IntPtr.Zero);return;}Application.EnableVisualStyles();Application.SetCompatibleTextRenderingDefault(false);Application.Run(new MainForm(Array.IndexOf(args,"--background")>=0));}}
}

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

/**
 * 电脑端界面。浅绿配色、白色卡片、柔和绿色主按钮、深灰文字。
 *
 * 缩放策略：进程声明为 DPI aware，字体按 point 指定会随系统 DPI 放大，
 * 所以布局必须按同一比例放大，否则文字一定溢出卡片。这里所有坐标和尺寸都经过 Theme.Px()，
 * 文字高度一律实测而不是写死，窗口放不下时外层滚动。
 */
static class Theme {
 public static float S=1f;
 public static int Dpi=96;

 public static readonly Color Background=Color.FromArgb(241,248,243);
 public static readonly Color Card=Color.White;
 public static readonly Color Ink=Color.FromArgb(35,48,41);
 public static readonly Color Muted=Color.FromArgb(92,122,105);
 public static readonly Color Green=Color.FromArgb(38,128,78);
 public static readonly Color GreenHover=Color.FromArgb(32,113,68);
 public static readonly Color GreenDeep=Color.FromArgb(31,110,66);
 public static readonly Color Pale=Color.FromArgb(230,244,234);
 public static readonly Color PaleHover=Color.FromArgb(216,238,225);
 public static readonly Color PalePressed=Color.FromArgb(203,230,214);
 public static readonly Color PaleDisabled=Color.FromArgb(238,244,240);
 public static readonly Color Line=Color.FromArgb(217,234,224);
 public static readonly Color Ok=Color.FromArgb(46,158,99);
 public static readonly Color Danger=Color.FromArgb(178,72,60);

 public static int Px(double value){return (int)Math.Round(value*S);}

 public static void DetectDpi(){
  int forced;
  if(int.TryParse(Environment.GetEnvironmentVariable("PHONESLEEP_DPI"),out forced)&&forced>=72&&forced<=480){
   Dpi=forced;S=Dpi/96f;return;
  }
  try{using(var g=Graphics.FromHwnd(IntPtr.Zero))Dpi=(int)Math.Round(g.DpiX);}catch{Dpi=96;}
  if(Dpi<72||Dpi>480)Dpi=96;
  S=Dpi/96f;
 }

 /**
  * 字号按 point 给出，但换算成像素后再建字体。
  * 这样排版和字号由同一个缩放系数驱动：150% 下 10pt 得到 20px，
  * 与系统在 144 DPI 下渲染 10pt 的结果一致，同时也让各档缩放可以离线校验。
  */
 public static Font F(double points,FontStyle style){
  float pixels=(float)(points*96.0/72.0*S);
  if(pixels<1)pixels=1;
  return new Font("Microsoft YaHei UI",pixels,style,GraphicsUnit.Pixel);
 }

 public static GraphicsPath Round(RectangleF r,float radius){
  float d=Math.Max(1,radius*2);
  if(d>r.Width)d=r.Width;
  if(d>r.Height)d=r.Height;
  var p=new GraphicsPath();
  if(d<=2){p.AddRectangle(r);return p;}
  p.AddArc(r.X,r.Y,d,d,180,90);
  p.AddArc(r.Right-d,r.Y,d,d,270,90);
  p.AddArc(r.Right-d,r.Bottom-d,d,d,0,90);
  p.AddArc(r.X,r.Bottom-d,d,d,90,90);
  p.CloseFigure();
  return p;
 }

 /** 实测换行后的文字高度，并留出余量，避免高 DPI 下最后一行被切掉。 */
 public static int TextHeight(Graphics g,string text,Font font,int width){
  if(string.IsNullOrEmpty(text))return font.Height;
  var flags=TextFormatFlags.WordBreak|TextFormatFlags.NoPadding;
  var size=TextRenderer.MeasureText(g,text,font,new Size(Math.Max(1,width),int.MaxValue),flags);
  return Math.Max(font.Height,size.Height+Px(3));
 }

 public static int TextWidth(Graphics g,string text,Font font){
  if(string.IsNullOrEmpty(text))return 0;
  return TextRenderer.MeasureText(g,text,font,new Size(int.MaxValue,int.MaxValue),TextFormatFlags.NoPadding).Width;
 }

 /** 找出控件所在的那层“纸面”颜色，自绘控件先用它铺底再画圆角。 */
 public static Color SurfaceOf(Control c){
  for(Control p=c.Parent;p!=null;p=p.Parent){
   if(p is Card)return Card;
   if(p is Surface)return ((Surface)p).Fill;
   if(p.BackColor.A==255)return p.BackColor;
  }
  return Background;
 }
}

/**
 * 外层滚动容器。
 * 默认行为是「只要某个控件拿到焦点就把它滚进视野」，于是屏幕放不下时窗口一打开就停在中间，
 * 标题被滚到视野外。这里固定在当前滚动位置，让窗口始终从顶部开始。
 */
class ScrollHost:Panel {
 protected override Point ScrollToControl(Control active){return AutoScrollPosition;}
}

/** 白色圆角卡片。 */
class Card:Panel {
 public Card(){
  DoubleBuffered=true;
  BackColor=Theme.Background;
 }
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(16)))
  using(var brush=new SolidBrush(Theme.Card))
  using(var pen=new Pen(Theme.Line,Math.Max(1,Theme.Px(1))))
  {e.Graphics.FillPath(brush,path);e.Graphics.DrawPath(pen,path);}
 }
}

/**
 * 普通背景容器，用来承载一组控件或在卡片内部划出一块区域。
 * Fill 决定底色：放在卡片里的区域必须填白色，否则会在白卡片上露出一块灰底。
 */
class Surface:Panel {
 public bool Card;
 public Color Fill;
 public Surface(bool card){
  DoubleBuffered=true;
  Card=card;
  Fill=card?Theme.Card:Theme.Background;
  BackColor=Fill;
 }
 /** 换底色。子控件的透明背景取的是父控件 BackColor，所以两者要一起改。 */
 public void SetFill(Color color){
  Fill=color;BackColor=color;Invalidate();
 }
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  if(Card){
   using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(16)))
   using(var brush=new SolidBrush(Theme.Card))
   using(var pen=new Pen(Theme.Line,Math.Max(1,Theme.Px(1))))
   {e.Graphics.FillPath(brush,path);e.Graphics.DrawPath(pen,path);}
  }else e.Graphics.Clear(Fill);
 }
}

/**
 * 按钮。完全自绘，不使用 FlatStyle，因此没有系统按钮的深色边框，也没有虚线焦点框。
 * 禁用态改用浅底深灰字，避免白字落在浅绿底上看不清。
 */
class SoftButton:Control {
 public bool Primary;
 bool hover,pressed;
 public SoftButton(){
  SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);
  Cursor=Cursors.Hand;
  Font=Theme.F(10,FontStyle.Bold);
  AccessibleRole=AccessibleRole.PushButton;TabStop=true;
 }
 protected override void OnKeyDown(KeyEventArgs e){if(Enabled&&(e.KeyCode==Keys.Space||e.KeyCode==Keys.Enter)){pressed=true;Invalidate();e.Handled=true;}base.OnKeyDown(e);}
 protected override void OnKeyUp(KeyEventArgs e){if(Enabled&&(e.KeyCode==Keys.Space||e.KeyCode==Keys.Enter)){pressed=false;Invalidate();OnClick(EventArgs.Empty);e.Handled=true;}base.OnKeyUp(e);}
 protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
 protected override void OnMouseLeave(EventArgs e){hover=false;pressed=false;Invalidate();base.OnMouseLeave(e);}
 protected override void OnMouseDown(MouseEventArgs e){if(e.Button==MouseButtons.Left){pressed=true;Invalidate();}base.OnMouseDown(e);}
 protected override void OnMouseUp(MouseEventArgs e){pressed=false;Invalidate();base.OnMouseUp(e);}
 protected override void OnEnabledChanged(EventArgs e){Invalidate();base.OnEnabledChanged(e);}
 protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Theme.SurfaceOf(this));}
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  Color fill,text;
  if(Primary&&Enabled){fill=pressed?Theme.GreenDeep:(hover?Theme.GreenHover:Theme.Green);text=Color.White;}
  else if(Primary){fill=Theme.PaleDisabled;text=Theme.Muted;}
  else if(Enabled){fill=pressed?Theme.PalePressed:(hover?Theme.PaleHover:Theme.Pale);text=Theme.GreenDeep;}
  else{fill=Theme.PaleDisabled;text=Theme.Muted;}
  using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(11)))
  using(var brush=new SolidBrush(fill))
   e.Graphics.FillPath(brush,path);
  var flags=TextFormatFlags.HorizontalCenter|TextFormatFlags.VerticalCenter|TextFormatFlags.WordBreak|TextFormatFlags.NoPadding;
  TextRenderer.DrawText(e.Graphics,Text,Font,ClientRectangle,text,flags);
 }
}

/** 标题栏按钮。图形用线条绘制，字体缺字时也不会出现方框。 */
class GlyphButton:Control {
 public bool Close;
 bool hover;
 public GlyphButton(bool close){
  Close=close;
  SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);
  Cursor=Cursors.Hand;
 }
 protected override void OnMouseEnter(EventArgs e){hover=true;Invalidate();base.OnMouseEnter(e);}
 protected override void OnMouseLeave(EventArgs e){hover=false;Invalidate();base.OnMouseLeave(e);}
 protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Theme.SurfaceOf(this));}
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  if(hover){
   using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(8)))
   using(var brush=new SolidBrush(Close?Color.FromArgb(240,233,231):Theme.Pale))
    e.Graphics.FillPath(brush,path);
  }
  Color color=Close&&hover?Theme.Danger:Theme.Muted;
  float pad=Width*0.32f;
  using(var pen=new Pen(color,Math.Max(1.4f,Theme.Px(1.4)))){
   if(Close){
    e.Graphics.DrawLine(pen,pad,pad,Width-pad,Height-pad);
    e.Graphics.DrawLine(pen,Width-pad,pad,pad,Height-pad);
   }else{
    float y=Height/2f;
    e.Graphics.DrawLine(pen,pad,y,Width-pad,y);
   }
  }
 }
}

/** 手机图标。 */
class PhoneGlyph:Control {
 public PhoneGlyph(){DoubleBuffered=true;}
 protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Theme.SurfaceOf(this));}
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  float w=Width,h=Height;
  using(var path=Theme.Round(new RectangleF(w*0.14f,h*0.03f,w*0.72f,h*0.90f),w*0.16f))
  using(var pen=new Pen(Theme.Green,Math.Max(1.5f,Theme.Px(1.5))))
   e.Graphics.DrawPath(pen,path);
  using(var pen=new Pen(Theme.Green,Math.Max(1.5f,Theme.Px(1.5)))){
   e.Graphics.DrawLine(pen,w*0.40f,h*0.13f,w*0.60f,h*0.13f);
   e.Graphics.DrawLine(pen,w*0.43f,h*0.83f,w*0.57f,h*0.83f);
  }
  using(var brush=new SolidBrush(Theme.Pale))
   e.Graphics.FillEllipse(brush,w*0.28f,h*0.32f,w*0.44f,h*0.30f);
  using(var pen=new Pen(Theme.Green,Math.Max(1.2f,Theme.Px(1.2)))){
   e.Graphics.DrawArc(pen,w*0.39f,h*0.40f,w*0.22f,h*0.15f,-45,270);
   e.Graphics.DrawLine(pen,w*0.50f,h*0.36f,w*0.50f,h*0.46f);
  }
 }
}

/**
 * 替换原生 ComboBox 的设备下拉。
 * 原生控件在高 DPI 下会露出系统绘制的高对比边框和箭头，和浅绿卡片不搭，所以自绘。
 */
class Picker:Control {
 public readonly List<Device> Items=new List<Device>();
 public int SelectedIndex=-1;
 ListBox dropdown;
 Control dropdownHost;
 bool open,hot;

 public Picker(){
  SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);
  Cursor=Cursors.Hand;
  Font=Theme.F(10,FontStyle.Regular);
 }
 public Device Selected{get{return SelectedIndex>=0&&SelectedIndex<Items.Count?Items[SelectedIndex]:null;}}

 public void SetItems(List<Device> source,string preferId){
  Items.Clear();Items.AddRange(source);
  int keep=0;
  if(preferId!=null)for(int i=0;i<Items.Count;i++)if(Items[i].Id==preferId)keep=i;
  SelectedIndex=Items.Count==0?-1:keep;
  if(dropdown!=null&&dropdown.Visible)SyncDropdown();
  Invalidate();
 }

 /** 下拉列表挂在最外层容器上，避免被卡片边界裁掉。 */
 public void AttachDropdown(Control host){
  dropdownHost=host;
  dropdown=new ListBox();
  dropdown.BorderStyle=BorderStyle.None;
  dropdown.DrawMode=DrawMode.OwnerDrawFixed;
  dropdown.Font=Theme.F(10,FontStyle.Regular);
  dropdown.BackColor=Theme.Card;
  dropdown.ForeColor=Theme.Ink;
  dropdown.ItemHeight=Theme.Px(30);
  dropdown.Visible=false;
  dropdown.DrawItem+=(s,e)=>{
   if(e.Index<0||e.Index>=Items.Count)return;
   bool selected=(e.State&DrawItemState.Selected)!=0;
   using(var b=new SolidBrush(selected?Theme.Pale:Theme.Card))e.Graphics.FillRectangle(b,e.Bounds);
   var d=Items[e.Index];
   var nameRect=new Rectangle(e.Bounds.X+Theme.Px(12),e.Bounds.Y,Math.Max(1,e.Bounds.Width-Theme.Px(24)),e.Bounds.Height);
   string address=d.Address+"";
   int addressWidth=Theme.TextWidth(e.Graphics,address,Font);
   bool room=nameRect.Width-addressWidth-Theme.Px(12)>Theme.Px(60);
   if(room)TextRenderer.DrawText(e.Graphics,address,Font,
     new Rectangle(e.Bounds.Right-addressWidth-Theme.Px(12),e.Bounds.Y,addressWidth,e.Bounds.Height),Theme.Muted,
     TextFormatFlags.Right|TextFormatFlags.VerticalCenter|TextFormatFlags.NoPadding);
   TextRenderer.DrawText(e.Graphics,d.Name,Font,
     new Rectangle(nameRect.X,nameRect.Y,room?nameRect.Width-addressWidth-Theme.Px(12):nameRect.Width,nameRect.Height),Theme.Ink,
     TextFormatFlags.Left|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPadding);
  };
  dropdown.Click+=(s,e)=>{
   Device d=dropdown.SelectedItem as Device;
   if(d!=null)SelectedIndex=Items.IndexOf(d);
   CloseDropdown();
   Invalidate();
  };
  host.Controls.Add(dropdown);
  dropdown.BringToFront();
 }

 void SyncDropdown(){
  dropdown.BeginUpdate();
  dropdown.Items.Clear();
  foreach(var d in Items)dropdown.Items.Add(d);
  dropdown.EndUpdate();
 }

 public void CloseDropdown(){open=false;if(dropdown!=null)dropdown.Visible=false;Invalidate();}

 protected override void OnMouseEnter(EventArgs e){hot=true;Invalidate();base.OnMouseEnter(e);}
 protected override void OnMouseLeave(EventArgs e){hot=false;Invalidate();base.OnMouseLeave(e);}
 protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Theme.SurfaceOf(this));}

 protected override void OnMouseDown(MouseEventArgs e){
  if(e.Button!=MouseButtons.Left)return;
  if(open){CloseDropdown();return;}
  if(dropdownHost==null||Items.Count==0){Invalidate();return;}
  SyncDropdown();
  if(dropdown.Items.Count>0)dropdown.SelectedIndex=SelectedIndex;
  int rows=Math.Min(Items.Count,5);
  int height=rows*dropdown.ItemHeight+Theme.Px(8);
  Point origin=dropdownHost.PointToClient(PointToScreen(new Point(0,Height+Theme.Px(4))));
  int y=origin.Y;
  if(y+height>dropdownHost.ClientSize.Height)y=Math.Max(0,origin.Y-Height-Theme.Px(4)-height);
  dropdown.SetBounds(origin.X,y,Width,height);
  dropdown.Visible=true;
  dropdown.BringToFront();
  open=true;
  Invalidate();
 }

 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(10)))
  using(var brush=new SolidBrush(hot||open?Theme.Pale:Theme.Background))
  using(var pen=new Pen(open?Theme.Green:Theme.Line,Math.Max(1,Theme.Px(1))))
  {e.Graphics.FillPath(brush,path);e.Graphics.DrawPath(pen,path);}

  int arrow=Theme.Px(18);
  Device d=Selected;
  var nameRect=new Rectangle(Theme.Px(14),0,Math.Max(Theme.Px(20),Width-arrow-Theme.Px(36)),Height);
  if(d==null){
   TextRenderer.DrawText(e.Graphics,Items.Count==0?"正在寻找手机…":"请选择发现的手机",Font,nameRect,Theme.Muted,
    TextFormatFlags.Left|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPadding);
  }else{
   string address=d.Address+"";
   int addressWidth=Theme.TextWidth(e.Graphics,address,Font);
   bool room=nameRect.Width-addressWidth-Theme.Px(12)>Theme.Px(60);
   if(room)TextRenderer.DrawText(e.Graphics,address,Font,
     new Rectangle(nameRect.Right-addressWidth,nameRect.Y,addressWidth,nameRect.Height),Theme.Muted,
     TextFormatFlags.Right|TextFormatFlags.VerticalCenter|TextFormatFlags.NoPadding);
   TextRenderer.DrawText(e.Graphics,d.Name,Font,
     new Rectangle(nameRect.X,nameRect.Y,room?nameRect.Width-addressWidth-Theme.Px(12):nameRect.Width,nameRect.Height),Theme.Ink,
     TextFormatFlags.Left|TextFormatFlags.VerticalCenter|TextFormatFlags.EndEllipsis|TextFormatFlags.NoPadding);
  }

  int cx=Width-Theme.Px(12)-arrow/2,cy=Height/2;
  using(var pen=new Pen(Theme.Muted,Math.Max(1.5f,Theme.Px(1.5)))){
   if(open){
    e.Graphics.DrawLine(pen,cx-arrow/2,cy+Theme.Px(2),cx,cy-Theme.Px(2));
    e.Graphics.DrawLine(pen,cx,cy-Theme.Px(2),cx+arrow/2,cy+Theme.Px(2));
   }else{
    e.Graphics.DrawLine(pen,cx-arrow/2,cy-Theme.Px(2),cx,cy+Theme.Px(2));
    e.Graphics.DrawLine(pen,cx,cy+Theme.Px(2),cx+arrow/2,cy-Theme.Px(2));
   }
  }
 }

 protected override void OnLostFocus(EventArgs e){CloseDropdown();base.OnLostFocus(e);}
}

/** 自绘输入框外框：白底、圆角、无系统边框，聚焦时描边变绿。 */
class InputFrame:Control {
 public readonly TextBox Box=new TextBox();
 public InputFrame(){
  SetStyle(ControlStyles.UserPaint|ControlStyles.AllPaintingInWmPaint|ControlStyles.OptimizedDoubleBuffer|ControlStyles.ResizeRedraw,true);
  Box.BorderStyle=BorderStyle.None;
  Box.BackColor=Theme.Card;
  Box.ForeColor=Theme.Ink;
  Box.Font=Theme.F(10.5,FontStyle.Regular);
  Box.TextAlign=HorizontalAlignment.Center;
  Controls.Add(Box);
 }
 protected override void OnResize(EventArgs e){base.OnResize(e);LayoutBox();}
 public void LayoutBox(){
  int padX=Theme.Px(14);
  Box.SetBounds(padX,Math.Max(0,(Height-Box.PreferredHeight)/2),Math.Max(Theme.Px(20),Width-padX*2),Box.PreferredHeight);
 }
 protected override void OnPaintBackground(PaintEventArgs e){e.Graphics.Clear(Theme.SurfaceOf(this));}
 protected override void OnPaint(PaintEventArgs e){
  e.Graphics.SmoothingMode=SmoothingMode.AntiAlias;
  using(var path=Theme.Round(new RectangleF(0,0,Width-1,Height-1),Theme.Px(10)))
  using(var brush=new SolidBrush(Theme.Card))
  using(var pen=new Pen(Box.Focused?Theme.Green:Theme.Line,Math.Max(1,Theme.Px(1))))
  {e.Graphics.FillPath(brush,path);e.Graphics.DrawPath(pen,path);}
 }
}

class MainForm:Form {
 [DllImport("user32.dll")]static extern bool RegisterHotKey(IntPtr h,int id,uint mod,uint key);
 [DllImport("user32.dll")]static extern bool UnregisterHotKey(IntPtr h,int id);
 [DllImport("user32.dll")]static extern bool ReleaseCapture();
 [DllImport("user32.dll")]static extern IntPtr SendMessage(IntPtr h,int msg,IntPtr w,IntPtr l);
 [DllImport("dwmapi.dll")]static extern int DwmSetWindowAttribute(IntPtr h,int attr,ref int value,int length);

 readonly object gate=new object();readonly Dictionary<string,Device> devices=new Dictionary<string,Device>();
 readonly Panel scroll=new ScrollHost();readonly Surface content=new Surface(false);
 Label title,subtitle;GlyphButton minimize,close;
 readonly Card deviceCard=new Card(),shortcutCard=new Card();
 readonly Surface pairedView=new Surface(false);
 Label status,connection,pill,keyBadge,pairedName,pairedState,codeLabel,statusLine,pairHeading,shortcutHeading,shortcutHint;
 readonly Picker picker=new Picker();readonly InputFrame codeFrame=new InputFrame();TextBox code;
 LinkLabel change;SoftButton pair,sleep,trayButton,shortcutButton;
 NotifyIcon tray;System.Windows.Forms.Timer timer=new System.Windows.Forms.Timer();
 readonly PhoneGlyph glyph=new PhoneGlyph();
 // 字体只建一次：DPI 在运行期不变，反复 new Font 会持续消耗 GDI 句柄。
 Font sleepFont,badgeFontLarge,badgeFontSmall;
 volatile bool active=true;int pairBusy,sleepBusy;string pairedId="",savedEndpoint="",startupStatus="";byte[] key;
 bool sleepRegistered,pairingMode,laying,lastOnline;
 Hotkey sleepKey=new Hotkey(0x77,0);
 readonly string config=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"paired-device.dat");
 readonly string hotkeyConfig=Path.Combine(AppDomain.CurrentDomain.BaseDirectory,"hotkeys.txt");

 public MainForm(bool hidden){
  Theme.DetectDpi();
  AutoScaleMode=AutoScaleMode.None;
  Text="一键息屏";
  BackColor=Theme.Background;ForeColor=Theme.Ink;
  FormBorderStyle=FormBorderStyle.None;
  StartPosition=FormStartPosition.CenterScreen;
  MaximizeBox=false;MinimizeBox=false;DoubleBuffered=true;
  try{Icon=Icon.ExtractAssociatedIcon(Application.ExecutablePath);}catch{Icon=SystemIcons.Application;}

  scroll.Dock=DockStyle.Fill;scroll.AutoScroll=true;scroll.BackColor=Theme.Background;
  content.BackColor=Theme.Background;
  scroll.Controls.Add(content);
  Controls.Add(scroll);

  Font=Theme.F(10,FontStyle.Regular);
  sleepFont=Theme.F(16,FontStyle.Bold);
  badgeFontLarge=Theme.F(22,FontStyle.Bold);
  badgeFontSmall=Theme.F(11,FontStyle.Bold);
  MouseEventHandler drag=(s,e)=>{if(e.Button==MouseButtons.Left){ReleaseCapture();SendMessage(Handle,0xA1,(IntPtr)2,IntPtr.Zero);}};
  MouseDown+=drag;scroll.MouseDown+=drag;content.MouseDown+=drag;

  title=new Label{Text="一键息屏",Font=Theme.F(18,FontStyle.Bold),ForeColor=Theme.Ink,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleLeft};
  subtitle=new Label{Text="轻按一下，让手机休息。",Font=Theme.F(10,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleLeft};
  minimize=new GlyphButton(false);
  close=new GlyphButton(true);
  minimize.Click+=(s,e)=>Hide();
  close.Click+=(s,e)=>Close();
  pill=new Label{Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleRight};

  pairHeading=new Label{Text="手机连接",Font=Theme.F(12.5,FontStyle.Bold),ForeColor=Theme.Ink,BackColor=Color.Transparent,AutoSize=false};
  connection=new Label{Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
  codeLabel=new Label{Text="连接码 · 只需输入一次",Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
  code=codeFrame.Box;
  code.UseSystemPasswordChar=true;

  pairedName=new Label{Text="我的手机",Font=Theme.F(14,FontStyle.Bold),ForeColor=Theme.Ink,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleLeft};
  pairedState=new Label{Text="正在自动连接…",Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
  statusLine=new Label{Text="已记住此设备，无需重复配对",Font=Theme.F(9,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleLeft};
  change=new LinkLabel{Text="更换手机",Font=Theme.F(9,FontStyle.Regular),LinkColor=Theme.Green,ActiveLinkColor=Theme.GreenDeep,BackColor=Color.Transparent,AutoSize=false,TextAlign=ContentAlignment.MiddleRight};
  change.Click+=(s,e)=>{pairingMode=true;ApplyMode();Relayout();};

  deviceCard.Controls.Add(pairHeading);deviceCard.Controls.Add(connection);deviceCard.Controls.Add(picker);
  deviceCard.Controls.Add(codeLabel);deviceCard.Controls.Add(codeFrame);deviceCard.Controls.Add(pairedView);
  pairedView.Controls.Add(glyph);pairedView.Controls.Add(pairedName);pairedView.Controls.Add(pairedState);
  pairedView.Controls.Add(statusLine);pairedView.Controls.Add(change);
  // 这块区域铺在白色卡片上，底色必须跟着卡片走。
  pairedView.SetFill(Theme.Card);
  pair=new SoftButton{Text="配对这台手机",Primary=true};
  pair.Click+=(s,e)=>Pair();
  deviceCard.Controls.Add(pair);

  shortcutHeading=new Label{Text="息屏快捷键",Font=Theme.F(12.5,FontStyle.Bold),ForeColor=Theme.Ink,BackColor=Color.Transparent,AutoSize=false};
  shortcutHint=new Label{Text="任何窗口，按一下即可",Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
  keyBadge=new Label{Font=Theme.F(22,FontStyle.Bold),ForeColor=Theme.GreenDeep,BackColor=Theme.Pale,AutoSize=false,TextAlign=ContentAlignment.MiddleCenter};
  shortcutButton=new SoftButton{Text="更改快捷键",Primary=false};
  shortcutButton.Click+=(s,e)=>ShortcutDialog();
  shortcutCard.Controls.Add(shortcutHeading);shortcutCard.Controls.Add(shortcutHint);
  shortcutCard.Controls.Add(keyBadge);shortcutCard.Controls.Add(shortcutButton);

  sleep=new SoftButton{Text="立即息屏",Primary=true};
  sleep.Click+=(s,e)=>SleepPhone();
  trayButton=new SoftButton{Text="收起到托盘",Primary=false};
  trayButton.Click+=(s,e)=>Hide();

  status=new Label{Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};

  content.Controls.Add(title);content.Controls.Add(subtitle);content.Controls.Add(pill);
  content.Controls.Add(minimize);content.Controls.Add(close);
  content.Controls.Add(deviceCard);content.Controls.Add(shortcutCard);
  content.Controls.Add(sleep);content.Controls.Add(status);content.Controls.Add(trayButton);

  var menu=new ContextMenuStrip();
  menu.Items.Add("立即息屏",null,(s,e)=>SleepPhone());
  menu.Items.Add("打开设置",null,(s,e)=>ShowWindow());
  menu.Items.Add("退出",null,(s,e)=>Close());
  tray=new NotifyIcon{Icon=Icon,Text="一键息屏",Visible=true,ContextMenuStrip=menu};
  tray.DoubleClick+=(s,e)=>ShowWindow();

  try{
   if(File.Exists(config)){
    var record=PairingRecord.Decode(File.ReadAllBytes(config));
    pairedId=record.Id;key=record.Key;
    if(record.Endpoint!=null){devices[pairedId]=record.Endpoint;savedEndpoint=record.Endpoint.Address.ToString();}
    status.Text="配对已保存，正在检查手机连接。";
   }
  }catch{status.Text="请重新输入手机连接码。";}
  startupStatus=status.Text;
  pairingMode=key==null;

  try{
   if(File.Exists(hotkeyConfig)){
    string[] p=File.ReadAllLines(hotkeyConfig);
    sleepKey=Hotkey.Parse(p.Length>1?p[1]:p[0]);
    if(p.Length>1)File.WriteAllLines(hotkeyConfig,new[]{sleepKey.Encode()});
   }
  }catch{sleepKey=new Hotkey(0x77,0);}
  sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)sleepKey.Modifiers,(uint)sleepKey.Key);
  UpdateKeyLabel();
  if(!sleepRegistered)status.Text="快捷键被占用，点击「更改快捷键」换一个。";

  picker.AttachDropdown(content);
  picker.SetItems(new List<Device>(),null);

  Relayout();
  ApplyMode();

  timer.Interval=900;
  timer.Tick+=(s,e)=>RefreshDevices();
  timer.Start();
  new Thread(Discover){IsBackground=true,Name="Phone discovery"}.Start();
  new Thread(Heartbeat){IsBackground=true,Name="Phone heartbeat"}.Start();

  FormClosing+=(s,e)=>{active=false;timer.Stop();if(sleepRegistered)UnregisterHotKey(Handle,1);tray.Visible=false;tray.Dispose();};
  if(hidden&&key!=null)Shown+=(s,e)=>Hide();
  Shown+=(s,e)=>{try{int rounded=2;DwmSetWindowAttribute(Handle,33,ref rounded,4);}catch{}};
 }

 /**
  * 全部位置按测量结果计算：文字高度实测、留白按 DPI 放大、窗口放不下时外层滚动。
  * 100/125/150/200% 下都不会出现文字挤出卡片或被切掉。
  */
 void Relayout(){
  if(laying||IsDisposed)return;
  laying=true;
  try{
   int pad=Theme.Px(26),headerH=Theme.Px(34),gapSmall=Theme.Px(6),gapHeader=Theme.Px(22);
   int cardPad=Theme.Px(18),gapCards=Theme.Px(18),gapMain=Theme.Px(16);
   int buttonH=Theme.Px(44),sleepH=Theme.Px(58),trayH=Theme.Px(38),pickerH=Theme.Px(42),rowH=Theme.Px(88);

   int design=Theme.Px(700);
   Rectangle working=Screen.PrimaryScreen==null?new Rectangle(0,0,Theme.Px(1280),Theme.Px(720)):Screen.PrimaryScreen.WorkingArea;
   int width=Math.Max(Theme.Px(430),Math.Min(design,working.Width-Theme.Px(40)));
   int usable=width-pad*2;

   using(var g=CreateGraphics()){
    int y=Theme.Px(22);
    title.SetBounds(pad,y,usable-Theme.Px(150),headerH);
    int subH=Theme.TextHeight(g,subtitle.Text,subtitle.Font,usable);
    subtitle.SetBounds(pad,y+headerH+gapSmall,usable,subH);
    y+=headerH+gapSmall+subH+gapHeader;

    int glyphSize=Theme.Px(30);
    close.SetBounds(pad+usable-glyphSize,Theme.Px(24),glyphSize,glyphSize);
    minimize.SetBounds(pad+usable-glyphSize*2-Theme.Px(8),Theme.Px(24),glyphSize,glyphSize);
    // 状态徽标排在两个标题栏按钮左边：按钮会用自身底色铺底，压在上面会把文字擦掉。
    int pillRight=pad+usable-glyphSize*2-Theme.Px(8)-Theme.Px(10);
    pill.SetBounds(pillRight-Theme.Px(180),Theme.Px(28),Theme.Px(180),Theme.Px(24));

    bool paired=key!=null&&!pairingMode;

    // 两张卡片并排
    int cardsTop=y;
    int columnGap=Theme.Px(16);
    int leftCardW=(usable-columnGap)*62/100;
    int rightCardW=usable-columnGap-leftCardW;
    int leftInner=leftCardW-cardPad*2;
    int rightInner=rightCardW-cardPad*2;

    int leftHintH=Math.Max(Theme.Px(18),Theme.TextHeight(g,connection.Text,connection.Font,leftInner));
    int rightHintH=Math.Max(Theme.Px(18),Theme.TextHeight(g,shortcutHint.Text,shortcutHint.Font,rightInner));
    int headerBlock=cardPad+Theme.Px(24);
    int codeLabelH=Math.Max(Theme.Px(20),Theme.TextHeight(g,codeLabel.Text,codeLabel.Font,leftInner));
    int codeFrameH=Theme.Px(40);
    // 未配对时左卡片要依次放下拉、连接码标签、输入框和按钮，高度必须按实测累加；
    // 沿用「已配对」那个固定行高会让按钮压到输入框上。
    int pairBlock=pickerH+Theme.Px(16)+codeLabelH+Theme.Px(8)+codeFrameH;
    int rightCardH=headerBlock+rightHintH+Theme.Px(18)+Theme.Px(64)+Theme.Px(18)+buttonH+cardPad;
    int leftBlockH=paired?rowH:pairBlock;
    int leftCardH=headerBlock+leftHintH+Theme.Px(16)+leftBlockH+(paired?0:Theme.Px(16)+buttonH)+cardPad;
    int cardH=Math.Max(leftCardH,rightCardH);

    // 左卡片内部：行区域三种形态（下拉、连接码、已配对）叠在同一块地方
    pairHeading.SetBounds(cardPad,cardPad,leftInner,Theme.Px(24));
    connection.SetBounds(cardPad,headerBlock,leftInner,leftHintH);
    int rowTop=headerBlock+leftHintH+Theme.Px(16);
    picker.SetBounds(cardPad,rowTop,leftInner,pickerH);
    codeLabel.SetBounds(cardPad,rowTop+pickerH+Theme.Px(16),leftInner,codeLabelH);
    codeFrame.SetBounds(cardPad,rowTop+pickerH+Theme.Px(16)+codeLabelH+Theme.Px(8),leftInner,codeFrameH);
    codeFrame.LayoutBox();
    // 已配对时把剩余高度全给这块，内容垂直居中，避免卡片底部留一段空白。
    int pairedH=Math.Max(rowH,cardH-rowTop-cardPad);
    pairedView.SetBounds(cardPad,rowTop,leftInner,pairedH);
    int pairedBlock=Theme.Px(110);
    int offset=Math.Max(0,(pairedH-pairedBlock)/2);
    glyph.SetBounds(Theme.Px(2),offset+Theme.Px(2),Theme.Px(54),Theme.Px(72));
    pairedName.SetBounds(Theme.Px(72),offset,leftInner-Theme.Px(80),Theme.Px(30));
    pairedState.SetBounds(Theme.Px(72),offset+Theme.Px(32),leftInner-Theme.Px(80),Theme.Px(22));
    statusLine.SetBounds(Theme.Px(2),offset+Theme.Px(84),leftInner-Theme.Px(112),Theme.Px(20));
    change.SetBounds(leftInner-Theme.Px(106),offset+Theme.Px(84),Theme.Px(104),Theme.Px(20));
    pair.SetBounds(cardPad,cardH-cardPad-buttonH,leftInner,buttonH);
    deviceCard.SetBounds(pad,cardsTop,leftCardW,cardH);

    // 右卡片：快捷键徽标
    shortcutHeading.SetBounds(cardPad,cardPad,rightInner,Theme.Px(24));
    shortcutHint.SetBounds(cardPad,headerBlock,rightInner,rightHintH);
    int badgeTop=headerBlock+rightHintH+Theme.Px(18);
    keyBadge.SetBounds(cardPad,badgeTop,rightInner,Theme.Px(64));
    UpdateKeyLabel();
    shortcutButton.SetBounds(cardPad,badgeTop+Theme.Px(64)+Theme.Px(18),rightInner,buttonH);
    shortcutCard.SetBounds(pad+leftCardW+columnGap,cardsTop,rightCardW,cardH);

    y=cardsTop+cardH+gapCards;

    sleep.Font=sleepFont;
    sleep.SetBounds(pad,y,usable,sleepH);
    y+=sleepH+gapMain;

    trayButton.SetBounds(pad+usable-Theme.Px(132),y,Theme.Px(132),trayH);
    int statusWidth=usable-Theme.Px(146);
    int statusH=Math.Max(trayH,Theme.TextHeight(g,status.Text,status.Font,statusWidth));
    status.SetBounds(pad,y,statusWidth,statusH);
    y+=Math.Max(statusH,trayH)+Theme.Px(24);

    int contentHeight=y;
    bool needScroll=contentHeight>working.Height-Theme.Px(40);
    int scrollbar=needScroll?SystemInformation.VerticalScrollBarWidth:0;
    content.SetBounds(0,0,width,contentHeight);

    int clientHeight=Math.Min(contentHeight,working.Height-Theme.Px(40));
    if(ClientSize.Width!=width+scrollbar||ClientSize.Height!=clientHeight)
     ClientSize=new Size(width+scrollbar,clientHeight);
   }
  }finally{laying=false;}
  ApplyMode();
 }

 /** 已配对与未配对两种形态：切换可见控件。两者占同一块区域，所以不会互相挤压。 */
 void ApplyMode(){
  bool paired=key!=null&&!pairingMode;
  pairedView.Visible=paired;
  picker.Visible=!paired;
  codeLabel.Visible=!paired;
  codeFrame.Visible=!paired;
  pair.Visible=!paired;
 }

 void ShowWindow(){Show();WindowState=FormWindowState.Normal;Activate();}
 protected override void WndProc(ref Message m){
  if(m.Msg==0x0312&&m.WParam.ToInt32()==1)SleepPhone();
  if(m.Msg==0x8001)ShowWindow();
  if(m.Msg==0x8002)SleepPhone();
  base.WndProc(ref m);
 }
 protected override void OnResize(EventArgs e){base.OnResize(e);Relayout();}

 void UpdateKeyLabel(){
  keyBadge.Text=sleepKey.ToString();
  keyBadge.Font=keyBadge.Text.Length>9?badgeFontSmall:badgeFontLarge;
  tray.Text="一键息屏 · "+sleepKey;
 }

 bool ApplyKey(Hotkey next){
  if(!next.Valid())return false;
  if(sleepRegistered)UnregisterHotKey(Handle,1);
  sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)next.Modifiers,(uint)next.Key);
  if(!sleepRegistered)return false;
  sleepKey=next;UpdateKeyLabel();return true;
 }

 void ShortcutDialog(){
  if(sleepRegistered)UnregisterHotKey(Handle,1);
  sleepRegistered=false;
  using(var d=new Form{Text="更改息屏快捷键",Font=Theme.F(10,FontStyle.Regular),BackColor=Theme.Background,ForeColor=Theme.Ink,
        FormBorderStyle=FormBorderStyle.FixedDialog,MaximizeBox=false,MinimizeBox=false,
        StartPosition=FormStartPosition.CenterParent,AutoScaleMode=AutoScaleMode.None}){
   int w=Theme.Px(420),pad=Theme.Px(24);
   d.ClientSize=new Size(w,Theme.Px(252));
   var caption=new Label{Text="选一个顺手的按键",Font=Theme.F(14,FontStyle.Bold),ForeColor=Theme.Ink,BackColor=Color.Transparent,AutoSize=false};
   var hint=new Label{Text="点击下方输入框，再按下你想使用的快捷键。",Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
   var frame=new InputFrame();
   var box=frame.Box;
   box.Font=Theme.F(15,FontStyle.Regular);
   box.ReadOnly=true;
   box.Text=sleepKey.ToString();
   var error=new Label{Text="可单用 F1–F24，或使用 Ctrl / Alt 组合键。",Font=Theme.F(9.5,FontStyle.Regular),ForeColor=Theme.Muted,BackColor=Color.Transparent,AutoSize=false};
   Hotkey next=new Hotkey(sleepKey.Key,sleepKey.Modifiers);
   box.KeyDown+=(s,e)=>{
    e.Handled=true;e.SuppressKeyPress=true;
    if(e.KeyCode==Keys.Menu||e.KeyCode==Keys.ControlKey||e.KeyCode==Keys.ShiftKey)return;
    var h=new Hotkey((int)e.KeyCode,(e.Alt?1:0)|(e.Control?2:0)|(e.Shift?4:0));
    if(!h.Valid()){error.Text="请使用功能键，或加上 Ctrl / Alt。";error.ForeColor=Theme.Danger;return;}
    next=h;box.Text=next.ToString();error.Text="";error.ForeColor=Theme.Muted;
   };
   var save=new SoftButton{Text="保存快捷键",Primary=true};
   var cancel=new SoftButton{Text="取消",Primary=false};
   cancel.Click+=(s,e)=>d.Close();
   save.Click+=(s,e)=>{
    if(!ApplyKey(next)){error.Text="这个快捷键被占用了，请换一个。";error.ForeColor=Theme.Danger;return;}
    try{File.WriteAllLines(hotkeyConfig,new[]{sleepKey.Encode()});status.Text="快捷键已更新，下次启动也会记住。";}
    catch{status.Text="当前已生效，但保存失败，请检查文件夹权限。";}
    d.Close();
   };
   d.Controls.Add(caption);d.Controls.Add(hint);d.Controls.Add(frame);d.Controls.Add(error);d.Controls.Add(save);d.Controls.Add(cancel);
   using(var g=d.CreateGraphics()){
    caption.SetBounds(pad,Theme.Px(20),w-pad*2,Theme.Px(28));
    hint.SetBounds(pad,Theme.Px(54),w-pad*2,Theme.TextHeight(g,hint.Text,hint.Font,w-pad*2));
    frame.SetBounds(pad,Theme.Px(92),w-pad*2,Theme.Px(44));
    error.SetBounds(pad,Theme.Px(146),w-pad*2,Theme.TextHeight(g,error.Text,error.Font,w-pad*2));
    int bw=(w-pad*2-Theme.Px(12))/2;
    cancel.SetBounds(pad,Theme.Px(188),bw,Theme.Px(42));
    save.SetBounds(pad+bw+Theme.Px(12),Theme.Px(188),bw,Theme.Px(42));
   }
   d.Shown+=(s,e)=>box.Focus();
   d.ShowDialog(this);
   Relayout();
  }
  if(!sleepRegistered)sleepRegistered=RegisterHotKey(Handle,1,0x4000u|(uint)sleepKey.Modifiers,(uint)sleepKey.Key);
 }

 void UpdateStatus(string text){
  if(!IsDisposed&&IsHandleCreated)try{BeginInvoke((Action)(()=>{status.Text=text;Relayout();}));}catch(InvalidOperationException){}
 }

 void RefreshDevices(){
  string selected=picker.Selected==null?pairedId:picker.Selected.Id;
  var visible=new List<Device>();
  bool online;
  lock(gate){
   foreach(var d in devices.Values)if((DateTime.UtcNow-d.Seen).TotalSeconds<15)visible.Add(d);
   Device phone;
   devices.TryGetValue(pairedId,out phone);
   online=phone!=null&&(DateTime.UtcNow-phone.Verified).TotalSeconds<16;
   pill.Text=online?"●  手机在线":(key==null?"●  等待连接":"●  正在重连");
   pill.ForeColor=online?Theme.Ok:Theme.Muted;
   sleep.Enabled=key!=null;
   if(key!=null&&!pairingMode){
    pairedName.Text=phone==null?"已配对的手机":phone.Name;
    pairedState.Text=online?"连接正常 · 随时可以息屏":"正在重连，请允许手机后台运行";
    pairedState.ForeColor=online?Theme.Ok:Theme.Muted;
   }
  }
  // 启动时的「正在检查手机连接」在连上以后必须换掉，否则和卡片里的「连接正常」自相矛盾。
  // 只在状态还停在启动文案时才改，免得覆盖掉息屏结果之类的提示。
  if(key!=null&&online&&!lastOnline&&status.Text==startupStatus){
   status.Text="已连接，按快捷键或点「立即息屏」就能让手机休息。";
   Relayout();
  }
  lastOnline=online;
  if(picker.Visible)picker.SetItems(visible,selected);
 }

 void Discover(){
  while(active){
   try{
    using(var udp=new UdpClient(0)){
     udp.EnableBroadcast=true;udp.Client.ReceiveTimeout=300;
     DateTime next=DateTime.MinValue;
     while(active){
      if(DateTime.UtcNow>=next){
       next=DateTime.UtcNow.AddSeconds(2);
       var targets=new HashSet<string>{"255.255.255.255"};
       foreach(var nic in NetworkInterface.GetAllNetworkInterfaces()){
        try{
         if(nic.OperationalStatus!=OperationalStatus.Up)continue;
         foreach(var address in nic.GetIPProperties().UnicastAddresses){
          if(address.Address.AddressFamily!=AddressFamily.InterNetwork||address.IPv4Mask==null)continue;
          byte[] ip=address.Address.GetAddressBytes(),mask=address.IPv4Mask.GetAddressBytes();
          for(int i=0;i<4;i++)ip[i]=(byte)(ip[i]|~mask[i]);
          targets.Add(new IPAddress(ip).ToString());
         }
        }catch(NetworkInformationException){}
       }
       byte[] query=Encoding.UTF8.GetBytes("PHONE_SLEEP_DISCOVER_V1");
       foreach(string ip in targets)try{udp.Send(query,query.Length,new IPEndPoint(IPAddress.Parse(ip),47651));}catch(SocketException){}
      }
      try{
       var ep=new IPEndPoint(IPAddress.Any,0);
       byte[] reply=udp.Receive(ref ep);
       string[] p=Encoding.UTF8.GetString(reply).Split('|');
       Guid id;int port;
       if(p.Length!=4||p[0]!="PHONE_SLEEP_V1"||!Guid.TryParse(p[1],out id)||!int.TryParse(p[2],out port)||port!=47652)continue;
       string name=Encoding.UTF8.GetString(Convert.FromBase64String(p[3]));
       if(name.Length>80)continue;
       lock(gate){
        Device previous;devices.TryGetValue(p[1],out previous);
        if(devices.Count>64)devices.Clear();
        devices[p[1]]=new Device{Id=p[1],Name=name,Address=ep.Address,Port=port,Seen=DateTime.UtcNow,
          Verified=previous!=null&&previous.Address.Equals(ep.Address)?previous.Verified:DateTime.MinValue};
       }
      }catch(SocketException){}catch(FormatException){}
     }
    }
   }catch{UpdateStatus("网络正在恢复，自动重试中…");if(active)Thread.Sleep(1000);}
  }
 }

 void SaveEndpoint(Device d,byte[] current){
  lock(gate){
   if(d.Id!=pairedId||current!=key)return;
   if(savedEndpoint==d.Address.ToString())return;
   File.WriteAllBytes(config,new PairingRecord{Id=pairedId,Key=current,Endpoint=d}.Encode());
   savedEndpoint=d.Address.ToString();
  }
 }

 void MarkVerified(Device d,byte[] current){
  lock(gate){
   Device latest;
   if(d.Id==pairedId&&current==key&&devices.TryGetValue(d.Id,out latest)&&latest.Address.Equals(d.Address))latest.Verified=DateTime.UtcNow;
  }
  try{SaveEndpoint(d,current);}catch{}
 }

 void Heartbeat(){
  while(active){
   Device d=null;byte[] current=null;
   lock(gate){if(key!=null){devices.TryGetValue(pairedId,out d);current=key;}}
   if(d!=null)try{string result=Protocol.Send(d,current,"PING");if(result=="READY"||result=="NO_PERMISSION")MarkVerified(d,current);}catch{}
   for(int i=0;i<25&&active;i++)Thread.Sleep(200);
  }
 }

 void Pair(){
  Device d=picker.Selected;
  if(d==null){status.Text="还没发现手机，请在手机开启连接。";Relayout();return;}
  string text=code.Text.Replace("-","").Replace(" ","").Trim().ToLowerInvariant();
  byte[] candidate;
  try{if(text.Length!=32)throw new FormatException();candidate=Protocol.Unhex(text);}
  catch{status.Text="请输入手机显示的完整连接码。";Relayout();return;}
  if(Interlocked.Exchange(ref pairBusy,1)!=0)return;
  pair.Enabled=false;
  ThreadPool.QueueUserWorkItem(_=>{
   try{
    string result=Protocol.Send(d,candidate,"PING");
    if(result!="READY"&&result!="NO_PERMISSION")throw new IOException("手机返回："+result);
    File.WriteAllBytes(config,new PairingRecord{Id=d.Id,Key=candidate,Endpoint=d}.Encode());
    lock(gate){pairedId=d.Id;key=candidate;savedEndpoint=d.Address.ToString();}
    MarkVerified(d,candidate);
    BeginInvoke((Action)(()=>{code.Clear();pairingMode=false;Relayout();}));
    UpdateStatus(result=="READY"?"配对成功，按快捷键就能息屏。":"配对成功，请在手机授予息屏权限。");
   }catch(InvalidDataException e){UpdateStatus(e.Message);}
   catch{UpdateStatus("手机没有响应，请回到手机应用开启连接并允许后台运行。");}
   finally{Interlocked.Exchange(ref pairBusy,0);if(!IsDisposed)try{BeginInvoke((Action)(()=>pair.Enabled=true));}catch{}}
  });
 }

 void SleepPhone(){
  if(key==null){status.Text="先配对手机，就可以一键息屏。";Relayout();ShowWindow();return;}
  Device d;
  lock(gate){if(!devices.TryGetValue(pairedId,out d)){UpdateStatus("等待手机上线，请检查手机连接和 Wi-Fi。");return;}}
  // 连点时不重复发指令，但要说一声，否则用户以为点了没反应。
  if(Interlocked.Exchange(ref sleepBusy,1)!=0){status.Text="上一次息屏还在进行，请稍候。";Relayout();return;}
  byte[] current=key;
  ThreadPool.QueueUserWorkItem(_=>{
   var watch=Stopwatch.StartNew();
   try{
    string result=null;
    for(int attempt=0;attempt<2;attempt++){
     try{
      lock(gate){Device latest;if(devices.TryGetValue(pairedId,out latest))d=latest;}
      result=Protocol.Send(d,current,"SLEEP");break;
     }catch(InvalidDataException){throw;}
     catch(IOException){if(attempt==1)throw;Thread.Sleep(350);}
     catch(SocketException){if(attempt==1)throw;Thread.Sleep(350);}
    }
    if(result=="OK"){MarkVerified(d,current);UpdateStatus("已息屏 · "+watch.ElapsedMilliseconds+" 毫秒");}
    else if(result=="NO_PERMISSION")UpdateStatus("请在手机授予「一键息屏」的息屏权限。");
    else if(result=="CLOCK")UpdateStatus("请将电脑和手机时间设为自动同步。");
    else UpdateStatus("手机返回："+result);
   }catch(InvalidDataException e){UpdateStatus(e.Message);}
   catch{UpdateStatus("手机后台连接已中断。请在手机按品牌完成后台运行设置，并锁定最近任务。");}
   finally{Interlocked.Exchange(ref sleepBusy,0);}
  });
 }
}

static class Program {
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern IntPtr FindWindow(string className,string title);
 [DllImport("user32.dll")]static extern bool PostMessage(IntPtr h,uint msg,IntPtr w,IntPtr l);
 [STAThread]static void Main(string[] args){
  bool created;
  using(var mutex=new Mutex(true,"Local\\PhoneSleepDesktopV1",out created)){
   if(!created){PostMessage(FindWindow(null,"一键息屏"),Array.IndexOf(args,"--sleep")>=0?0x8002u:0x8001u,IntPtr.Zero,IntPtr.Zero);return;}
   Application.EnableVisualStyles();
   Application.SetCompatibleTextRenderingDefault(false);
   Application.Run(new MainForm(Array.IndexOf(args,"--background")>=0));
  }
 }
}

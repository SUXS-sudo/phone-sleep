using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
class IconMaker {
 static void Main(string[] args){using(var image=new Bitmap(64,64))using(var g=Graphics.FromImage(image)){g.SmoothingMode=SmoothingMode.AntiAlias;g.Clear(Color.Transparent);using(var path=new GraphicsPath()){path.AddArc(0,0,24,24,180,90);path.AddArc(39,0,24,24,270,90);path.AddArc(39,39,24,24,0,90);path.AddArc(0,39,24,24,90,90);path.CloseFigure();using(var b=new SolidBrush(Color.FromArgb(53,104,238)))g.FillPath(b,path);}using(var p=new Pen(Color.White,4)){p.StartCap=LineCap.Round;p.EndCap=LineCap.Round;g.DrawArc(p,16,17,32,32,-55,290);g.DrawLine(p,32,13,32,32);}using(var icon=Icon.FromHandle(image.GetHicon()))using(var file=File.Create(args[0]))icon.Save(file);}}
}

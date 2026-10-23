using System;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Windows.Forms;
class ShortcutTests {
 [DllImport("user32.dll",SetLastError=true)]static extern bool RegisterHotKey(IntPtr h,int id,uint mod,uint key);
 [DllImport("user32.dll")]static extern bool UnregisterHotKey(IntPtr h,int id);
 static void Assert(bool value,string label){if(!value)throw new Exception(label);Console.WriteLine("PASS "+label);}
 [STAThread]static void Main(){
  Application.EnableVisualStyles();using(var f=new MainForm(false))using(var blocker=new Form()){
   var apply=typeof(MainForm).GetMethod("ApplyKey",BindingFlags.Instance|BindingFlags.NonPublic);
   Assert(RegisterHotKey(blocker.Handle,21,0x4000,0x76),"removed F7 hotkey is available");UnregisterHotKey(blocker.Handle,21);
   Assert((bool)apply.Invoke(f,new object[]{new Hotkey(0x85,0)}),"custom sleep hotkey registered");
   Assert(!RegisterHotKey(blocker.Handle,22,0x4000,0x85),"custom sleep key exclusively owned");
   Assert(RegisterHotKey(blocker.Handle,23,0x4000,0x83),"test collision key reserved");
   Assert(!(bool)apply.Invoke(f,new object[]{new Hotkey(0x83,0)}),"occupied shortcut rejected");
   var current=(Hotkey)typeof(MainForm).GetField("sleepKey",BindingFlags.Instance|BindingFlags.NonPublic).GetValue(f);
   Assert(current.Key==0x85,"previous setting preserved after conflict");
   UnregisterHotKey(blocker.Handle,23);
   Assert((bool)apply.Invoke(f,new object[]{new Hotkey(0x85,0)}),"original shortcut restored");
   f.Close();
  }
 }
}

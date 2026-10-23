package cn.local.phonesleep;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 手机端统一外观：浅绿背景、白色卡片、柔和绿色主按钮、深灰文字。
 *
 * 按钮用 TextView 自绘而不是 Material Button：Button 自带高程阴影和状态动画，
 * 换成自定义背景后会留下一圈深色边缘，自绘可以从根上避免。
 * 所有含文字的控件都按内容自适应高度，只设 minHeight，因此系统字体放大时不会裁切。
 */
final class Style {
 static final int BG=Color.rgb(241,248,243);
 static final int CARD=Color.WHITE;
 static final int INK=Color.rgb(35,48,41);
 static final int MUTED=Color.rgb(92,122,105);
 static final int GREEN=Color.rgb(38,128,78);
 static final int GREEN_DEEP=Color.rgb(31,110,66);
 static final int PALE=Color.rgb(230,244,234);
 static final int PALE_PRESSED=Color.rgb(209,233,219);
 static final int LINE=Color.rgb(217,234,224);
 static final int OK=Color.rgb(46,158,99);
 static final int WARN=Color.rgb(176,106,32);

 static final Typeface MEDIUM=Typeface.create("sans-serif-medium",Typeface.NORMAL);

 static int dp(Context c,float value){return (int)(c.getResources().getDisplayMetrics().density*value+0.5f);}

 static GradientDrawable round(int color,float radiusDp,Context c){
  GradientDrawable d=new GradientDrawable();
  d.setColor(color);
  d.setCornerRadius(dp(c,radiusDp));
  return d;
 }

 static GradientDrawable roundStroke(int color,float radiusDp,int strokeColor,Context c){
  GradientDrawable d=round(color,radiusDp,c);
  d.setStroke(Math.max(1,dp(c,1)),strokeColor);
  return d;
 }

 static StateListDrawable states(int normal,int pressed,float radiusDp,Context c){
  StateListDrawable s=new StateListDrawable();
  s.addState(new int[]{android.R.attr.state_pressed},round(pressed,radiusDp,c));
  s.addState(new int[]{},round(normal,radiusDp,c));
  return s;
 }

 static StateListDrawable statesStroke(int normal,int pressed,float radiusDp,int stroke,Context c){
  StateListDrawable s=new StateListDrawable();
  s.addState(new int[]{android.R.attr.state_pressed},roundStroke(pressed,radiusDp,stroke,c));
  s.addState(new int[]{},roundStroke(normal,radiusDp,stroke,c));
  return s;
 }

 /** 纯文字控件，强制横向不挤压、行距略松，避免大字体下字挤在一起。 */
 static TextView text(Context c,String value,float sizeSp,int color,boolean medium){
  TextView t=new TextView(c);
  t.setText(value);
  t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sizeSp);
  t.setTextColor(color);
  t.setLineSpacing(dp(c,3),1f);
  t.setIncludeFontPadding(false);
  if(medium)t.setTypeface(MEDIUM);
  return t;
 }

 static TextView title(Context c,String value){return text(c,value,22,INK,true);}
 static TextView heading(Context c,String value){return text(c,value,16,INK,true);}
 static TextView body(Context c,String value){return text(c,value,14,MUTED,false);}
 static TextView caption(Context c,String value){return text(c,value,12.5f,MUTED,false);}

 /** 主按钮：绿色实底白字。次按钮：浅绿底深绿字。 */
 static TextView button(Context c,String title,boolean primary,Runnable action){
  TextView b=text(c,title,15,primary?Color.WHITE:GREEN_DEEP,true);
  b.setGravity(Gravity.CENTER);
  b.setMinHeight(dp(c,48));
  b.setPadding(dp(c,16),dp(c,13),dp(c,16),dp(c,13));
  b.setBackground(primary?states(GREEN,GREEN_DEEP,14,c):states(PALE,PALE_PRESSED,14,c));
  b.setClickable(true);
  b.setFocusable(true);
  b.setOnClickListener(v->action.run());
  return b;
 }

 static void addButton(LinearLayout parent,TextView button){
  parent.addView(button,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
 }

 /** 白色卡片，四周留出统一内边距。 */
 static LinearLayout card(Context c){
  return card(c,20);
 }

 static LinearLayout card(Context c,float paddingDp){
  LinearLayout box=new LinearLayout(c);
  box.setOrientation(LinearLayout.VERTICAL);
  float p=paddingDp;
  box.setPadding(dp(c,p),dp(c,p),dp(c,p),dp(c,p));
  box.setBackground(roundStroke(CARD,18,LINE,c));
  return box;
 }

 static void add(LinearLayout parent,View child){
  parent.addView(child,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
 }

 static void gap(LinearLayout parent,int heightDp){
  parent.addView(new View(parent.getContext()),new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(parent.getContext(),heightDp)));
 }

 static LinearLayout.LayoutParams cardParams(Context c,int bottomGapDp){
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
  lp.bottomMargin=dp(c,bottomGapDp);
  return lp;
 }

 /** 两个并排按钮，中间留出间隙。 */
 static LinearLayout row(Context c){
  LinearLayout r=new LinearLayout(c);
  r.setOrientation(LinearLayout.HORIZONTAL);
  return r;
 }

 static void addRowButton(LinearLayout row,TextView button,int gapDp){
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f);
  lp.rightMargin=dp(row.getContext(),gapDp);
  row.addView(button,lp);
 }

 /** 最后一个并排按钮不带右边距。 */
 static void addRowButtonLast(LinearLayout row,TextView button){
  row.addView(button,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
 }
}
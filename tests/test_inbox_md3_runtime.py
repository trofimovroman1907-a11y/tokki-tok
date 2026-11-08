"""Exercise the inbox with native rows arriving, rebinding and leaving the screen."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

from test_profile_md3_runtime import STUBS as PROFILE_STUBS

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = dict(PROFILE_STUBS)
STUBS.pop('cat/narezany/margyt/Check.java')
STUBS['android/util/DisplayMetrics.java'] = 'package android.util;public class DisplayMetrics{public float density=1,scaledDensity=1;}'
STUBS['android/R.java'] = 'package android;public class R{public static class id{public static int mask=99,content=100;}}'
STUBS['android/graphics/Color.java'] = 'package android.graphics;public class Color{public static int WHITE=-1,TRANSPARENT=0;}'
STUBS['android/content/res/ColorStateList.java'] = 'package android.content.res;public class ColorStateList{int colour;public ColorStateList(int c){colour=c;}public int getDefaultColor(){return colour;}public static ColorStateList valueOf(int c){return new ColorStateList(c);}}'
STUBS['android/graphics/Typeface.java'] = 'package android.graphics;public class Typeface{public static int NORMAL=0,BOLD=1;int style;public static Typeface create(String s,int n){Typeface t=new Typeface();t.style=n;return t;}public static Typeface create(Typeface t,int n){return create("",n);}public boolean isBold(){return style==1;}}'
STUBS['android/graphics/drawable/ColorDrawable.java'] = 'package android.graphics.drawable;public class ColorDrawable extends Drawable{public int fill;public ColorDrawable(int c){fill=c;}}'
STUBS['android/graphics/drawable/GradientDrawable.java'] = STUBS['android/graphics/drawable/GradientDrawable.java'].replace('public int fill;', 'public int fill;public void setStroke(int n,int c){}')
STUBS['android/graphics/drawable/LayerDrawable.java'] = STUBS['android/graphics/drawable/LayerDrawable.java'].replace('public int getId', 'public void setDrawable(int i,Drawable d){layers[i]=d;}public int getId')
STUBS['android/graphics/drawable/RippleDrawable.java'] = '''package android.graphics.drawable;public class RippleDrawable extends LayerDrawable{
public RippleDrawable(Drawable d){super(new Drawable[]{d,new GradientDrawable()});}
public RippleDrawable(android.content.res.ColorStateList c,Drawable d,Drawable mask){super(new Drawable[]{d,mask});}}'''
STUBS['android/text/TextUtils.java'] = 'package android.text;public class TextUtils{public enum TruncateAt{END}}'
STUBS['android/view/Gravity.java'] = 'package android.view;public class Gravity{public static int START=3,END=5,CENTER=17,CENTER_VERTICAL=16,CENTER_HORIZONTAL=1,BOTTOM=80,TOP=48;}'
STUBS['android/view/View.java'] = STUBS['android/view/View.java'].replace('public static final int VISIBLE', '''
 public interface OnLayoutChangeListener{void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 private java.util.List<OnLayoutChangeListener> layouts=new java.util.ArrayList<>();private int minimum,minwidth,px,py,mw,mh;private float tx,ty,sx=1,sy=1;
 public int getMinimumWidth(){return minwidth;}public void setMinimumWidth(int n){minwidth=n;}
 public float getScaleX(){return sx;}public float getScaleY(){return sy;}public void setScaleX(float n){sx=n;}public void setScaleY(float n){sy=n;}
 public void addOnLayoutChangeListener(OnLayoutChangeListener l){layouts.add(l);}public void removeOnLayoutChangeListener(OnLayoutChangeListener l){layouts.remove(l);}
 public static class MeasureSpec{public static int EXACTLY=1;public static int makeMeasureSpec(int size,int mode){return size;}}
 public void measure(int w,int h){mw=w;mh=h;}public void layout(int l,int t,int r,int b){px=l;py=t;}
 public int getTop(){return py;}public int getLeft(){return px;}public static int generateViewId(){return 777;}
 public float getTranslationX(){return tx;}public float getTranslationY(){return ty;}public void setTranslationX(float f){tx=f;}public void setTranslationY(float f){ty=f;}
 public int getMinimumHeight(){return minimum;}public void setMinimumHeight(int n){minimum=n;}public boolean isEnabled(){return true;}
 public float getAlpha(){return alpha;}
 public static final int VISIBLE''').replace('public void getLocationInWindow(int[] p){}', 'public void getLocationInWindow(int[] p){if(parent!=null)parent.getLocationInWindow(p);p[0]+=px+tx;p[1]+=py+ty;}')
STUBS['android/view/ViewGroup.java'] = STUBS['android/view/ViewGroup.java'].replace('public int leftMargin,topMargin,rightMargin,bottomMargin;', 'public int leftMargin,topMargin,rightMargin,bottomMargin;public int getMarginStart(){return leftMargin;}public int getMarginEnd(){return rightMargin;}public void setMarginStart(int n){leftMargin=n;}public void setMarginEnd(int n){rightMargin=n;}').replace('public void setClipChildren(boolean b){}public void setClipToPadding(boolean b){}', 'private boolean cc=true,cp=true;public void setClipChildren(boolean b){cc=b;}public void setClipToPadding(boolean b){cp=b;}public boolean getClipChildren(){return cc;}public boolean getClipToPadding(){return cp;}')
STUBS['android/widget/FrameLayout.java'] = STUBS['android/widget/FrameLayout.java'].replace('public LayoutParams(int w,int h){super(w,h);}', 'public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,int g){super(w,h);}')
STUBS['android/widget/RelativeLayout.java'] = '''package android.widget;public class RelativeLayout extends android.view.ViewGroup{
public static int ALIGN_PARENT_START=0,CENTER_IN_PARENT=1,CENTER_HORIZONTAL=2,CENTER_VERTICAL=3,START_OF=4,ALIGN_PARENT_LEFT=5;
public RelativeLayout(android.content.Context c){super(c);}public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{
int[] rules=new int[8];public LayoutParams(int w,int h){super(w,h);}public int[] getRules(){return rules;}public int getRule(int n){return rules[n];}
public void removeRule(int n){rules[n]=0;}public void addRule(int n){rules[n]=-1;}public void addRule(int n,int a){rules[n]=a;}}}'''
STUBS['android/widget/TextView.java'] = STUBS['android/widget/TextView.java'].replace('public int ink;', 'public int ink;private android.graphics.Typeface face=new android.graphics.Typeface();public int getCurrentTextColor(){return ink;}public android.graphics.Typeface getTypeface(){return face;}public void setEllipsize(android.text.TextUtils.TruncateAt t){}').replace('setTypeface(android.graphics.Typeface t){}', 'setTypeface(android.graphics.Typeface t){face=t;}')
STUBS['android/widget/TextView.java'] = STUBS['android/widget/TextView.java'].replace('public int ink;', '''public int ink;private int maxwidth=Integer.MAX_VALUE,maxems=-1;private boolean fontpadding=true;
public void setMaxWidth(int n){maxwidth=n;maxems=-1;}public int getMaxWidth(){return maxwidth;}
public void setMaxEms(int n){maxems=n;maxwidth=-1;}public int getMaxEms(){return maxems;}
public void setIncludeFontPadding(boolean n){fontpadding=n;}public boolean getIncludeFontPadding(){return fontpadding;}
''')
STUBS['cat/narezany/margyt/Md3.java'] = '''package cat.narezany.margyt;class Md3{static final String KEY_INBOX="inbox";static boolean on=true;static int page=0xff141218;
static boolean isOn(String s){return on;}static int behind(android.view.View v){return page;}static int ink(int c){return NavBar.bright(c)<.5f?-1:0xff161823;}}'''
STUBS['cat/narezany/margyt/ProfileLayout.java'] = 'package cat.narezany.margyt;class ProfileLayout{static boolean shown=true;static boolean onScreen(android.view.View v,android.view.View r){return shown&&v.isShown();}}'
STUBS['cat/narezany/margyt/Popup.java'] = 'package cat.narezany.margyt;class Popup{static void show(android.view.View v,String a,String b,String c,android.graphics.drawable.Drawable d){throw new AssertionError("unexpected fallback");}}'

HARNESS = '''package cat.narezany.margyt;
import android.view.*;import android.widget.*;import android.graphics.drawable.*;
public class InboxCheck{
static android.content.Context c=new android.content.Context();static int seq=1000,opens,compose;
static void ck(boolean b,String s){if(!b)throw new AssertionError(s);}static int id(String n){return android.content.res.Resources.ids.computeIfAbsent(n,k->seq++);}
static <T extends View>T named(T v,String n){v.setId(id(n));return v;}static TextView text(String n,String s){TextView t=named(new TextView(c),n);t.setText(s);return t;}
public static class Badge extends View{public Badge(){super(c);}public int getCount(){return 500;}public int getMaxCount(){return 99;}}
public static class Avatar extends FrameLayout{View picture=new View(c);public Avatar(){super(c);addView(picture,new FrameLayout.LayoutParams(64,64));}public View getRealAvatarView(){return picture;}}
static int[] at(View view){int[] p=new int[2];view.getLocationInWindow(p);return p;}
static ViewGroup notice(String title,String preview){LinearLayout row=named(new LinearLayout(c),"uxm");row.setLayoutParams(new LinearLayout.LayoutParams(-1,72));row.addView(text("brn",title));row.addView(text("nhq",preview));row.addView(named(new Badge(),"nhs"));row.setOnClickListener(v->opens++);return row;}
public static void main(String[] args){
 FrameLayout root=new FrameLayout(c);root.setId(android.R.id.content);root.setLayoutParams(new ViewGroup.LayoutParams(412,850));
 LinearLayout list=named(new LinearLayout(c),"l78");list.setLayoutParams(new FrameLayout.LayoutParams(412,700));root.addView(list);
 View nativeCompose=named(new View(c),"fha");nativeCompose.setOnClickListener(v->compose++);root.addView(nativeCompose);
 View nav=named(new View(c),"on6");nav.layout(0,760,412,832);root.addView(nav);
 FrameLayout filters=new FrameLayout(c);filters.setLayoutParams(new LinearLayout.LayoutParams(-1,44));
 HorizontalScrollView scroll=new HorizontalScrollView(c);LinearLayout chips=new LinearLayout(c);scroll.addView(chips);filters.addView(scroll);list.addView(filters);
 LinearLayout main=new LinearLayout(c);TextView mainLabel=text("","Основные");mainLabel.setTextColor(0xff9988ff);main.addView(mainLabel);chips.addView(main);
 TextView count=text("","99+");count.setIncludeFontPadding(true);LinearLayout.LayoutParams countParams=new LinearLayout.LayoutParams(30,32);countParams.gravity=Gravity.TOP;main.addView(count,countParams);
 ViewGroup followers=notice("Alice","подписались на вас"),activity=notice("Активность",""),system=notice("Уведомления системы",""),money=notice("Монетизация","");
 list.addView(followers);list.addView(activity);list.addView(system);list.addView(money);
 LinearLayout chat=named(new LinearLayout(c),"w0w");chat.setLayoutParams(new LinearLayout.LayoutParams(412,72));
 TextView name=text("user_name","Person"),preview=text("mv1","Real native message");name.layout(94,16,250,36);chat.addView(name);chat.addView(preview);list.addView(chat);
 TextView time=text("mv7","16 мин");time.setLayoutParams(new LinearLayout.LayoutParams(50,20));time.layout(280,39,330,59);chat.addView(time);
 LinearLayout actions=named(new LinearLayout(c),"gdj");actions.layout(352,0,412,72);chat.addView(actions);
 View camera=named(new View(c),"gdi"),unread=named(new View(c),"gdm");actions.addView(camera,new LinearLayout.LayoutParams(24,24));camera.layout(20,24,44,48);actions.addView(unread,new LinearLayout.LayoutParams(20,20));unread.layout(20,24,40,44);
 InboxMd3.list(list,true);
 ck(list.getChildCount()==6&&list.getChildAt(1)==followers&&list.getChildAt(5)==chat,"native RecyclerView order and identities unchanged");
 ck(followers.getLayoutParams().height==0&&activity.getLayoutParams().height==0,"native service rows collapsed after capture");
 ck(filters.getChildCount()==2&&filters.getLayoutParams().height==166,"tiles reserve space in the existing filter header");
 LinearLayout panel=(LinearLayout)filters.getChildAt(1),grid=(LinearLayout)panel.getChildAt(0);ck(panel.getTop()==44&&grid.getChildCount()==4,"panel sits below native filters");
 ck(chat.getLayoutParams().height==76&&name.getText().toString().equals("Person")&&preview.getText().toString().equals("Real native message"),"chat style preserves real content");
 FrameLayout first=(FrameLayout)grid.getChildAt(0);TextView badge=(TextView)first.getChildAt(1);ck("99+".contentEquals(badge.getText()),"custom native badge count and max are mirrored");
 first.performClick();ck(opens==1,"tile uses native row action");
 LinearLayout fab=(LinearLayout)root.getChildAt(root.getChildCount()-1);fab.performClick();ck(compose==1,"compose uses native picker without sending anything");
 int views=filters.getChildCount();for(int i=0;i<8;i++)InboxMd3.list(list,true);ck(filters.getChildCount()==views&&list.getChildCount()==6,"repeated layout creates no duplicate headers or rows");
 InboxMd3.folder(main,true);ck(InboxMd3.nativeInk(mainLabel)==0xff9988ff,"chip painting preserves native selection signal");
 ck(count.getLayoutParams().height==18&&((LinearLayout.LayoutParams)count.getLayoutParams()).gravity==Gravity.CENTER_VERTICAL&&!count.getIncludeFontPadding(),"folder counter has a compact centred box");
 int counterInk=count.getCurrentTextColor();count.setTextColor(0xffeeeeee);count.setBackground(new ColorDrawable(0xff444444));
 mainLabel.setTextColor(0xff123456);InboxMd3.folderFrame(list);
 ck(count.getCurrentTextColor()==counterInk&&count.getBackground() instanceof GradientDrawable,"native counter colour/background reset is repaired before drawing");
 ck(InboxMd3.nativeInk(mainLabel)==0xff123456,"repair retains the fresh native selection colour");
 mainLabel.setTextColor(0xff9988ff);InboxMd3.folderFrame(list);
 LinearLayout lateChip=new LinearLayout(c);lateChip.addView(text("","Позже"));chips.addView(lateChip);InboxMd3.folder(lateChip,false);
 TextView lateCount=text("","7");lateChip.addView(lateCount,new LinearLayout.LayoutParams(20,32));InboxMd3.folderFrame(list);
 ck(lateCount.getLayoutParams().height==18&&lateCount.getCurrentTextColor()==counterInk,"counter added after the initial scan is styled before drawing");
 LinearLayout emptyChip=new LinearLayout(c);emptyChip.addView(text("","Непрочитанные"));TextView emptyCount=text("","");emptyChip.addView(emptyCount,new LinearLayout.LayoutParams(20,32));chips.addView(emptyChip);InboxMd3.folder(emptyChip,false);
 emptyCount.setText("99+");InboxMd3.folderFrame(list);
 ck(emptyCount.getLayoutParams().height==18&&emptyCount.getCurrentTextColor()==counterInk&&emptyCount.getBackground() instanceof GradientDrawable,"initially empty counter is painted when its count arrives");
 emptyCount.setText("");InboxMd3.folder(emptyChip,false);emptyCount.setText("8");InboxMd3.folderFrame(list);
 ck(emptyCount.getCurrentTextColor()==counterInk,"counter regains its badge text colour after clearing and repopulating");
 ck(at(camera)[1]>=at(time)[1]+time.getHeight()+4&&at(unread)[1]>=at(time)[1]+time.getHeight()+4,"camera and badge clear the timestamp vertically");
 ck(at(unread)[0]+unread.getWidth()+8<=at(camera)[0],"simultaneous badge and camera do not overlap");
 ck(at(name)[0]+name.getMaxWidth()+8<=at(time)[0],"long names reserve timestamp space");
 LinearLayout stories=new LinearLayout(c),cell=new LinearLayout(c);cell.setPadding(0,8,0,10);cell.setClipToPadding(true);stories.addView(cell);Avatar avatar=named(new Avatar(),"wq0");avatar.layout(8,33,72,97);cell.addView(avatar,new LinearLayout.LayoutParams(64,64));
 TextView note=text("q4n","Что хорошего?"),caption=text("wq6","Real name");note.setLayoutParams(new LinearLayout.LayoutParams(72,52));note.layout(4,9,76,61);cell.addView(note);caption.setLayoutParams(new LinearLayout.LayoutParams(72,20));caption.layout(4,87,76,107);cell.addView(caption);list.addView(stories);
 InboxMd3.refresh();ck(Math.abs(avatar.picture.getScaleX()-.875f)<.001f,"avatar picture leaves space inside the story ring");
 float noteTop=note.getTop()+note.getTranslationY()+note.getHeight()*(1-note.getScaleY())/2;
 ck(noteTop>=-.01f&&noteTop+note.getHeight()*note.getScaleY()<=avatar.getTop()+avatar.getTranslationY()-2,"smaller note sits above the avatar");
 ck(caption.getTop()+caption.getTranslationY()>=avatar.getTop()+avatar.getTranslationY()+64+4,"story caption clears the avatar");
 ck(!cell.getClipToPadding()&&caption.getTop()+caption.getTranslationY()+caption.getHeight()<=cell.getHeight()-4,"native bottom padding cannot cut through a story name");
 caption.getLayoutParams().height=26;InboxMd3.refresh();
 ck(caption.getTop()+caption.getTranslationY()+caption.getHeight()<=cell.getHeight()-4,"larger name line still fits in the story row");
 Md3.page=0xfff2edf7;InboxMd3.refresh();ck(((GradientDrawable)((LayerDrawable)first.getBackground()).getDrawable(0)).fill!=0,"light theme repaints ripple content");
 ProfileLayout.shown=false;InboxMd3.refresh();ck(fab.getVisibility()==View.GONE,"offscreen cached inbox cannot overlay the profile or feed");ProfileLayout.shown=true;
 // A recycled notification row must regain its original dimensions and opacity.
 ((TextView)followers.findViewById(id("nhq"))).setText("another native row");((TextView)followers.findViewById(id("brn"))).setText("Unknown type");
 InboxMd3.refresh();ck(followers.getLayoutParams().height==72&&followers.getAlpha()==1,"rebound row is restored instead of left hidden");
 Md3.on=false;InboxMd3.refresh();ck(filters.getChildCount()==1&&filters.getLayoutParams().height==44,"disabling restores filter hierarchy and height");
 ck(activity.getLayoutParams().height==72&&activity.getAlpha()==1&&chat.getLayoutParams().height==72,"native service and conversation sizes restored");
 ck(root.getChildCount()==3&&mainLabel.getCurrentTextColor()==0xff9988ff,"compose removed and native selection colours restored");
 ck(count.getLayoutParams().height==32&&count.getIncludeFontPadding()&&((LinearLayout.LayoutParams)count.getLayoutParams()).gravity==Gravity.TOP,"native counter geometry restored");
 ck(camera.getTranslationX()==0&&camera.getTranslationY()==0&&name.getMaxWidth()==Integer.MAX_VALUE,"native chat positions restored");
 ck(avatar.picture.getScaleX()==1&&note.getScaleY()==1&&note.getTranslationY()==0,"native story geometry restored");
 ck(cell.getClipToPadding()&&cell.getPaddingBottom()==10,"native story clipping and padding restored");
 Md3.on=true;InboxMd3.list(list,true);ck(filters.getChildCount()==2,"reenabling works without stale header state");
 }
}'''


class InboxMd3RuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_native_rows_lifecycle_counters_actions_and_themes(self):
        sources = dict(STUBS)
        sources['cat/narezany/margyt/InboxMd3.java'] = (ROOT / 'inject/java/cat/narezany/margyt/InboxMd3.java').read_text()
        sources['cat/narezany/margyt/InboxCheck.java'] = HARNESS
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for name, content in sources.items():
                path = pathlib.Path(directory) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            result = subprocess.run(['javac', '-d', directory, *paths], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(['java', '-cp', directory, 'cat.narezany.margyt.InboxCheck'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

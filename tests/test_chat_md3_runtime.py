"""Native input, controls and messages survive chat styling and its off switch."""
import pathlib
import shutil
import subprocess
import struct
import tempfile
import unittest

from test_inbox_md3_runtime import STUBS as INBOX_STUBS

ROOT = pathlib.Path(__file__).resolve().parents[1]
STUBS = dict(INBOX_STUBS)
STUBS['cat/narezany/margyt/Md3.java'] = STUBS['cat/narezany/margyt/Md3.java'].replace('KEY_INBOX="inbox"', 'KEY_INBOX="inbox",KEY_CHAT="chat"')
STUBS['android/graphics/ColorFilter.java'] = 'package android.graphics;public class ColorFilter{}'
STUBS['android/graphics/Outline.java'] = 'package android.graphics;public class Outline{public float radius;public void setRoundRect(int a,int b,int c,int d,float r){radius=r;}}'
STUBS['android/graphics/PixelFormat.java'] = 'package android.graphics;public class PixelFormat{public static int TRANSLUCENT=-3;}'
STUBS['android/graphics/Rect.java'] = 'package android.graphics;public class Rect{public int left,top,right,bottom;public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}'
STUBS['android/graphics/RectF.java'] = '''package android.graphics;public class RectF{public float left,top,right,bottom;
public void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}}'''
STUBS['android/graphics/Paint.java'] = '''package android.graphics;public class Paint{public static int ANTI_ALIAS_FLAG=1;int colour;
public Paint(int n){}public int getColor(){return colour;}public void setColor(int c){colour=c;}public void setAlpha(int n){}public void setColorFilter(ColorFilter c){}}'''
STUBS['android/graphics/Canvas.java'] = '''package android.graphics;public class Canvas{public float right,left;public void drawRoundRect(RectF box,float a,float b,Paint p){right=box.right;left=box.left;}}'''
STUBS['android/graphics/drawable/Drawable.java'] = STUBS['android/graphics/drawable/Drawable.java'].replace('public static class ConstantState', '''
 private android.graphics.Rect bounds=new android.graphics.Rect(0,0,400,52);
 public void setBounds(int l,int t,int r,int b){bounds=new android.graphics.Rect(l,t,r,b);}public android.graphics.Rect getBounds(){return bounds;}
 public void draw(android.graphics.Canvas c){}public void invalidateSelf(){}public void setAlpha(int n){}public void setColorFilter(android.graphics.ColorFilter c){}public int getOpacity(){return -3;}
 public static class ConstantState''')
STUBS['android/view/ViewOutlineProvider.java'] = 'package android.view;public abstract class ViewOutlineProvider{public abstract void getOutline(View v,android.graphics.Outline o);}'
STUBS['android/view/View.java'] = STUBS['android/view/View.java'].replace('public static final int VISIBLE', '''
 public static final int LAYOUT_DIRECTION_RTL=1;private ViewOutlineProvider outline;
 public int getLayoutDirection(){return 0;}public ViewOutlineProvider getOutlineProvider(){return outline;}public void setOutlineProvider(ViewOutlineProvider n){outline=n;}
 public static final int VISIBLE''')
STUBS['android/widget/FrameLayout.java'] = STUBS['android/widget/FrameLayout.java'].replace('public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{', 'public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams{public int gravity=-1;')
STUBS['android/widget/ImageView.java'] = STUBS['android/widget/ImageView.java'].replace('public enum ScaleType', '''
private android.graphics.ColorFilter filter;public android.graphics.ColorFilter getColorFilter(){return filter;}
public void setColorFilter(android.graphics.ColorFilter c){filter=c;}
public enum ScaleType''').replace('setColorFilter(int c,android.graphics.PorterDuff.Mode m){}', 'setColorFilter(int c,android.graphics.PorterDuff.Mode m){filter=new android.graphics.ColorFilter();}')
STUBS['android/widget/TextView.java'] = STUBS['android/widget/TextView.java'].replace('public int ink;', '''
public int ink;private android.text.TextUtils.TruncateAt ellipsize;private android.content.res.ColorStateList hints=android.content.res.ColorStateList.valueOf(0xff777777);
public android.content.res.ColorStateList getTextColors(){return android.content.res.ColorStateList.valueOf(ink);}public void setTextColor(android.content.res.ColorStateList n){ink=n.getDefaultColor();}
public int getCurrentHintTextColor(){return hints.getDefaultColor();}public android.content.res.ColorStateList getHintTextColors(){return hints;}
public void setHintTextColor(int n){hints=android.content.res.ColorStateList.valueOf(n);}public void setHintTextColor(android.content.res.ColorStateList n){hints=n;}
public android.text.TextUtils.TruncateAt getEllipsize(){return ellipsize;}
''').replace('setEllipsize(android.text.TextUtils.TruncateAt t){}', 'setEllipsize(android.text.TextUtils.TruncateAt t){ellipsize=t;}')
STUBS['android/widget/EditText.java'] = '''package android.widget;public class EditText extends TextView{public int sets;
public EditText(android.content.Context c){super(c);}@Override public void setText(CharSequence text){super.setText(text);sets++;}}'''
STUBS['com/ss/android/ugc/aweme/views/RoundingCornerLayoutCL.java'] = '''package com.ss.android.ugc.aweme.views;public class RoundingCornerLayoutCL extends android.widget.FrameLayout{public RoundingCornerLayoutCL(android.content.Context c){super(c);}}'''
STUBS['test/Cell.java'] = '''package test;public class Cell{public android.view.View itemView;public Object item;
public Object getItem(){return item;}}'''
for direction in ['Send', 'Receive']:
    name = 'TextTranslate' + direction + 'Cell'
    STUBS[f'com/ss/android/ugc/aweme/im/sdk/chat/ui/text/content/{name}.java'] = f'''package com.ss.android.ugc.aweme.im.sdk.chat.ui.text.content;public class {name} extends test.Cell{{}}'''
STUBS['test/Rows.java'] = '''package test;public class Rows extends android.widget.FrameLayout{
public java.util.Map<android.view.View,Object> holders=new java.util.IdentityHashMap<>();public Rows(android.content.Context c){super(c);}
public Object findContainingViewHolder(android.view.View row){return holders.get(row);}}'''
NATIVE_CLASSES = ['15FJ', '15FI', '0QTr', '13Z9', '13ZA', '13ZB', '15FC', '15Kc']
for name in ['15FJ', '15FI']:
    STUBS[f'X/A{name}.java'] = f'package X;public class A{name}{{public A0QTr config=new A0QTr();public A0QTr LJI(){{return config;}}}}'
STUBS['X/A0QTr.java'] = 'package X;public class A0QTr{public Object LIZJ=new A13Z9();}'
for name in ['13Z9', '13ZA', '13ZB', '15FC']:
    STUBS[f'X/A{name}.java'] = f'package X;public class A{name}{{}}'
STUBS['X/A15Kc.java'] = '''package X;public class A15Kc{public static A15Kc LIZ=new A15Kc();
public android.view.View LIZ(android.view.View row,boolean alternate){return row.findViewById(alternate?0x7f0a3ab4:0x7f0a1bf7);}}'''


def native_names(directory):
    """JVM names can start with digits; javac's source syntax cannot."""
    replacements = {('X/A' + name).encode(): ('X/' + name).encode() for name in NATIVE_CLASSES}
    for path in pathlib.Path(directory).rglob('*.class'):
        data = path.read_bytes()
        count = struct.unpack_from('>H', data, 8)[0]
        output = bytearray(data[:10])
        pos, index = 10, 1
        sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        while index < count:
            tag = data[pos]
            pos += 1
            output.append(tag)
            if tag == 1:
                length = struct.unpack_from('>H', data, pos)[0]
                pos += 2
                value = data[pos:pos + length]
                pos += length
                for old, new in replacements.items():
                    value = value.replace(old, new)
                output.extend(struct.pack('>H', len(value)))
                output.extend(value)
            else:
                size = sizes[tag]
                output.extend(data[pos:pos + size])
                pos += size
                if tag in (5, 6):
                    index += 1
            index += 1
        output.extend(data[pos:])
        path.write_bytes(output)
    for name in NATIVE_CLASSES:
        (pathlib.Path(directory) / 'X' / ('A' + name + '.class')).rename(pathlib.Path(directory) / 'X' / (name + '.class'))

HARNESS = '''package cat.narezany.margyt;
import android.view.*;import android.widget.*;import android.graphics.*;import android.graphics.drawable.*;
public class ChatCheck {
 static android.content.Context c=new android.content.Context();static int seq=2000,backCalls,voiceCalls,mediaCalls;
 static void ck(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int id(String name){return android.content.res.Resources.ids.computeIfAbsent(name,k->seq++);}
 static <T extends View>T named(T view,String name){view.setId(id(name));return view;}
 static FrameLayout group(String name){return named(new FrameLayout(c),name);}
 static TextView text(String name,String value){TextView view=named(new TextView(c),name);view.setText(value);return view;}
 public static void main(String[] args){
  android.content.res.Resources.ids.put("f5z",0x7f0a1bf7);android.content.res.Resources.ids.put("kwn",0x7f0a3ab4);
  FrameLayout root=group("dy_");root.setLayoutParams(new FrameLayout.LayoutParams(412,916));
  FrameLayout bar=group("dyq"),wrap=group("wrap"),line=group("line");root.addView(bar,new FrameLayout.LayoutParams(-1,56));bar.addView(wrap,new FrameLayout.LayoutParams(-1,56));wrap.addView(line,new FrameLayout.LayoutParams(-1,56));
  View back=named(new View(c),"n9k");back.setOnClickListener(v->backCalls++);line.addView(back,new FrameLayout.LayoutParams(40,56));
  TextView name=text("ihm","Real nickname"),status=text("vn5","Online");name.setTextColor(0xffee7722);line.addView(name);line.addView(status);
  test.Rows list=named(new test.Rows(c),"u52");root.addView(list);
  FrameLayout message=group("wm7");list.addView(message);
  com.ss.android.ugc.aweme.views.RoundingCornerLayoutCL video=named(new com.ss.android.ugc.aweme.views.RoundingCornerLayoutCL(c),"f7j");
  video.setOnClickListener(v->mediaCalls++);message.addView(video,new FrameLayout.LayoutParams(162,216));Drawable picture=new Drawable();ImageView image=new ImageView(c);image.setImageDrawable(picture);video.addView(image);
  TextView time=text("pdh","14:32:43");message.addView(time);
  FrameLayout quote=group("kv7"),quoteOuter=group("kvc"),quoteBody=group("kv6");View stripe=named(new View(c),"kvb");message.addView(quote);quote.addView(quoteOuter);quoteOuter.addView(stripe);quoteOuter.addView(quoteBody);
  Drawable nativeQuote=new ColorDrawable(0xff555555),outerQuote=new ColorDrawable(0xff112233);quoteBody.setBackground(nativeQuote);quoteOuter.setBackground(outerQuote);
  FrameLayout plain=group("wm7"),body=group("f5z");plain.addView(body);list.addView(plain);Drawable originalBubble=new ColorDrawable(0xff444444);body.setBackground(originalBubble);
  com.ss.android.ugc.aweme.im.sdk.chat.ui.text.content.TextTranslateSendCell cell=new com.ss.android.ugc.aweme.im.sdk.chat.ui.text.content.TextTranslateSendCell();cell.itemView=plain;X.A15FJ item=new X.A15FJ();cell.item=item;list.holders.put(plain,cell);
  ck(ChatBubbles.find(list,plain)!=null&&ChatBubbles.find(list,plain).outgoing,"verified plain send holder classified");
  item.config.LIZJ=new X.A13ZA();ck(ChatBubbles.find(list,plain)!=null,"second verified default style accepted");item.config.LIZJ=new X.A13ZB();ck(ChatBubbles.find(list,plain)!=null,"third verified default style accepted");
  item.config.LIZJ=new X.A15FC();ck(ChatBubbles.find(list,plain)==null,"custom metadata rejected before its image loads");item.config.LIZJ=new X.A13Z9();
  body.setTag(0x7f0a0a37,"custom-resource");ck(ChatBubbles.find(list,plain)==null,"custom resource tag rejected");body.setTag(0x7f0a0a37,null);
  cell.itemView=message;ck(ChatBubbles.find(list,plain)==null,"mismatched recycled holder rejected");cell.itemView=plain;
  FrameLayout composer=group("ui_slot_input_layout_redesign"),field=group("lf9");root.addView(composer);composer.addView(field,new FrameLayout.LayoutParams(344,-2));
  EditText edit=new EditText(c);edit.setText("draft\\nsecond line");edit.setMaxLines(6);field.addView(edit,new FrameLayout.LayoutParams(210,-2));
  FrameLayout voice=group("lfm"),touch=group("cvv");field.addView(voice,new FrameLayout.LayoutParams(36,36));voice.layout(300,4,336,40);voice.addView(touch,new FrameLayout.LayoutParams(36,36));touch.setOnClickListener(v->voiceCalls++);
  ImageView mic=named(new ImageView(c),"u1w");touch.addView(mic,new FrameLayout.LayoutParams(36,36));
  ImageView send=new ImageView(c);voice.addView(send,new FrameLayout.LayoutParams(36,36));send.setVisibility(View.GONE);send.setOnClickListener(v->voiceCalls++);
  Drawable nativeField=new ColorDrawable(0xff223344);field.setBackground(nativeField);
  int children=root.getChildCount(),draftWrites=edit.sets;
  ChatMd3.screen(root,true);
  ck(quoteBody.getBackground()!=nativeQuote&&quoteOuter.getBackground()==outerQuote&&!quoteOuter.getClipToOutline(),"quote colours the actual native body without changing overlap container");
  ck(body.getBackground()!=originalBubble,"plain outgoing bubble receives accent background");
  item.config.LIZJ=new X.A15FC();ChatMd3.refresh();ck(body.getBackground()==originalBubble,"recycled plain bubble restores native background while custom image loads");
  Drawable loadedCustom=new Drawable();body.setBackground(loadedCustom);ChatMd3.refresh();ck(body.getBackground()==loadedCustom,"custom loaded artwork is preserved");
  item.config.LIZJ=new X.A13Z9();ChatMd3.refresh();
  ck(bar.getLayoutParams().height==64&&wrap.getLayoutParams().height==64&&line.getLayoutParams().height==64,"header wrapper heights match");
  ck(name.getCurrentTextColor()==0xffee7722,"native coloured nickname retained");
  ck(field.getLayoutParams().height==-2&&edit.getLayoutParams().height==-2&&edit.getMaxLines()==6,"native multiline input can grow");
  ck(mic.getLayoutParams().width==44&&mic.getPaddingLeft()==10&&send.getLayoutParams().width==44,"mic and send retain full touch targets with compact glyphs");
  ck(edit.sets==draftWrites&&edit.getText().toString().equals("draft\\nsecond line"),"draft and IME text are not rewritten");
  ck(video.getParent()==message&&video.getLayoutParams().width==144&&video.getLayoutParams().height==192&&image.getDrawable()==picture,"media retains native aspect and bitmap at the compact size");
  ck(video.getClipToOutline()&&time.getText().toString().equals("14:32"),"video rounded and timestamp compact");
  back.performClick();touch.performClick();video.performClick();ck(backCalls==1&&voiceCalls==1&&mediaCalls==1,"native control handlers retained");
  Canvas canvas=new Canvas();field.getBackground().setBounds(0,0,344,52);field.getBackground().draw(canvas);ck(canvas.right==292,"field leaves a gap before native mic without reparenting it");
  voice.setVisibility(View.GONE);field.getBackground().draw(canvas);ck(canvas.right==344,"send/hidden mic state leaves full field background");voice.setVisibility(View.VISIBLE);
  for(int i=0;i<5;i++)ChatMd3.screen(root,true);ck(root.getChildCount()==children&&edit.sets==draftWrites,"refresh adds no wrappers or text writes");
  int dark=((GradientDrawable)voice.getBackground()).fill;Md3.page=0xfffaf5ff;ChatMd3.refresh();int light=((GradientDrawable)voice.getBackground()).fill;
  ck(dark!=light&&mic.getColorFilter()!=null,"accent and icon follow light theme");
  touch.setVisibility(View.GONE);send.setVisibility(View.VISIBLE);ChatMd3.refresh();ck(send.getColorFilter()!=null&&send.getLayoutParams().width==44,"native send transition retains themed icon and hit target");send.performClick();ck(voiceCalls==2,"native send handler retained");
  ProfileLayout.shown=false;int unchanged=((GradientDrawable)voice.getBackground()).fill;Md3.page=0xff141218;ChatMd3.refresh();ck(((GradientDrawable)voice.getBackground()).fill==unchanged,"cached offscreen chats are not restyled");ProfileLayout.shown=true;
  time.setText("16:45:02");ChatMd3.refresh();ck(time.getText().toString().equals("16:45"),"rebound timestamp uses latest native text");
  video.getLayoutParams().width=180;video.getLayoutParams().height=120;ChatMd3.refresh();ck(video.getLayoutParams().width==144&&video.getLayoutParams().height==96,"rebound landscape video retains its new aspect");
  body.setLayoutParams(new FrameLayout.LayoutParams(240,90));body.setPadding(5,6,7,8);body.setAlpha(.7f);
  android.graphics.Typeface reboundFace=android.graphics.Typeface.create("rebound",0);time.setTypeface(reboundFace);
  back.getLayoutParams().width=52;
  Md3.on=false;ChatMd3.refresh();
  ck(field.getBackground()==nativeField&&bar.getLayoutParams().height==56&&back.getLayoutParams().width==52,"switch restores owned dimensions and preserves fresh native changes");
  ck(body.getBackground()==loadedCustom,"latest native bubble background restored after rebind");
  ck(body.getLayoutParams().width==240&&body.getLayoutParams().height==90&&body.getPaddingLeft()==5&&body.getPaddingBottom()==8&&body.getAlpha()==.7f,"recolour-only state never restores stale native geometry");
  ck(time.getTypeface()==reboundFace,"unmodified native typeface survives timestamp restoration");
  ck(quoteBody.getBackground()==nativeQuote&&quoteOuter.getBackground()==outerQuote,"native quote surfaces restored");
  ck(!video.getClipToOutline()&&time.getText().toString().equals("16:45:02"),"switch restores clipping and current timestamp");
  ck(video.getLayoutParams().width==180&&video.getLayoutParams().height==120,"disable restores current native media size after recycling");
  ck(mic.getColorFilter()==null&&mic.getLayoutParams().width==36&&voice.getLayoutParams().width==36,"voice tint and sizes restored");
  ck(send.getColorFilter()==null&&send.getLayoutParams().width==36&&send.getVisibility()==View.VISIBLE,"current native send state preserved on disable");
  ck(edit.getText().toString().equals("draft\\nsecond line")&&edit.sets==draftWrites&&edit.getTextSize()==22,"draft survives disable");
  Md3.on=true;ChatMd3.screen(root,true);ck(root.getChildCount()==children&&field.getBackground()!=nativeField,"re-enable without duplicates");
 }
}'''


class ChatMd3RuntimeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_native_input_handlers_theme_and_restoration(self):
        sources = dict(STUBS)
        sources['cat/narezany/margyt/ChatMd3.java'] = (ROOT / 'inject/java/cat/narezany/margyt/ChatMd3.java').read_text()
        sources['cat/narezany/margyt/ChatBubbles.java'] = (ROOT / 'inject/java/cat/narezany/margyt/ChatBubbles.java').read_text()
        sources['cat/narezany/margyt/ChatCheck.java'] = HARNESS
        with tempfile.TemporaryDirectory() as directory:
            paths = []
            for name, content in sources.items():
                path = pathlib.Path(directory) / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                paths.append(str(path))
            result = subprocess.run(['javac', '-d', directory, *paths], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            native_names(directory)
            result = subprocess.run(['java', '-cp', directory, 'cat.narezany.margyt.ChatCheck'], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

package cat.narezany.margyt;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Material chat chrome on the original views and their original handlers. */
final class ChatMd3 {
    private ChatMd3() {}
    private static final int SESSION = 0x7e0c0650, SAVED = 0x7e0c0651;
    private static final Map<String,Integer> ids = new HashMap<>();
    private static final WeakHashMap<ViewGroup,WeakReference<Session>> sessions = new WeakHashMap<>();

    static void screen(ViewGroup root, boolean on) {
        Object held = root.getTag(SESSION);
        Session session = held instanceof Session ? (Session)held : null;
        if (!on) { if (session != null) session.restore(); return; }
        if (!visible(root) || named(root,"dyq") == null || named(root,"u52") == null) return;
        if (session == null) {
            session = new Session(root);
            root.setTag(SESSION,session);
            sessions.put(root,new WeakReference<>(session));
        }
        session.update();
    }

    static void refresh() {
        boolean on = Md3.isOn(Md3.KEY_CHAT);
        for (Map.Entry<ViewGroup,WeakReference<Session>> entry : sessions.entrySet()) {
            Session session = entry.getValue().get();
            if (session == null) continue;
            if (!on) session.restore();
            else if (entry.getKey().isAttachedToWindow() && visible(entry.getKey())) session.update();
        }
    }

    private static final class Session {
        final WeakReference<ViewGroup> root;
        final ArrayList<WeakReference<View>> changed = new ArrayList<>();
        int page,ink,muted,surface,primary,onPrimary,quote;

        Session(ViewGroup root) { this.root = new WeakReference<>(root); }

        Saved keep(View view) {
            Object held = view.getTag(SAVED);
            if (held instanceof Saved) return (Saved)held;
            Saved saved = new Saved(view);
            view.setTag(SAVED,saved);
            changed.add(new WeakReference<>(view));
            return saved;
        }

        void update() {
            ViewGroup root = this.root.get();
            if (root == null) return;
            page = Md3.behind(root);ink = Md3.ink(page);
            muted = NavBar.mix(ink,page,.24f);
            surface = NavBar.mix(page,ink,.08f);
            quote = NavBar.mix(page,Accent.colour()|0xff000000,.13f);
            primary = NavBar.mix(Accent.colour()|0xff000000,NavBar.bright(page)<.5f?0xffffffff:page,.56f);
            onPrimary = Md3.ink(primary);
            header(named(root,"dyq"));
            input(named(root,"ui_slot_input_layout_redesign"));
            shortcuts(named(root,"a3n"));
            View list = named(root,"u52");
            if (list instanceof ViewGroup) {
                ViewGroup rows = (ViewGroup)list;
                for (int i=0;i<rows.getChildCount();i++) message(rows,rows.getChildAt(i));
            }
        }

        void header(View view) {
            if (!(view instanceof ViewGroup)) return;
            fill(view,NavBar.mix(page,ink,.025f),0);
            ViewGroup group=(ViewGroup)view;
            // The two native wrappers have a fixed 56 dp child. Resize the whole
            // chain together; the constraints continue to centre its contents.
            for(int i=0;i<3;i++) {
                keep(group);size(group,null,dp(group,64));
                if(group.getChildCount()!=1||!(group.getChildAt(0) instanceof ViewGroup))break;
                group=(ViewGroup)group.getChildAt(0);
            }
            View back=named(view,"n9k");
            if(back!=null){keep(back);size(back,dp(back,44),null);}
            View avatar=named(view,"lxd");
            if(avatar!=null){keep(avatar);size(avatar,dp(avatar,40),dp(avatar,40));}
            text(named(view,"ihm"),17,null,true,true);
            texts(named(view,"vn5"),12,muted,0);
            tint(named(view,"ptk"),ink);tint(named(view,"kmm"),ink);tint(named(view,"kik"),ink);
        }

        void input(View view) {
            if(view==null)return;
            View field=named(view,"lf9"),camera=named(view,"dhg"),actions=named(view,"lfc"),voice=named(view,"lfm");
            if(field==null)return;
            keep(field).own(field,Saved.MIN_HEIGHT,dp(field,52));
            Saved fieldState=keep(field);
            if(!(fieldState.applied instanceof Input))fieldState.applied=new Input(field,voice);
            Input background=(Input)fieldState.applied;background.tint(surface);fieldState.background(field);
            background.voice(voice);
            if(camera!=null){
                keep(camera);size(camera,dp(camera,44),dp(camera,44));fill(camera,surface,14);
                View parent=camera.getParent() instanceof View?(View)camera.getParent():null;
                if(parent!=null){keep(parent);size(parent,dp(camera,44),dp(camera,44));margin(parent,12,null);}
                button(named(camera,"tux_icon_view"),muted);
            }
            margin(field,8,12);
            EditText edit=edit(field,0);
            if(edit!=null){
                text(edit,16,ink,false,false);
                padding(edit,dp(edit,12),edit.getPaddingTop(),edit.getPaddingRight(),edit.getPaddingBottom());
                if(edit.getCurrentHintTextColor()!=muted)keep(edit).own(edit,Saved.HINT,ColorStateList.valueOf(muted));
                // Leave the native text, selection, input connection and height
                // constraints alone, including its multiline and voice modes.
            }
            if(actions instanceof ViewGroup){
                ViewGroup group=(ViewGroup)actions;
                for(int i=0;i<group.getChildCount();i++){
                    View child=group.getChildAt(i);
                    if(child instanceof ImageView)button(child,muted);
                }
            }
            View sticker=named(view,"kv2");button(sticker,muted);
            View stickerTarget=named(view,"lff");if(stickerTarget!=null){keep(stickerTarget);size(stickerTarget,dp(stickerTarget,44),dp(stickerTarget,44));}
            if(voice!=null){
                keep(voice);size(voice,dp(voice,44),dp(voice,44));fill(voice,primary,22);
                View touch=named(voice,"cvv");
                if(touch!=null){keep(touch);size(touch,dp(touch,44),dp(touch,44));}
                View nativeBack=named(voice,"u1y");
                if(nativeBack!=null){keep(nativeBack);size(nativeBack,dp(nativeBack,44),dp(nativeBack,44));fill(nativeBack,primary,22);}
                voiceIcons(voice,0);
            }
        }

        void voiceIcons(View view,int depth){
            if(depth>2)return;
            if(view instanceof ImageView){
                button(view,onPrimary);
                if(view.getLayoutParams() instanceof FrameLayout.LayoutParams){
                    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)view.getLayoutParams();
                    if(params.gravity!=Gravity.CENTER)keep(view).own(view,Saved.GRAVITY,Gravity.CENTER);
                }
            }else if(view instanceof ViewGroup){
                ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)voiceIcons(group.getChildAt(i),depth+1);
            }
        }

        void shortcuts(View view) {
            if(!(view instanceof ViewGroup))return;
            ViewGroup row=(ViewGroup)view;
            for(int i=0;i<row.getChildCount();i++){
                View chip=row.getChildAt(i);
                if(chip.getVisibility()!=View.VISIBLE)continue;
                texts(named(chip,"a3y"),12,muted,0);
                fill(chip,NavBar.mix(page,ink,.055f),10);
            }
        }

        void message(ViewGroup list,View row) {
            ChatBubbles.Match bubble=ChatBubbles.find(list,row);
            if(bubble!=null){
                int colour=bubble.outgoing?NavBar.mix(page,Accent.colour()|0xff000000,.32f):surface;
                fill(bubble.background,colour,18);keep(bubble.background).bubble=true;
            }else{
                unpaintBubble(named(row,"f5z"));unpaintBubble(named(row,"kwn"));
            }
            View media=named(row,"f7j");
            if(media!=null&&media.isShown()&&media.getClass().getName().endsWith("RoundingCornerLayoutCL")){
                fitMedia(media);rounded(media,20);
            }
            View share=named(row,"kso");
            if(share!=null&&share.isShown()){fill(share,surface,20);tint(named(share,"j0g"),muted);}
            View preview=named(row,"kv7");
            if(preview!=null&&preview.isShown()){
                View content=named(preview,"kv6");
                if(content!=null){fill(content,quote,14);rounded(content,14);}
                View stripe=named(preview,"kvb");if(stripe!=null)fill(stripe,primary,1);
                text(named(preview,"kvf"),13,null,false,true);
            }
            View time=named(row,"pdh");
            if(time instanceof TextView){text(time,11,muted,false,true);shortTime((TextView)time);}
        }

        void unpaintBubble(View view){
            if(view==null)return;Object held=view.getTag(SAVED);if(!(held instanceof Saved))return;
            Saved saved=(Saved)held;if(!saved.bubble)return;
            if(view.getBackground()==saved.applied)view.setBackground(saved.nativeBackground);
            saved.bubble=false;
        }

        void fitMedia(View view) {
            ViewGroup.LayoutParams params=view.getLayoutParams();
            if(params==null||params.width<=0||params.height<=0)return;
            Saved saved=keep(view);
            if(params.width!=saved.paintedMediaWidth||params.height!=saved.paintedMediaHeight){saved.mediaWidth=params.width;saved.mediaHeight=params.height;}
            int width=Math.min(saved.mediaWidth,dp(view,144));
            int height=Math.max(1,Math.round(saved.mediaHeight*(width/(float)saved.mediaWidth)));
            size(view,width,height);saved.paintedMediaWidth=width;saved.paintedMediaHeight=height;
        }

        void shortTime(TextView text) {
            Saved saved=keep(text);CharSequence value=text.getText();
            if(value==null)return;
            String now=value.toString();
            if(now.equals(saved.paintedWords))return;
            if(now.matches("[0-9]{1,2}:[0-9]{2}:[0-9]{2}")){
                saved.nativeWords=value;saved.paintedWords=now.substring(0,now.length()-3);text.setText(saved.paintedWords);
            }else{saved.nativeWords=null;saved.paintedWords=null;}
        }

        void text(View view,float sp,Integer colour,boolean bold,boolean single) {
            if(!(view instanceof TextView))return;
            TextView text=(TextView)view;Saved saved=keep(text);
            float wanted=sp*text.getResources().getDisplayMetrics().scaledDensity;
            if(Math.abs(text.getTextSize()-wanted)>.5f)saved.own(text,Saved.TEXT_SIZE,wanted);
            if(colour!=null)saved.ink(text,colour);
            if(bold&&(text.getTypeface()==null||!text.getTypeface().isBold()))saved.own(text,Saved.TYPEFACE,Typeface.create(text.getTypeface(),Typeface.BOLD));
            if(single){saved.own(text,Saved.LINES,1);saved.own(text,Saved.ELLIPSIZE,TextUtils.TruncateAt.END);}
        }

        void texts(View view,float sp,int colour,int depth) {
            if(view==null||depth>3)return;
            if(view instanceof TextView)text(view,sp,colour,false,true);
            else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)texts(group.getChildAt(i),sp,colour,depth+1);}
        }

        void fill(View view,int colour,float radius) {
            Saved saved=keep(view);
            if(saved.shell==null){
                saved.shell=new Shell();
                saved.applied=view.isClickable()?new RippleDrawable(ColorStateList.valueOf((Accent.colour()&0xffffff)|0x22000000),saved.shell,null):saved.shell;
            }
            saved.shell.tint(colour,dp(view,radius));saved.background(view);
        }

        void tint(View view,int colour) {
            if(!(view instanceof ImageView))return;
            ImageView image=(ImageView)view;Saved saved=keep(view);
            if(image.getColorFilter()!=saved.paintedFilter||saved.filterColour!=colour){
                if(image.getColorFilter()!=saved.paintedFilter)saved.nativeFilter=image.getColorFilter();
                image.setColorFilter(colour,PorterDuff.Mode.SRC_IN);saved.paintedFilter=image.getColorFilter();saved.filterColour=colour;
            }
        }

        void button(View view,int colour) {
            if(!(view instanceof ImageView))return;
            keep(view);size(view,dp(view,44),dp(view,44));
            padding(view,dp(view,10),dp(view,10),dp(view,10),dp(view,10));tint(view,colour);
        }

        void rounded(View view,float radius) {
            Saved saved=keep(view);
            if(!(view.getOutlineProvider() instanceof Rounded))saved.own(view,Saved.OUTLINE,new Rounded(dp(view,radius)));
            saved.own(view,Saved.CLIP,true);
        }

        void margin(View view,Integer start,Integer end) {
            if(!(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams))return;
            Saved saved=keep(view);
            if(start!=null){saved.own(view,Saved.MARGIN_START,dp(view,start));saved.own(view,Saved.MARGIN_LEFT,dp(view,start));}
            if(end!=null){saved.own(view,Saved.MARGIN_END,dp(view,end));saved.own(view,Saved.MARGIN_RIGHT,dp(view,end));}
        }

        void restore() {
            for(WeakReference<View> held:changed){View view=held.get();if(view==null)continue;Object saved=view.getTag(SAVED);if(saved instanceof Saved)((Saved)saved).restore(view);view.setTag(SAVED,null);}
            changed.clear();
        }
    }

    private static final class Rounded extends ViewOutlineProvider {
        final float radius;Rounded(float radius){this.radius=radius;}
        @Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),radius);}
    }

    private static final class Shell extends GradientDrawable {
        int colour;float radius=-1;
        void tint(int colour,float radius){if(this.colour!=colour){this.colour=colour;setColor(colour);}if(this.radius!=radius){this.radius=radius;setCornerRadius(radius);}}
    }

    /** The microphone remains a native child of the field. Paint the field up
     * to that button, keeping the original input and recording touch routes. */
    private static final class Input extends Drawable {
        final WeakReference<View> field;WeakReference<View> voice;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box=new RectF();final int[] at=new int[2],base=new int[2];
        Input(View field,View voice){this.field=new WeakReference<>(field);this.voice=new WeakReference<>(voice);}
        void voice(View view){if(voice.get()!=view){voice=new WeakReference<>(view);invalidateSelf();}}
        void tint(int colour){if(paint.getColor()!=colour){paint.setColor(colour);invalidateSelf();}}
        @Override public void draw(Canvas canvas){
            View field=this.field.get(),voice=this.voice.get();
            box.set(getBounds());
            if(field!=null&&voice!=null&&voice.isShown()&&voice.getWidth()>0){
                field.getLocationInWindow(base);voice.getLocationInWindow(at);
                if(field.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL)box.left=Math.min(box.right,at[0]-base[0]+voice.getWidth()+dp(field,8));
                else box.right=Math.max(box.left,at[0]-base[0]-dp(field,8));
            }
            float radius=field==null?22:dp(field,22);canvas.drawRoundRect(box,radius,radius,paint);
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }

    private static final class Saved {
        static final int WIDTH=0,HEIGHT=1,MIN_HEIGHT=2,PAD_LEFT=3,PAD_TOP=4,PAD_RIGHT=5,PAD_BOTTOM=6,
                MARGIN_LEFT=7,MARGIN_RIGHT=8,MARGIN_START=9,MARGIN_END=10,GRAVITY=11,
                OUTLINE=12,CLIP=13,TEXT_SIZE=14,LINES=15,TYPEFACE=16,ELLIPSIZE=17,HINT=18;
        int owned;
        Object[] before,painted;
        Drawable nativeBackground,applied;
        Shell shell;boolean bubble;
        ColorStateList nativeInk;Integer paintedInk;
        ColorFilter nativeFilter,paintedFilter;int filterColour;
        CharSequence nativeWords;String paintedWords;
        int mediaWidth,mediaHeight,paintedMediaWidth=-1,paintedMediaHeight=-1;

        Saved(View view){
            nativeBackground=view.getBackground();
            TextView text=view instanceof TextView?(TextView)view:null;
            nativeInk=text==null?null:text.getTextColors();
            nativeFilter=view instanceof ImageView?((ImageView)view).getColorFilter():null;
        }
        void own(View view,int property,Object value){
            Object current=read(view,property);
            if(Objects.equals(current,value))return;
            if(before==null){before=new Object[19];painted=new Object[19];}
            if((owned&(1<<property))==0||!Objects.equals(current,painted[property]))before[property]=current;
            write(view,property,value);painted[property]=read(view,property);owned|=1<<property;
        }
        static Object read(View v,int property){
            ViewGroup.LayoutParams p=v.getLayoutParams();
            switch(property){
                case WIDTH:return p.width;case HEIGHT:return p.height;case MIN_HEIGHT:return v.getMinimumHeight();
                case PAD_LEFT:return v.getPaddingLeft();case PAD_TOP:return v.getPaddingTop();case PAD_RIGHT:return v.getPaddingRight();case PAD_BOTTOM:return v.getPaddingBottom();
                case MARGIN_LEFT:return((ViewGroup.MarginLayoutParams)p).leftMargin;case MARGIN_RIGHT:return((ViewGroup.MarginLayoutParams)p).rightMargin;
                case MARGIN_START:return((ViewGroup.MarginLayoutParams)p).getMarginStart();case MARGIN_END:return((ViewGroup.MarginLayoutParams)p).getMarginEnd();
                case GRAVITY:return((FrameLayout.LayoutParams)p).gravity;case OUTLINE:return v.getOutlineProvider();case CLIP:return v.getClipToOutline();
                case TEXT_SIZE:return((TextView)v).getTextSize();case LINES:return((TextView)v).getMaxLines();case TYPEFACE:return((TextView)v).getTypeface();
                case ELLIPSIZE:return((TextView)v).getEllipsize();case HINT:return((TextView)v).getHintTextColors();default:throw new IllegalArgumentException();
            }
        }
        static void write(View v,int property,Object value){
            ViewGroup.LayoutParams p=v.getLayoutParams();
            switch(property){
                case WIDTH:p.width=(Integer)value;v.setLayoutParams(p);break;case HEIGHT:p.height=(Integer)value;v.setLayoutParams(p);break;
                case MIN_HEIGHT:v.setMinimumHeight((Integer)value);break;
                case PAD_LEFT:v.setPadding((Integer)value,v.getPaddingTop(),v.getPaddingRight(),v.getPaddingBottom());break;
                case PAD_TOP:v.setPadding(v.getPaddingLeft(),(Integer)value,v.getPaddingRight(),v.getPaddingBottom());break;
                case PAD_RIGHT:v.setPadding(v.getPaddingLeft(),v.getPaddingTop(),(Integer)value,v.getPaddingBottom());break;
                case PAD_BOTTOM:v.setPadding(v.getPaddingLeft(),v.getPaddingTop(),v.getPaddingRight(),(Integer)value);break;
                case MARGIN_LEFT:((ViewGroup.MarginLayoutParams)p).leftMargin=(Integer)value;v.setLayoutParams(p);break;
                case MARGIN_RIGHT:((ViewGroup.MarginLayoutParams)p).rightMargin=(Integer)value;v.setLayoutParams(p);break;
                case MARGIN_START:((ViewGroup.MarginLayoutParams)p).setMarginStart((Integer)value);v.setLayoutParams(p);break;
                case MARGIN_END:((ViewGroup.MarginLayoutParams)p).setMarginEnd((Integer)value);v.setLayoutParams(p);break;
                case GRAVITY:((FrameLayout.LayoutParams)p).gravity=(Integer)value;v.setLayoutParams(p);break;
                case OUTLINE:v.setOutlineProvider((ViewOutlineProvider)value);break;case CLIP:v.setClipToOutline((Boolean)value);break;
                case TEXT_SIZE:((TextView)v).setTextSize(TypedValue.COMPLEX_UNIT_PX,(Float)value);break;
                case LINES:((TextView)v).setMaxLines((Integer)value);break;case TYPEFACE:((TextView)v).setTypeface((Typeface)value);break;
                case ELLIPSIZE:((TextView)v).setEllipsize((TextUtils.TruncateAt)value);break;case HINT:((TextView)v).setHintTextColor((ColorStateList)value);break;
                default:throw new IllegalArgumentException();
            }
        }
        void background(View view){if(view.getBackground()!=applied){nativeBackground=view.getBackground();view.setBackground(applied);}}
        void ink(TextView text,int colour){
            if(paintedInk==null||text.getCurrentTextColor()!=paintedInk)nativeInk=text.getTextColors();
            if(text.getCurrentTextColor()!=colour)text.setTextColor(colour);paintedInk=colour;
        }
        void restore(View view){
            for(int property=0;property<19;property++)if((owned&(1<<property))!=0&&Objects.equals(read(view,property),painted[property]))write(view,property,before[property]);
            if(view.getBackground()==applied)view.setBackground(nativeBackground);
            if(view instanceof ImageView&&((ImageView)view).getColorFilter()==paintedFilter)((ImageView)view).setColorFilter(nativeFilter);
            if(view instanceof TextView){
                TextView text=(TextView)view;
                if(paintedInk!=null&&text.getCurrentTextColor()==paintedInk)text.setTextColor(nativeInk);
                if(paintedWords!=null&&nativeWords!=null&&paintedWords.contentEquals(text.getText()))text.setText(nativeWords);
            }
        }
    }

    private static void size(View view,Integer width,Integer height){if(view.getLayoutParams()==null)return;Saved saved=(Saved)view.getTag(SAVED);if(width!=null)saved.own(view,Saved.WIDTH,width);if(height!=null)saved.own(view,Saved.HEIGHT,height);}
    private static void padding(View view,int l,int t,int r,int b){Saved saved=(Saved)view.getTag(SAVED);saved.own(view,Saved.PAD_LEFT,l);saved.own(view,Saved.PAD_TOP,t);saved.own(view,Saved.PAD_RIGHT,r);saved.own(view,Saved.PAD_BOTTOM,b);}
    private static EditText edit(View view,int depth){if(view instanceof EditText)return(EditText)view;if(depth<4&&view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){EditText found=edit(group.getChildAt(i),depth+1);if(found!=null)return found;}}return null;}
    private static boolean visible(View view){return ProfileLayout.onScreen(view,view.getRootView());}
    private static int dp(View view,float value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
    private static View named(View root,String name){if(root==null)return null;Integer id=ids.get(name);if(id==null){id=root.getResources().getIdentifier(name,"id",root.getContext().getPackageName());if(id!=0)ids.put(name,id);}return id==0?null:root.findViewById(id);}
}

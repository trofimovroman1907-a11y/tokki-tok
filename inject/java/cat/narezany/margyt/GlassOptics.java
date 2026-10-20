package cat.narezany.margyt;

import android.graphics.*;
import android.os.Build;
import android.view.View;

/** QWEA0's optical model with reusable shader state and one backdrop per window. */
final class GlassOptics {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF local = new RectF();
    private final Matrix transform = new Matrix();
    private final int[] point = new int[2];
    private Lens lens;
    private boolean failed;
    private Integer surfaceColour;

    void draw(Canvas canvas, View view, RectF bounds, float top, float bottom, int colour, float press) {
        float density = view.getResources().getDisplayMetrics().density;
        if (Color.alpha(colour) < 255) {
            if (surfaceColour == null) surfaceColour = Skin.remembered(view.getContext()).card;
            float alpha = Color.alpha(colour) / 255f;
            colour = Color.rgb(Math.round(Color.red(colour)*alpha+Color.red(surfaceColour)*(1-alpha)),
                    Math.round(Color.green(colour)*alpha+Color.green(surfaceColour)*(1-alpha)),
                    Math.round(Color.blue(colour)*alpha+Color.blue(surfaceColour)*(1-alpha)));
        }
        int save = canvas.save();
        canvas.translate(bounds.left,bounds.top);
        local.set(0,0,bounds.width(),bounds.height());
        top=Math.min(top,local.height()/2f); bottom=Math.min(bottom,local.height()/2f);
        if(Design.fluent()){top=Math.min(top,density*8);bottom=Math.min(bottom,density*8);}
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated() && !failed) {
            try {
                if (lens == null) lens = new Lens();
                GlassBackdrop.Frame frame = GlassBackdrop.frame(view);
                if (frame != null && frame.ready && frame.bitmap != null) {
                    view.getLocationOnScreen(point);
                    float x=point[0]-frame.x+bounds.left, y=point[1]-frame.y+bounds.top;
                    lens.paint(canvas,frame,transform,x,y,local.width(),local.height(),top,bottom,colour,press,density);
                    canvas.restoreToCount(save);
                    return;
                }
            } catch (Throwable error) {
                failed = true;
                Diary.note("liquid glass shader: " + error);
                android.util.Log.e("MargyTGlass", "Optical surface could not be rendered", error);
            }
        }
        // Older devices still get the same controls, readable tint and directional rim.
        paint.setShader(null); paint.setStyle(Paint.Style.FILL);
        paint.setColor((colour & 0xffffff) | (Design.fluent()?0x30000000:0x18000000));
        canvas.drawRoundRect(local,top,top,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density*.65f));
        paint.setShader(new LinearGradient(0,0,local.width(),local.height(),Design.fluent()
                ?new int[]{0x30ffffff,0x14ffffff}:new int[]{0x90ffffff,0x08ffffff,0x65ffffff},null,Shader.TileMode.CLAMP));
        local.inset(density*.4f,density*.4f);canvas.drawRoundRect(local,top,top,paint);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    /** Kept in a separate class so Android 6–12 never loads RuntimeShader. */
    private static final class Lens {
        private static final String BLUR="uniform shader content;uniform float2 axis;half4 main(float2 p){return content.eval(p)*.4026+(content.eval(p+axis)+content.eval(p-axis))*.2442+(content.eval(p+axis*2.)+content.eval(p-axis*2.))*.0545;}";
        private RuntimeShader shader;
        private RuntimeShader acrylic;
        private final RuntimeShader blurX=new RuntimeShader(BLUR),blurY=new RuntimeShader(BLUR);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] hsv=new float[3];
        private Bitmap bound;
        private BitmapShader input;
        void paint(Canvas canvas, GlassBackdrop.Frame frame, Matrix matrix, float x,float y,
                   float w,float h,float top,float bottom,int colour,float press,float dp) {
            if (bound != frame.bitmap) {
                bound=frame.bitmap;
                input=new BitmapShader(bound,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
            }
            matrix.setScale(1/frame.scale,1/frame.scale);matrix.postTranslate(-x,-y);
            input.setLocalMatrix(matrix);
            // Fluent deliberately softens only slightly; Liquid Glass keeps its own optics.
            float blur=dp*(Design.fluent()?.85f:1.1f);
            blurX.setInputShader("content",input);blurX.setFloatUniform("axis",blur,0);
            blurY.setInputShader("content",blurX);blurY.setFloatUniform("axis",0,blur);
            if(Design.fluent()){
                if(acrylic==null)acrylic=new RuntimeShader(FluentShader.ACRYLIC);
                acrylic.setInputShader("content",blurY);
                acrylic.setFloatUniform("size",w,h);
                acrylic.setFloatUniform("radii",Math.min(top,dp*8),Math.min(bottom,dp*8));
                acrylic.setFloatUniform("tint",Color.red(colour)/255f,Color.green(colour)/255f,Color.blue(colour)/255f,.24f);
                acrylic.setFloatUniform("density",dp);acrylic.setFloatUniform("pressed",press);
                paint.setShader(acrylic);canvas.drawRect(0,0,w,h,paint);return;
            }
            if(shader==null)shader=new RuntimeShader(GlassShader.LENS);
            shader.setInputShader("content",blurY);
            shader.setFloatUniform("margin",0);
            shader.setFloatUniform("viewSize",w,h);
            shader.setFloatUniform("shape1",w/2,h/2,w/2,h/2);
            shader.setFloatUniform("shape1L",w/2,h/2,w/2,h/2);
            shader.setFloatUniform("radii1",top,top,bottom,bottom);
            shader.setFloatUniform("shape2",0,0,0,0);shader.setFloatUniform("radius2",0);shader.setFloatUniform("blendK",0);
            shader.setFloatUniform("bevel",Math.max(dp*3,Math.min(dp*22,h*.27f)));
            shader.setFloatUniform("refractPx",Math.min(dp*25,h*.42f));
            shader.setFloatUniform("falloff",2);shader.setFloatUniform("refractDir",-1);
            shader.setFloatUniform("rimSoft",dp*.7f);
            shader.setFloatUniform("sampleLo",-x,-y);
            shader.setFloatUniform("sampleHi",frame.bitmap.getWidth()/frame.scale-x,frame.bitmap.getHeight()/frame.scale-y);
            shader.setFloatUniform("dispersion",.065f);
            float tx=viewTouchX,ty=viewTouchY;
            shader.setFloatUniform("lightDir",-.7071f+tx*.16f,-.7071f+ty*.16f);
            shader.setFloatUniform("specStrength",.85f+press*.35f);
            shader.setFloatUniform("rimBandMax",Math.max(2,Math.min(dp*4,h*.12f)));
            boolean dark=NavBar.bright(colour)<.5f;
            shader.setFloatUniform("tintColor",dark?.10f:.96f,dark?.11f:.97f,dark?.13f:.99f,.08f);
            shader.setFloatUniform("adaptiveTint",0);
            Color.colorToHSV(colour,hsv);
            shader.setFloatUniform("glassTint",Color.red(colour)/255f,Color.green(colour)/255f,Color.blue(colour)/255f,hsv[1]>.28f?.10f:.025f);
            shader.setFloatUniform("dimAmount",0);shader.setFloatUniform("satFactor",1.12f);
            shader.setFloatUniform("press",press);
            shader.setFloatUniform("touchPos",w*(.5f+tx*.5f),h*(.5f+ty*.5f));
            shader.setFloatUniform("touchAmp",press*.75f);
            paint.setShader(shader);canvas.drawRect(0,0,w,h,paint);
        }
    }
    static float viewTouchX,viewTouchY;
}

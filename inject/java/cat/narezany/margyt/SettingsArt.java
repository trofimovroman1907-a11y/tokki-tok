package cat.narezany.margyt;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Pictures at the top of a setting that show what it does, the way a phone's
 * own settings do: a phone with TikTok's feed on it for the screen saver and
 * the simple feed, a conversation with narezany for the streak sticker, a
 * profile for the avatar shape. Drawn here, so they follow the setting live
 * and need nothing from the network.
 *
 * Each remembers how it was last drawn, so a picture made again after the
 * settings were rebuilt moves from there to the new state instead of jumping.
 */
final class SettingsArt {
    private SettingsArt() {}

    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);

    static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    /** One number moved over a few hundred milliseconds, redrawing as it goes. */
    private static final class Eased {
        float value;
        private ValueAnimator animator;

        Eased(float value) { this.value = value; }

        void to(final View view, float target, long duration, android.animation.TimeInterpolator curve) {
            if (animator != null) animator.cancel();
            if (!Motion.enabled(view.getContext()) || Math.abs(target - value) < 0.001f) {
                value = target;
                view.invalidate();
                return;
            }
            animator = ValueAnimator.ofFloat(value, target);
            animator.setDuration(duration);
            animator.setInterpolator(curve);
            animator.addUpdateListener(a -> {
                value = (Float) a.getAnimatedValue();
                view.invalidate();
            });
            animator.start();
        }
    }

    // ================================================================ phone

    /**
     * TikTok's feed on a phone: a video, the bar across the top with LIVE on
     * the left, the buttons down the right, the caption and the bottom bar.
     */
    static final class Phone extends View {
        private static float lastUi = 1f, lastEye = 0f, lastBar = 1f, lastSimple = 0f;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF box = new RectF();
        private final Path path = new Path();
        private final Eased ui = new Eased(lastUi), eye = new Eased(lastEye), bar = new Eased(lastBar),
                simple = new Eased(lastSimple);
        private float wantUi = 1f, wantEye = 0f, wantBar = 1f, wantSimple = 0f;
        private Bitmap avatar;

        Phone(Context context, Skin skin) {
            super(context);
            this.skin = skin;
            avatar = Launcher.preview("tilcayo");
            setMinimumHeight(Math.round(dp(context, 250)));
        }

        /** The screen saver: on or off, how faint it makes things, and whether the bottom bar stays. */
        Phone burn(boolean on, int strength, boolean keepBar) {
            float faint = on ? Math.max(0.06f, 1f - strength / 100f) : 1f;
            wantUi = faint;
            wantEye = on ? 1f : 0f;
            wantBar = on ? (keepBar ? faint : 0f) : 1f;
            return this;
        }

        /** The simple feed: the tabs, the avatar, the sound and the record go, and the rest gets smaller. */
        Phone simple(boolean on) {
            wantSimple = on ? 1f : 0f;
            return this;
        }

        /** Move to what was last asked for. */
        void settle() {
            ui.to(this, wantUi, 320, EMPHASIZED);
            bar.to(this, wantBar, 320, EMPHASIZED);
            simple.to(this, wantSimple, 360, EMPHASIZED);
            eye.to(this, wantEye, 420, new OvershootInterpolator(2.2f));
            lastUi = wantUi;
            lastEye = wantEye;
            lastBar = wantBar;
            lastSimple = wantSimple;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 250)));
        }

        private float u;   // a hundredth of the screen's width

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float height = getHeight() - 28 * d;
            float width = height * 0.49f;
            float left = (getWidth() - width) / 2f, top = 14 * d;
            // the phone
            box.set(left, top, left + width, top + height);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Expressive.mix(skin.card, skin.text, 0.18f));
            canvas.drawRoundRect(box, width * 0.15f, width * 0.15f, paint);
            box.inset(3 * d, 3 * d);
            float screenLeft = box.left, screenTop = box.top, w = box.width(), h = box.height();
            u = w / 100f;
            canvas.save();
            path.reset();
            path.addRoundRect(box, width * 0.13f, width * 0.13f, Path.Direction.CW);
            canvas.clipPath(path);
            video(canvas, box);
            canvas.translate(screenLeft, screenTop);
            float s = simple.value;
            int faint = Math.round(255 * ui.value);
            top(canvas, w, faint, s);
            rail(canvas, w, h, faint, s);
            caption(canvas, w, h, faint, s);
            nav(canvas, w, h, Math.round(255 * bar.value));
            canvas.restore();
        }

        /** The video: a dark picture with two soft lights in it, never dimmed. */
        private void video(Canvas canvas, RectF screen) {
            int accent = Accent.colour();
            paint.setShader(new LinearGradient(0, screen.top, 0, screen.bottom,
                    Expressive.mix(0xFF1B1530, accent, 0.25f), 0xFF0B1418, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(new RadialGradient(screen.left + screen.width() * 0.3f, screen.top + screen.height() * 0.38f,
                    screen.width() * 0.55f, (Expressive.mix(accent, 0xFFFFFFFF, 0.2f) & 0xFFFFFF) | 0x66000000, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(new RadialGradient(screen.left + screen.width() * 0.75f, screen.top + screen.height() * 0.62f,
                    screen.width() * 0.45f, 0x5525C9D6, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(screen, paint);
            paint.setShader(null);
        }

        private void white(int alpha) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int alpha, boolean bold, Paint.Align align) {
            text.setColor(Color.argb(alpha, 255, 255, 255));
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            text.setTextAlign(align);
            canvas.drawText(said, x, y, text);
        }

        private void top(Canvas canvas, float w, int alpha, float s) {
            float y = 13 * u;
            // LIVE, or the eye that takes its place
            float e = eye.value;
            if (e < 0.999f) {
                int liveAlpha = Math.round(alpha * (1f - Math.min(1f, e)));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f * u);
                paint.setColor(Color.argb(liveAlpha, 255, 255, 255));
                box.set(5 * u, y - 4 * u, 17 * u, y + 4 * u);
                canvas.drawRoundRect(box, 2 * u, 2 * u, paint);
                words(canvas, "LIVE", 11 * u, y + 1.8f * u, 4.6f * u, liveAlpha, true, Paint.Align.CENTER);
            }
            if (e > 0.001f) eyeAt(canvas, 11 * u, y, 6.5f * u * Math.max(0f, e));
            // the two tabs, gone in the simple feed
            int tabs = Math.round(alpha * (1f - s));
            if (tabs > 0) {
                words(canvas, Local.of("Подписки"), 40 * u, y + 2 * u, 5.6f * u, tabs * 160 / 255, false, Paint.Align.CENTER);
                words(canvas, Local.of("Для вас"), 64 * u, y + 2 * u, 5.6f * u, tabs, true, Paint.Align.CENTER);
                white(tabs);
                box.set(60 * u, y + 4.5f * u, 68 * u, y + 5.5f * u);
                canvas.drawRoundRect(box, u, u, paint);
            }
            // search stays
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.4f * u);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(89 * u, y - 0.5f * u, 3.2f * u, paint);
            canvas.drawLine(91.4f * u, y + 1.9f * u, 93.5f * u, y + 4f * u, paint);
        }

        /** The eye the screen saver puts where LIVE was: always clear, it is what turns it off. */
        private void eyeAt(Canvas canvas, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            path.reset();
            path.moveTo(cx - r, cy);
            path.quadTo(cx, cy - r * 1.05f, cx + r, cy);
            path.quadTo(cx, cy + r * 1.05f, cx - r, cy);
            path.close();
            canvas.drawPath(path, paint);
            paint.setColor(Accent.colour());
            canvas.drawCircle(cx, cy, r * 0.42f, paint);
            paint.setColor(0xFF111111);
            canvas.drawCircle(cx, cy, r * 0.2f, paint);
        }

        private void rail(Canvas canvas, float w, float h, int alpha, float s) {
            float x = 88 * u;
            float scale = 1f - 0.18f * s;
            float y = h * 0.45f;
            float step = 15.5f * u * scale;
            // the author's picture and the follow button, gone in the simple feed
            int author = Math.round(alpha * (1f - s));
            if (author > 0) {
                float r = 5.6f * u;
                if (avatar != null) {
                    paint.setShader(new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                    Matrix m = new Matrix();
                    m.setRectToRect(new RectF(0, 0, avatar.getWidth(), avatar.getHeight()),
                            new RectF(x - r, y - step - r, x + r, y - step + r), Matrix.ScaleToFit.FILL);
                    paint.getShader().setLocalMatrix(m);
                    paint.setAlpha(author);
                    canvas.drawCircle(x, y - step, r, paint);
                    paint.setShader(null);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(0.8f * u);
                paint.setColor(Color.argb(author, 255, 255, 255));
                canvas.drawCircle(x, y - step, r, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor((Accent.colour() & 0xFFFFFF) | (author << 24));
                canvas.drawCircle(x, y - step + r, 2.2f * u, paint);
            }
            String[] counts = {"12K", "345", "1K", "89"};
            for (int i = 0; i < 4; i++) {
                float cy = y + i * step + (s * -step * 0.5f);
                white(alpha);
                float r = 3.6f * u * scale;
                switch (i) {
                    case 0: heart(canvas, x, cy, r); break;
                    case 1: bubble(canvas, x, cy, r); break;
                    case 2: bookmark(canvas, x, cy, r); break;
                    default: share(canvas, x, cy, r); break;
                }
                words(canvas, counts[i], x, cy + r + 4.6f * u * scale, 3.8f * u * scale, alpha, true, Paint.Align.CENTER);
            }
            // the record that turns, gone in the simple feed
            int record = Math.round(alpha * (1f - s));
            if (record > 0) {
                float cy = h * 0.83f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(record, 40, 40, 40));
                canvas.drawCircle(x, cy, 4.8f * u, paint);
                paint.setColor(Color.argb(record, 200, 200, 200));
                canvas.drawCircle(x, cy, 1.8f * u, paint);
            }
        }

        private void heart(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx, cy + r);
            path.cubicTo(cx - r * 1.9f, cy - r * 0.2f, cx - r * 0.7f, cy - r * 1.5f, cx, cy - r * 0.45f);
            path.cubicTo(cx + r * 0.7f, cy - r * 1.5f, cx + r * 1.9f, cy - r * 0.2f, cx, cy + r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void bubble(Canvas canvas, float cx, float cy, float r) {
            box.set(cx - r * 1.2f, cy - r, cx + r * 1.2f, cy + r * 0.8f);
            canvas.drawOval(box, paint);
            path.reset();
            path.moveTo(cx - r * 0.5f, cy + r * 0.5f);
            path.lineTo(cx - r * 0.9f, cy + r * 1.25f);
            path.lineTo(cx + r * 0.1f, cy + r * 0.7f);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void bookmark(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx - r * 0.85f, cy - r);
            path.lineTo(cx + r * 0.85f, cy - r);
            path.lineTo(cx + r * 0.85f, cy + r);
            path.lineTo(cx, cy + r * 0.45f);
            path.lineTo(cx - r * 0.85f, cy + r);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void share(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx + r, cy - r * 0.1f);
            path.lineTo(cx + r * 0.05f, cy - r);
            path.lineTo(cx + r * 0.05f, cy - r * 0.5f);
            path.cubicTo(cx - r * 0.9f, cy - r * 0.4f, cx - r * 1.1f, cy + r * 0.4f, cx - r, cy + r);
            path.cubicTo(cx - r * 0.6f, cy + r * 0.2f, cx - r * 0.2f, cy + r * 0.25f, cx + r * 0.05f, cy + r * 0.25f);
            path.lineTo(cx + r * 0.05f, cy + r * 0.8f);
            path.close();
            canvas.drawPath(path, paint);
        }

        private void caption(Canvas canvas, float w, float h, int alpha, float s) {
            float y = h * (0.74f + 0.03f * s);
            words(canvas, "@narezany", 5 * u, y, 5f * u, alpha, true, Paint.Align.LEFT);
            white(alpha * 210 / 255);
            float line = 2.2f * u * (1f - 0.15f * s);
            box.set(5 * u, y + 3 * u, 62 * u, y + 3 * u + line);
            canvas.drawRoundRect(box, line, line, paint);
            box.set(5 * u, y + 7 * u, 46 * u, y + 7 * u + line);
            canvas.drawRoundRect(box, line, line, paint);
            if (s < 0.999f) {
                // a third line and the sound, which the simple feed leaves out
                int gone = Math.round(alpha * (1f - s));
                white(gone * 210 / 255);
                box.set(5 * u, y + 11 * u, 54 * u, y + 11 * u + line);
                canvas.drawRoundRect(box, line, line, paint);
                words(canvas, "♫  Margy — original sound", 5 * u, y + 18.5f * u, 3.8f * u, gone, false, Paint.Align.LEFT);
            }
        }

        private void nav(Canvas canvas, float w, float h, int alpha) {
            if (alpha <= 0) return;
            float top = h - 13 * u;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(alpha, 0, 0, 0));
            canvas.drawRect(0, top, w, h, paint);
            float y = top + 6 * u;
            for (int i = 0; i < 5; i++) {
                float x = w * (0.1f + 0.2f * i);
                if (i == 2) {
                    paint.setColor(Color.argb(alpha, 0x25, 0xF4, 0xEE));
                    box.set(x - 6.3f * u, y - 3.6f * u, x + 4.7f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    paint.setColor((Accent.colour() & 0xFFFFFF) | (alpha << 24));
                    box.set(x - 4.7f * u, y - 3.6f * u, x + 6.3f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    white(alpha);
                    box.set(x - 5.5f * u, y - 3.6f * u, x + 5.5f * u, y + 3.6f * u);
                    canvas.drawRoundRect(box, 1.6f * u, 1.6f * u, paint);
                    paint.setColor(Color.argb(alpha, 0, 0, 0));
                    canvas.drawRect(x - 2.4f * u, y - 0.45f * u, x + 2.4f * u, y + 0.45f * u, paint);
                    canvas.drawRect(x - 0.45f * u, y - 2.4f * u, x + 0.45f * u, y + 2.4f * u, paint);
                } else {
                    white(i == 0 ? alpha : alpha * 170 / 255);
                    canvas.drawCircle(x, y, 2.3f * u, paint);
                }
            }
        }
    }

    // ================================================================= chat

    /** A conversation with narezany: the sticker the streak sends, sent, when the streak is on. */
    static final class Chat extends View {
        private static float lastShown = 0f;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final RectF box = new RectF();
        private final Path path = new Path();
        private final Eased shown = new Eased(lastShown);
        private float want;
        private Bitmap sticker, avatar;

        Chat(Context context, Skin skin) {
            super(context);
            this.skin = skin;
            avatar = Launcher.preview("tilcayo");
        }

        Chat state(boolean on, Bitmap sticker) {
            want = on ? 1f : 0f;
            this.sticker = sticker;
            invalidate();
            return this;
        }

        void settle() {
            shown.to(this, want, 460, want > 0 ? new OvershootInterpolator(1.6f) : EMPHASIZED);
            lastShown = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            settle();
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 200)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float left = 16 * d, right = getWidth() - 16 * d, top = 14 * d, bottom = getHeight() - 14 * d;
            box.set(left, top, right, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(skin.page);
            canvas.drawRoundRect(box, 20 * d, 20 * d, paint);
            // the head of the conversation
            float hy = top + 26 * d;
            if (avatar != null) round(canvas, avatar, left + 30 * d, hy, 14 * d, 255);
            words(canvas, "narezany", left + 52 * d, hy - 1 * d, 15 * d, skin.text, true);
            words(canvas, "🔥 128", left + 52 * d, hy + 15 * d, 12 * d, skin.muted(), false);
            paint.setColor(Expressive.mix(skin.page, skin.text, 0.1f));
            canvas.drawRect(left, top + 50 * d, right, top + 50 * d + Math.max(1, d), paint);
            // what narezany wrote
            float by = top + 76 * d;
            String said = Local.of("продлеваем? 🔥");
            text.setTextSize(14 * d);
            text.setTypeface(Typeface.DEFAULT);
            float tw = text.measureText(said);
            box.set(left + 14 * d, by - 16 * d, left + 14 * d + tw + 24 * d, by + 12 * d);
            paint.setColor(Expressive.mix(skin.page, skin.text, 0.09f));
            canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
            words(canvas, said, left + 26 * d, by + 3 * d, 14 * d, skin.text, false);
            // the sticker, sent by the mod
            float size = 64 * d, cx = right - 18 * d - size / 2f, cy = bottom - 16 * d - size / 2f;
            float t = shown.value;
            if (t < 0.98f) {
                // where it will go: a dashed outline, while the streak is off
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f * d);
                paint.setPathEffect(new DashPathEffect(new float[] {6 * d, 5 * d}, 0));
                paint.setColor((skin.muted() & 0xFFFFFF) | (Math.round(140 * (1f - Math.min(1f, t))) << 24));
                box.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
                canvas.drawRoundRect(box, 14 * d, 14 * d, paint);
                paint.setPathEffect(null);
                paint.setStyle(Paint.Style.FILL);
            }
            if (t > 0.01f) {
                canvas.save();
                float scale = 0.5f + 0.5f * t;
                canvas.scale(scale, scale, cx, cy);
                int alpha = Math.round(255 * Math.min(1f, t));
                if (sticker != null) {
                    paint.setAlpha(alpha);
                    box.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
                    canvas.drawBitmap(sticker, null, box, paint);
                    paint.setAlpha(255);
                } else {
                    text.setTextAlign(Paint.Align.CENTER);
                    text.setTextSize(44 * d);
                    text.setColor(Color.argb(alpha, 0, 0, 0));
                    canvas.drawText("🔥", cx, cy + 15 * d, text);
                    text.setTextAlign(Paint.Align.LEFT);
                }
                canvas.restore();
                text.setTextAlign(Paint.Align.RIGHT);
                words(canvas, "MargyT", cx + size / 2f, cy + size / 2f + 13 * d, 10 * d,
                        (skin.muted() & 0xFFFFFF) | (alpha << 24), false);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int colour, boolean bold) {
            text.setColor(colour);
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            canvas.drawText(said, x, y, text);
        }

        private void round(Canvas canvas, Bitmap picture, float cx, float cy, float r, int alpha) {
            BitmapShader shader = new BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setRectToRect(new RectF(0, 0, picture.getWidth(), picture.getHeight()),
                    new RectF(cx - r, cy - r, cx + r, cy + r), Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            paint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }
    }

    // ============================================================== profile

    /** narezany's profile, with the avatar in the shape that is chosen. */
    static final class Profile extends View {
        private static String lastShape;

        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Matrix matrix = new Matrix();
        private final Path clip = new Path();
        private final Eased morph = new Eased(1f);
        private String from, to;
        private final Bitmap avatar;

        Profile(Context context, Skin skin) {
            super(context);
            this.skin = skin;
            avatar = Launcher.preview("tilcayo");
            to = AvatarShape.chosen();
            from = lastShape == null ? to : lastShape;
        }

        /** Another shape: the old one gives way to it. */
        void shape(String shape) {
            if (shape.equals(to)) return;
            from = to;
            to = shape;
            morph.value = 0f;
            morph.to(this, 1f, 420, new OvershootInterpolator(1.4f));
            lastShape = to;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (!from.equals(to)) {
                morph.value = 0f;
                morph.to(this, 1f, 420, new OvershootInterpolator(1.4f));
            }
            lastShape = to;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 196)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1);
            float cx = getWidth() / 2f, cy = 58 * d, r = 40 * d;
            float t = morph.value;
            float settled = Math.max(0f, Math.min(1f, t));
            if (!from.equals(to) && settled < 1f) avatarIn(canvas, from, cx, cy, r, Math.round(255 * (1f - settled)), 1f);
            avatarIn(canvas, to, cx, cy, r, Math.round(255 * settled), 0.85f + 0.15f * t);
            text.setTextAlign(Paint.Align.CENTER);
            words(canvas, "narezany", cx, cy + r + 24 * d, 17 * d, skin.text, true);
            words(canvas, "@narezany", cx, cy + r + 42 * d, 13 * d, skin.muted(), false);
            float y = cy + r + 72 * d, gap = getWidth() / 4f;
            String[][] stats = {{"17", Local.of("Подписки")}, {"4,2K", Local.of("Подписчики")}, {"52K", Local.of("Лайки")}};
            for (int i = 0; i < 3; i++) {
                float x = cx + (i - 1) * gap;
                words(canvas, stats[i][0], x, y, 16 * d, skin.text, true);
                words(canvas, stats[i][1], x, y + 17 * d, 12 * d, skin.muted(), false);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void avatarIn(Canvas canvas, String shape, float cx, float cy, float r, int alpha, float scale) {
            if (alpha <= 0 || avatar == null) return;
            int index = 0;
            for (int i = 0; i < AvatarShape.ALL.length; i++) if (AvatarShape.ALL[i].equals(shape)) index = i;
            matrix.reset();
            matrix.setScale(r * scale, r * scale);
            matrix.postTranslate(cx, cy);
            clip.reset();
            AvatarShape.unit(index).transform(matrix, clip);
            BitmapShader shader = new BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setRectToRect(new RectF(0, 0, avatar.getWidth(), avatar.getHeight()),
                    new RectF(cx - r, cy - r, cx + r, cy + r), Matrix.ScaleToFit.FILL);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            paint.setAlpha(alpha);
            canvas.drawPath(clip, paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }

        private void words(Canvas canvas, String said, float x, float y, float size, int colour, boolean bold) {
            text.setColor(colour);
            text.setTextSize(size);
            text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            canvas.drawText(said, x, y, text);
        }
    }

    // ================================================================ style

    /** A settings card in one of the three styles, small: what choosing it would look like. */
    static final class Style extends View {
        private final Skin skin;
        private final String mode;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        boolean chosen;

        Style(Context context, Skin skin, String mode, boolean chosen) {
            super(context);
            this.skin = skin;
            this.mode = mode;
            this.chosen = chosen;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 104)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1), w = getWidth(), h = getHeight();
            int accent = Accent.colour();
            box.set(3 * d, 3 * d, w - 3 * d, h - 3 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            boolean glass = "glass".equals(mode), fluent = "fluent".equals(mode);
            // the page behind
            if (glass) {
                paint.setShader(new LinearGradient(0, 0, w, h, Expressive.mix(accent, 0xFF2B2250, 0.4f), 0xFF0E3A44, Shader.TileMode.CLAMP));
            } else {
                paint.setColor(fluent ? Expressive.mix(skin.page, skin.text, 0.04f) : skin.page);
            }
            canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
            paint.setShader(null);
            if (glass) {
                paint.setShader(new RadialGradient(w * 0.75f, h * 0.3f, w * 0.4f, (accent & 0xFFFFFF) | 0xAA000000, 0, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
                paint.setShader(null);
            }
            if (chosen) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * d);
                paint.setColor(accent);
                canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
                paint.setStyle(Paint.Style.FILL);
            }
            float left = 11 * d, right = w - 11 * d;
            for (int row = 0; row < 2; row++) {
                float top = 16 * d + row * 38 * d, bottom = top + (fluent ? 33 : 36) * d;
                box.set(left, top, right, bottom);
                if (glass) {
                    paint.setColor(0x40FFFFFF);
                    canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(d);
                    paint.setColor(0x66FFFFFF);
                    canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else if (fluent) {
                    paint.setColor(skin.card);
                    canvas.drawRoundRect(box, 4 * d, 4 * d, paint);
                } else {
                    // a segmented group: large outer corners, small inner ones
                    float outer = 12 * d, inner = 3 * d;
                    float[] corners = row == 0 ? new float[] {outer, outer, outer, outer, inner, inner, inner, inner}
                            : new float[] {inner, inner, inner, inner, outer, outer, outer, outer};
                    Path p = new Path();
                    p.addRoundRect(box, corners, Path.Direction.CW);
                    paint.setColor(skin.card);
                    canvas.drawPath(p, paint);
                }
                float cy = (top + bottom) / 2f;
                int ink = glass ? 0xFFFFFFFF : skin.text;
                // the icon
                paint.setColor(glass ? 0x88FFFFFF : fluent ? accent : Expressive.mix(skin.card, accent, 0.3f));
                if (fluent) {
                    box.set(left + 7 * d, cy - 5 * d, left + 17 * d, cy + 5 * d);
                    canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                } else {
                    canvas.drawCircle(left + 13 * d, cy, 7 * d, paint);
                }
                // two lines of words
                paint.setColor((ink & 0xFFFFFF) | 0xCC000000);
                box.set(left + 25 * d, cy - 5 * d, right - 34 * d, cy - 2 * d);
                canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                paint.setColor((ink & 0xFFFFFF) | 0x66000000);
                box.set(left + 25 * d, cy + 2 * d, right - 44 * d, cy + 5 * d);
                canvas.drawRoundRect(box, 2 * d, 2 * d, paint);
                // the switch, on in the first row
                boolean on = row == 0;
                float sw = fluent ? 18 * d : 22 * d, sh = fluent ? 9 * d : 13 * d;
                box.set(right - 6 * d - sw, cy - sh / 2f, right - 6 * d, cy + sh / 2f);
                if (glass) {
                    paint.setColor(on ? (accent & 0xFFFFFF) | 0xE6000000 : 0x55FFFFFF);
                    canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                    paint.setColor(0xFFFFFFFF);
                    float tx = on ? box.right - sh * 0.62f : box.left + sh * 0.62f;
                    box.set(tx - sh * 0.62f, box.top + d, tx + sh * 0.62f, box.bottom - d);
                    canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                } else {
                    if (on) {
                        paint.setColor(accent);
                        canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                    } else {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(fluent ? d : 1.5f * d);
                        paint.setColor(skin.muted());
                        canvas.drawRoundRect(box, sh / 2f, sh / 2f, paint);
                        paint.setStyle(Paint.Style.FILL);
                    }
                    float r = fluent ? 2.6f * d : (on ? 4.6f * d : 3f * d);
                    paint.setColor(on ? 0xFFFFFFFF : skin.muted());
                    canvas.drawCircle(on ? box.right - sh / 2f : box.left + sh / 2f, cy, r, paint);
                }
            }
        }
    }

    // ============================================================== loading

    /** TikTok's two dots beside the mod's shape: the one in use bright, the other faint. */
    static final class Loader extends View {
        private static float lastOn = 0f;
        private final Skin skin;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Eased on = new Eased(lastOn);
        private float want;

        Loader(Context context, Skin skin, boolean enabled) {
            super(context);
            this.skin = skin;
            want = enabled ? 1f : 0f;
        }

        void set(boolean enabled) {
            want = enabled ? 1f : 0f;
            on.to(this, want, 320, EMPHASIZED);
            lastOn = want;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            set(want > 0.5f);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(dp(getContext(), 128)));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float d = dp(getContext(), 1), w = getWidth(), cy = 56 * d;
            float t = on.value;
            // TikTok's: a pink dot and a cyan one passing each other
            float phase = (android.os.SystemClock.uptimeMillis() % 900) / 900f;
            float swing = (float) Math.sin(phase * Math.PI * 2) * 9 * d;
            float lx = w * 0.3f;
            int faintTikTok = Math.round(255 * (1f - 0.75f * t));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((0x25F4EE) | (faintTikTok << 24));
            canvas.drawCircle(lx - swing, cy, 9 * d, paint);
            paint.setColor((0xFE2C55) | (faintTikTok << 24));
            canvas.drawCircle(lx + swing, cy, 9 * d, paint);
            // the mod's, drawn by the very code that draws it over TikTok
            int faintOurs = Math.round(255 * (0.25f + 0.75f * t));
            float size = 46 * d, rx = w * 0.7f;
            canvas.saveLayerAlpha(rx - size, cy - size, rx + size, cy + size, faintOurs);
            canvas.translate(rx - size / 2f, cy - size / 2f);
            Loading.preview(canvas, Math.round(size), Math.round(size));
            canvas.restore();
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(13 * d);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(Expressive.mix(skin.muted(), skin.text, 1f - t));
            canvas.drawText("TikTok", lx, cy + 46 * d, text);
            text.setColor(Expressive.mix(skin.muted(), skin.text, t));
            canvas.drawText("MargyT", rx, cy + 46 * d, text);
            // only while it can be seen: scrolled back into sight, it is drawn and starts again
            if (isShown() && getGlobalVisibleRect(seen)) postInvalidateOnAnimation();
        }

        private final android.graphics.Rect seen = new android.graphics.Rect();
    }
}

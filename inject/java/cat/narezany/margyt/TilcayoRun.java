package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Fat tilcayos running and jumping along a rolling wavy line at the bottom of
 * TikTok's screens. Mostly they jump; now and then a scene plays: a hug, a
 * kiss, a triple salute with fireworks, or one enormous leap.
 *
 * The cats come from the home screen made for the television (TVHome,
 * CatRunway), the same picture and the same moves. Here they are a layer over
 * TikTok that never takes a touch: everything underneath works as before.
 * Drawing follows the display's own frame rate and only the strip the cats
 * move through is drawn again. Off by default, kept on once switched on.
 */
final class TilcayoRun {

    private TilcayoRun() {}

    static final String KEY = "fun_tilcayo_run";
    private static final int TAG = 0x7e0c033e;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Bitmap sprite;

    static boolean isOn(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs != null && prefs.getBoolean(KEY, false);
    }

    static void set(Activity settings, boolean on) {
        SharedPreferences prefs = prefs(settings);
        if (prefs != null) prefs.edit().putBoolean(KEY, on).apply();
        if (!on) MAIN.post(TilcayoRun::removeAll);
    }

    private static SharedPreferences prefs(Context context) {
        Context base = context != null ? context : Margy.context();
        return base == null ? null : base.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    private static final List<java.lang.ref.WeakReference<Runway>> SHOWN = new ArrayList<>();

    /** A TikTok screen came up: the cats go on it, if they are wanted. */
    static void resumed(Activity activity) {
        if (activity == null || activity.getClass().getName().startsWith("cat.narezany.margyt.")) return;
        if (!isOn(activity)) return;
        MAIN.postDelayed(() -> attach(activity), 300L);
    }

    private static void attach(Activity activity) {
        try {
            if (activity.isFinishing() || !isOn(activity)) return;
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout) || !decor.isAttachedToWindow()) return;
            FrameLayout screen = (FrameLayout) decor;
            for (int i = 0; i < screen.getChildCount(); i++) if (screen.getChildAt(i).getTag(TAG) != null) return;
            Runway runway = new Runway(activity, true);
            runway.setTag(TAG, Boolean.TRUE);
            screen.addView(runway, new FrameLayout.LayoutParams(-1, -1));
            runway.setElevation(999f);
            synchronized (SHOWN) { SHOWN.add(new java.lang.ref.WeakReference<>(runway)); }
        } catch (Throwable error) {
            Diary.note("tilcayo run: " + error);
        }
    }

    private static void removeAll() {
        synchronized (SHOWN) {
            for (java.lang.ref.WeakReference<Runway> held : SHOWN) {
                Runway runway = held.get();
                if (runway != null && runway.getParent() instanceof FrameLayout)
                    ((FrameLayout) runway.getParent()).removeView(runway);
            }
            SHOWN.clear();
        }
    }

    /** The picture, once: a fat tilcayo cut out of its background. */
    static Bitmap sprite(Context context) {
        Bitmap known = sprite;
        if (known != null) return known;
        try (java.io.InputStream in = context.getAssets().open("margyt/tilcayo_run.png")) {
            known = BitmapFactory.decodeStream(in);
        } catch (Throwable error) {
            Diary.note("tilcayo run picture: " + error);
        }
        sprite = known;
        return known;
    }

    // ------------------------------------------------------------ the cats

    private static final class Cat {
        final float h, speed;
        float x, phase, air = -1f, airDur, airH, rest, tx, lean, sx = 1f, sy = 1f, lifted, sceneRot;
        int dir;
        boolean spin, busy, still;

        Cat(float h, int dir, float speed, Random random) {
            this.h = h;
            this.dir = dir;
            this.speed = speed;
            phase = random.nextFloat();
        }
    }

    private static final class Part {
        float x, y, vx, vy, life;
        final float max, size;
        final int colour;
        final boolean heart;

        Part(float x, float y, float vx, float vy, float life, int colour, boolean heart, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.max = life; this.colour = colour; this.heart = heart; this.size = size;
        }
    }

    private abstract static class Scene {
        float t;
        abstract boolean update(float dt);
    }

    /**
     * The strip with the cats. Over TikTok it fills the screen and lets every touch
     * through; in the settings it is the picture of itself, in a card.
     */
    static final class Runway extends View {
        private final boolean overTikTok;
        private final Bitmap picture;
        private final float aspect, dp1, amp, wave;
        private final Random random = new Random();
        private final List<Cat> cats = new ArrayList<>();
        private final ArrayList<Part> parts = new ArrayList<>();
        private Scene scene;
        private float nextScene = 7f, clock;
        private long last;
        private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint groundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint partPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path heart = new Path(), ground = new Path();
        private final Rect dirty = new Rect(), lastDirty = new Rect(), box = new Rect();
        private final RectF place = new RectF();
        private final int[] sparks;

        Runway(Context context, boolean overTikTok) {
            super(context);
            this.overTikTok = overTikTok;
            picture = sprite(context);
            aspect = picture == null ? 1f : picture.getWidth() / (float) picture.getHeight();
            dp1 = context.getResources().getDisplayMetrics().density;
            amp = dp1 * 7;
            wave = dp1 * 150;
            float scale = overTikTok ? 1f : 0.8f;
            cats.add(new Cat(dp1 * 54 * scale, 1, dp1 * 70, random));
            cats.add(new Cat(dp1 * 42 * scale, -1, dp1 * 104, random));
            cats.add(new Cat(dp1 * 34 * scale, 1, dp1 * 58, random));
            int accent = Accent.colour();
            sparks = new int[]{accent, 0xFFFF7EB6, 0xFFFFC857, 0xFF8B7CFF, 0xFFFFFFFF};
            groundPaint.setStyle(Paint.Style.STROKE);
            groundPaint.setStrokeCap(Paint.Cap.ROUND);
            groundPaint.setStrokeWidth(dp1 * 3);
            groundPaint.setColor((0x66 << 24) | (accent & 0x00FFFFFF));
            heart.moveTo(0f, 0.9f);
            heart.cubicTo(-1.5f, -0.2f, -0.7f, -1.3f, 0f, -0.45f);
            heart.cubicTo(0.7f, -1.3f, 1.5f, -0.2f, 0f, 0.9f);
            heart.close();
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            setWillNotDraw(false);
        }

        // never a touch: what is under the cats goes on working
        @Override public boolean dispatchTouchEvent(MotionEvent event) { return false; }
        @Override public boolean onTouchEvent(MotionEvent event) { return false; }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            for (int i = 0; i < cats.size(); i++) cats.get(i).x = w * (0.2f + 0.3f * i);
            lastDirty.set(0, 0, w, h);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            last = 0L;
            invalidate();
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) { last = 0L; invalidate(); }
        }

        /** Over TikTok the line runs above the navigation bar, not under it. */
        private float baseY() {
            float inset = 0;
            if (overTikTok) {
                try {
                    android.view.WindowInsets insets = getRootWindowInsets();
                    if (insets != null) inset = insets.getSystemWindowInsetBottom();
                } catch (Throwable ignored) {}
            }
            return getHeight() - inset - dp1 * 18;
        }

        private float groundY(float x) { return baseY() + amp * (float) Math.sin(x / wave * 2f * Math.PI - clock * 1.2f); }

        private float slope(float x) { return amp * 2f * (float) Math.PI / wave * (float) Math.cos(x / wave * 2f * Math.PI - clock * 1.2f); }

        private static float smooth(float v) {
            float k = Math.max(0f, Math.min(1f, v));
            return k * k * (3f - 2f * k);
        }

        // ---------- ordinary life: mostly jumping ----------

        private void jump(Cat cat, boolean bigOne) {
            cat.air = 0f;
            cat.airDur = 0.6f + random.nextFloat() * 0.3f;
            cat.airH = bigOne ? dp1 * (85 + random.nextFloat() * 35) : dp1 * (30 + random.nextFloat() * 42);
            if (bigOne) cat.airDur += 0.25f;
            cat.spin = random.nextFloat() < 0.25f;
        }

        private void live(Cat cat, float dt) {
            float margin = cat.h;
            if (cat.rest > 0) {
                cat.rest -= dt;
                cat.phase += dt * 0.6f;
                return;
            }
            if (cat.air >= 0) {
                cat.air += dt;
                cat.x += cat.dir * cat.speed * 0.9f * dt;
                if (cat.air >= cat.airDur) cat.air = -1f;
            } else {
                cat.x += cat.dir * cat.speed * dt;
                cat.phase += dt * cat.speed / (cat.h * 1.1f);
                float roll = random.nextFloat();
                if (roll < dt * 0.65f) jump(cat, random.nextFloat() < 0.15f);
                else if (roll < dt * 0.69f) cat.rest = 1f + random.nextFloat() * 1.5f;
                else if (roll < dt * 0.75f) cat.dir = -cat.dir;
            }
            if (cat.x < margin) { cat.x = margin; cat.dir = 1; }
            else if (cat.x > getWidth() - margin) { cat.x = getWidth() - margin; cat.dir = -1; }
        }

        // ---------- scenes ----------

        private boolean walk(Cat cat, float dt) {
            float d = cat.tx - cat.x;
            if (Math.abs(d) < 3f) { cat.x = cat.tx; cat.still = true; return true; }
            cat.still = false;
            cat.dir = d > 0 ? 1 : -1;
            cat.x += cat.dir * Math.min(cat.speed * 1.5f * dt, Math.abs(d));
            cat.phase += dt * cat.speed / (cat.h * 1.1f);
            return false;
        }

        private static void release(Cat... group) {
            for (Cat cat : group) {
                cat.busy = false; cat.still = false; cat.lean = 0f;
                cat.sx = 1f; cat.sy = 1f; cat.lifted = 0f; cat.sceneRot = 0f;
            }
        }

        private void heartAt(float x, float y, boolean big) {
            parts.add(new Part(x, y, 0f, 0f, 1.5f, 0xFFFF5C8A, true, dp1 * (big ? 13 : 7)));
        }

        private void burst(float x, float y, int colour) {
            for (int i = 0; i < 18; i++) {
                double a = i * 2 * Math.PI / 18 + random.nextFloat() * 0.3f;
                float speed = dp1 * (70 + random.nextFloat() * 110);
                parts.add(new Part(x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed, 1.2f, colour, false, dp1 * 3.2f));
            }
        }

        private void dust(float x, float y) {
            for (int i = 0; i < 9; i++) {
                int dir = i % 2 == 0 ? 1 : -1;
                parts.add(new Part(x, y, dir * dp1 * (20 + random.nextFloat() * 50), -dp1 * (10 + random.nextFloat() * 30), 0.6f, 0xFFE6E0D4, false, dp1 * 3f));
            }
        }

        /** Two cats meet in the middle: a hug, or a kiss with hearts. */
        private final class Duo extends Scene {
            final Cat left, right;
            final boolean kiss;
            final float total;
            int phase;
            float hold, nextHeart = 0.5f;

            Duo(Cat a, Cat b, boolean kiss) {
                this.kiss = kiss;
                left = a.x <= b.x ? a : b;
                right = left == a ? b : a;
                total = kiss ? 2.1f : 2.9f;
                float gap = (left.h + right.h) * aspect * 0.5f * (kiss ? 0.80f : 0.70f);
                float mx = getWidth() * (0.25f + random.nextFloat() * 0.5f);
                left.tx = mx - gap / 2;
                right.tx = mx + gap / 2;
                left.busy = true;
                right.busy = true;
            }

            @Override boolean update(float dt) {
                t += dt;
                if (phase == 0) {
                    boolean l = walk(left, dt), r = walk(right, dt);
                    if (l && r) { phase = 1; left.dir = 1; right.dir = -1; }
                    else if (t > 10f) { release(left, right); return true; }
                    return false;
                }
                hold += dt;
                float ease = smooth(hold / 0.45f) * smooth((total - hold) / 0.35f);
                float lean = (kiss ? 11f : 7f) * ease;
                left.lean = lean;
                right.lean = -lean;
                float top = Math.max(left.h, right.h);
                if (kiss) {
                    float off = left.h * 0.12f * ease;
                    left.x = left.tx + off;
                    right.x = right.tx - off;
                    left.lifted = left.h * 0.05f * ease;
                    right.lifted = right.h * 0.05f * ease;
                    if (hold > 0.75f && nextHeart > 0f) {
                        nextHeart = -1f;
                        float mid = (left.x + right.x) / 2;
                        heartAt(mid, groundY(mid) - top * 1.25f, true);
                        heartAt(mid - dp1 * 22, groundY(mid) - top * 1.0f, false);
                        heartAt(mid + dp1 * 22, groundY(mid) - top * 1.05f, false);
                    }
                } else {
                    float sq = 1f + 0.045f * ease * (float) Math.sin(hold * 8f);
                    left.sx = sq;
                    left.sy = 1f / sq;
                    right.sx = 1f + 0.045f * ease * (float) Math.sin(hold * 8f + 1.5f);
                    right.sy = 1f / right.sx;
                    if (hold > nextHeart) {
                        nextHeart += 0.5f;
                        float mid = (left.x + right.x) / 2 + (random.nextFloat() - 0.5f) * dp1 * 30;
                        heartAt(mid, groundY(mid) - top * 1.2f, false);
                    }
                }
                if (hold >= total) {
                    release(left, right);
                    left.dir = -1;
                    right.dir = 1;
                    return true;
                }
                return false;
            }
        }

        /** All three in a row: two rolling waves of jumps, then one together, and fireworks. */
        private final class Salute extends Scene {
            final List<Cat> trio;
            int phase;
            boolean fired;

            Salute(List<Cat> group) {
                trio = new ArrayList<>(group);
                Collections.sort(trio, (a, b) -> Float.compare(a.x, b.x));
                float spacing = 0;
                for (Cat cat : trio) spacing += cat.h * aspect;
                spacing = spacing / trio.size() * 0.95f;
                float mx = getWidth() * (0.3f + random.nextFloat() * 0.4f);
                for (int i = 0; i < trio.size(); i++) {
                    trio.get(i).tx = mx + (i - 1) * spacing;
                    trio.get(i).busy = true;
                }
            }

            private float lift(int i) {
                for (int r = 0; r <= 1; r++) {
                    float start = r * 0.8f + i * 0.12f, u = (t - start) / 0.62f;
                    if (u >= 0f && u <= 1f) return 4f * dp1 * 52 * u * (1f - u);
                }
                float u = (t - 1.75f) / 0.85f;
                return u >= 0f && u <= 1f ? 4f * dp1 * 100 * u * (1f - u) : 0f;
            }

            @Override boolean update(float dt) {
                if (phase == 0) {
                    t += dt;
                    boolean done = true;
                    for (Cat cat : trio) done &= walk(cat, dt);
                    if (done) {
                        phase = 1;
                        t = 0f;
                        trio.get(0).dir = 1;
                        trio.get(1).dir = 1;
                        trio.get(2).dir = -1;
                    } else if (t > 10f) {
                        release(trio.toArray(new Cat[0]));
                        return true;
                    }
                    return false;
                }
                t += dt;
                for (int i = 0; i < trio.size(); i++) trio.get(i).lifted = lift(i);
                if (!fired && t > 1.75f + 0.425f) {
                    fired = true;
                    for (int i = 0; i < trio.size(); i++) {
                        Cat cat = trio.get(i);
                        burst(cat.x, groundY(cat.x) - cat.lifted - cat.h * 1.6f - dp1 * 18, sparks[(i * 2) % sparks.length]);
                    }
                }
                if (t > 3.1f) {
                    release(trio.toArray(new Cat[0]));
                    return true;
                }
                return false;
            }
        }

        /** One cat crouches, flies up spinning twice and lands in a puff of dust. */
        private final class Leap extends Scene {
            final Cat cat;
            final float peak;
            int phase;

            Leap(Cat cat) {
                this.cat = cat;
                peak = Math.max(cat.h, Math.min(dp1 * 250, baseY() - cat.h * 1.7f - dp1 * 30));
                cat.busy = true;
                cat.still = true;
            }

            @Override boolean update(float dt) {
                t += dt;
                if (phase == 0) {
                    float k = smooth(t / 0.5f);
                    cat.sy = 1f - 0.3f * k;
                    cat.sx = 1f / cat.sy;
                    if (t >= 0.5f) { phase = 1; t = 0f; }
                } else if (phase == 1) {
                    float u = Math.max(0f, Math.min(1f, t / 1.8f));
                    cat.lifted = 4f * peak * u * (1f - u);
                    cat.sceneRot = -cat.dir * 720f * u;
                    cat.sy = 1f + 0.12f * (float) Math.sin(u * Math.PI);
                    cat.sx = 1f / cat.sy;
                    if (t >= 1.8f) {
                        phase = 2;
                        t = 0f;
                        cat.lifted = 0f;
                        cat.sceneRot = 0f;
                        dust(cat.x, groundY(cat.x));
                    }
                } else {
                    float k = smooth(t / 0.4f);
                    cat.sy = 0.7f + 0.3f * k;
                    cat.sx = 1f / cat.sy;
                    if (t >= 0.4f) { release(cat); return true; }
                }
                return false;
            }
        }

        private void startScene() {
            List<Cat> free = new ArrayList<>();
            for (Cat cat : cats) if (!cat.busy && cat.air < 0 && cat.rest <= 0) free.add(cat);
            Collections.shuffle(free, random);
            int pick = random.nextInt(4);
            if (pick <= 1) scene = free.size() >= 2 ? new Duo(free.get(0), free.get(1), random.nextBoolean()) : null;
            else if (pick == 2) scene = free.size() >= 3 ? new Salute(free) : null;
            else scene = free.isEmpty() ? null : new Leap(free.get(0));
            nextScene = scene == null ? 1.5f : 10f + random.nextFloat() * 15f;
        }

        // ---------- frame ----------

        private float lift(Cat cat) {
            if (cat.air >= 0) {
                float u = cat.air / cat.airDur;
                return 4f * cat.airH * u * (1f - u);
            }
            if (cat.still || cat.rest > 0) return cat.lifted;
            return Math.abs((float) Math.sin(cat.phase * Math.PI)) * cat.h * 0.12f + cat.lifted;
        }

        private void updateParts(float dt) {
            Iterator<Part> it = parts.iterator();
            while (it.hasNext()) {
                Part p = it.next();
                p.life -= dt;
                if (p.life <= 0f) { it.remove(); continue; }
                if (p.heart) {
                    p.y -= dp1 * 38 * dt;
                    p.x += (float) Math.sin(p.life * 5f) * dp1 * 12 * dt;
                } else {
                    p.vy += dp1 * 130 * dt;
                    p.x += p.vx * dt;
                    p.y += p.vy * dt;
                }
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            if (picture == null || getWidth() == 0) return;
            long now = System.nanoTime();
            float dt = last == 0L ? 0f : Math.min(0.1f, (now - last) / 1e9f);
            last = now;
            clock += dt;

            if (scene != null) {
                if (scene.update(dt)) scene = null;
            } else {
                nextScene -= dt;
                if (nextScene <= 0f) startScene();
            }
            for (Cat cat : cats) if (!cat.busy) live(cat, dt);
            updateParts(dt);

            ground.rewind();
            for (float x = 0f; x <= getWidth(); x += dp1 * 6) {
                float y = groundY(x);
                if (x == 0f) ground.moveTo(x, y); else ground.lineTo(x, y);
            }
            canvas.drawPath(ground, groundPaint);

            dirty.set(0, (int) (baseY() - amp - dp1 * 6), getWidth(), getHeight());
            List<Cat> order = new ArrayList<>(cats);
            Collections.sort(order, (a, b) -> Float.compare(a.h, b.h));
            for (Cat cat : order) drawCat(canvas, cat);
            for (Part p : parts) drawPart(canvas, p);

            // on until the screen goes: a window out of sight stops the frames by itself
            if (isShown() && getWindowVisibility() == VISIBLE) {
                box.set(dirty);
                box.union(lastDirty);
                lastDirty.set(dirty);
                postInvalidateOnAnimation(box.left, box.top, box.right, box.bottom);
            }
        }

        private void drawCat(Canvas canvas, Cat cat) {
            float w = cat.h * aspect;
            float feet = groundY(cat.x) - lift(cat);
            float breathe = (float) Math.sin(cat.phase * 2f * Math.PI * (cat.rest > 0 || cat.still ? 0.5f : 1f));
            float sy = (1f + 0.05f * breathe) * cat.sy;
            float sx = cat.sx / (1f + 0.05f * breathe);
            float rot = cat.lean + cat.sceneRot;
            if (!cat.still) rot += (float) Math.toDegrees(Math.atan(slope(cat.x))) + (float) Math.sin(cat.phase * 2f * Math.PI) * 3f;
            if (cat.air >= 0 && cat.spin) rot += -cat.dir * 360f * (cat.air / cat.airDur);
            float cy = feet - cat.h / 2f;
            canvas.save();
            canvas.translate(cat.x, cy);
            canvas.rotate(rot);
            canvas.scale(cat.dir * sx, sy);
            place.set(-w / 2, -cat.h / 2, w / 2, cat.h / 2);
            canvas.drawBitmap(picture, null, place, bmp);
            canvas.restore();
            int r = (int) (Math.max(w, cat.h) * 0.8f);
            dirty.union((int) (cat.x - r), (int) (cy - r), (int) (cat.x + r), (int) (cy + r));
        }

        private void drawPart(Canvas canvas, Part p) {
            float a = Math.max(0f, Math.min(1f, p.life / (p.max * 0.45f)));
            partPaint.setColor((p.colour & 0x00FFFFFF) | ((int) (255 * a) << 24));
            if (p.heart) {
                canvas.save();
                canvas.translate(p.x, p.y);
                canvas.scale(p.size, p.size);
                canvas.drawPath(heart, partPaint);
                canvas.restore();
            } else {
                canvas.drawCircle(p.x, p.y, p.size * (0.5f + 0.5f * a), partPaint);
            }
            int r = (int) (p.size * 2f + dp1 * 4);
            dirty.union((int) (p.x - r), (int) (p.y - r), (int) (p.x + r), (int) (p.y + r));
        }
    }
}

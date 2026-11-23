package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * Нарезанный's birthday, 24 November: a cake instead of the like button on his own videos, all day, Moscow time.
 * A preview build has it now, and a beta for testers (Version.BETA) all the time, for a look at it.
 */
final class LikeCake {
    private LikeCake() {}

    /** 24.11.2026 00:00 and 25.11.2026 00:00, Moscow time. */
    private static final long OPENS = 1795467600000L, CAKE_ENDS = 1795554000000L;
    /** His handle: the videos that get a cake. */
    private static final String OWNER = "narezany";

    static boolean cakeDay() {
        if (Version.LITE) return false;
        if (Version.PARTY_PREVIEW || Version.BETA) return true;
        long now = System.currentTimeMillis();
        return now >= OPENS && now < CAKE_ENDS;
    }

    private static final java.util.Map<String, Bitmap> SPRITES = new java.util.HashMap<>();

    private static Bitmap sprite(String name) {
        synchronized (SPRITES) {
            if (SPRITES.containsKey(name)) return SPRITES.get(name);
        }
        Bitmap b = null;
        try (InputStream in = Margy.context().getAssets().open("margyt/cake/" + name + ".png")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) {
        }
        synchronized (SPRITES) {
            SPRITES.put(name, b);
        }
        return b;
    }

    static void trimMemory() {
        synchronized (SPRITES) {
            SPRITES.clear();
        }
    }

    // ------------------------------------------------------------ the cake on his videos

    /** What TikTok last painted each heart with: pink is liked. Read straight from the paint, so a tap shows at once. */
    private static final java.util.Map<View, Boolean> painted = new java.util.WeakHashMap<>();

    static void painted(View v, boolean liked) {
        if (v == null || !cakeDay()) return;
        synchronized (painted) {
            painted.put(v, liked);
        }
    }

    /** Liked or not: the heart's own paint if it was seen, else the video's own record. */
    private static boolean liked(View icon, Object post) {
        Boolean known;
        synchronized (painted) {
            known = painted.get(icon);
        }
        if (known != null) return known;
        Object digged = NativeRead.get(post, "isUserDigged");
        if (digged instanceof Boolean) return (Boolean) digged;
        return NativeRead.number(NativeRead.get(post, "getUserDigg")) == 1;
    }

    private static WeakReference<View> caked = new WeakReference<>(null);
    private static WeakReference<View> hidden = new WeakReference<>(null);
    private static WeakReference<Object> lastPost = new WeakReference<>(null);
    private static boolean lastMine;
    private static Cake cakeDrawable;
    private static long lookedAt;

    /** Before each frame, from the rail: the like button of the video on screen wears a cake if it is his video. */
    static void tick() {
        if (!cakeDay()) {
            if (caked.get() != null) restore();
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lookedAt < 100L) {
            View on = caked.get();
            if (on != null && cakeDrawable != null && cakeDrawable.lit) on.invalidate();
            return;
        }
        lookedAt = now;
        try {
            Object post = PlayerTools.post();
            if (post != lastPost.get()) {
                lastPost = new WeakReference<>(post);
                lastMine = post != null && OWNER.equalsIgnoreCase(NativeRead.string(NativeRead.get(NativeRead.get(post, "getAuthor"), "getUniqueId")));
            }
            View like = lastMine ? FeedRail.likeOnScreen() : null;
            View icon = like == null ? null : iconOf(like, 0);
            if (icon == null) {
                restore();
                return;
            }
            if (caked.get() != like || hidden.get() != icon) {
                restore();
                if (cakeDrawable == null) cakeDrawable = new Cake();
                like.getOverlay().add(cakeDrawable);
                caked = new WeakReference<>(like);
                hidden = new WeakReference<>(icon);
            }
            if (icon.getAlpha() != 0f) icon.setAlpha(0f);
            int[] at = new int[2], base = new int[2];
            icon.getLocationInWindow(at);
            like.getLocationInWindow(base);
            int x = at[0] - base[0], y = at[1] - base[1];
            // exactly where the heart was, never bigger: the count under it stays readable
            cakeDrawable.setBounds(x, y, x + icon.getWidth(), y + icon.getHeight());
            cakeDrawable.lit = liked(icon, post);
            like.invalidate();
        } catch (Throwable error) {
            Diary.note("party cake: " + error);
            restore();
        }
    }

    private static void restore() {
        View like = caked.get(), icon = hidden.get();
        if (like != null && cakeDrawable != null) like.getOverlay().remove(cakeDrawable);
        if (icon != null) icon.setAlpha(1f);
        caked = new WeakReference<>(null);
        hidden = new WeakReference<>(null);
    }

    /** The heart in the like button: the first picture in it, never the count. */
    private static View iconOf(View v, int depth) {
        if (v == null || depth > 6 || v.getVisibility() != View.VISIBLE) return null;
        String name = v.getClass().getName();
        if (v instanceof android.widget.ImageView || name.contains("Lottie") || name.contains("TuxIcon")) {
            return v.getWidth() > 0 ? v : null;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View found = iconOf(g.getChildAt(i), depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** The pixel cake in the heart's place: candles lit once the video is liked. */
    private static final class Cake extends Drawable {
        private final Paint pix = new Paint();
        private final long born = System.nanoTime();
        private final RectF to = new RectF();
        /** Not liked yet: the cake in grey, the way the heart is white until it is pressed. */
        private final android.graphics.ColorMatrixColorFilter grey;
        boolean lit;

        Cake() {
            pix.setFilterBitmap(false);
            android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
            m.setSaturation(0f);
            grey = new android.graphics.ColorMatrixColorFilter(m);
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            if (b.width() <= 0) return;
            float t = (System.nanoTime() - born) / 1e9f;
            Bitmap cake = sprite(lit ? "cake_lit_" + ((int) (t * 6f) % 2) : "cake");
            if (cake == null) return;
            // fit inside the heart's box, keeping its shape, lifted a little above its middle so the count below stays clear
            float k = Math.min(b.width() / (float) cake.getWidth(), b.height() / (float) cake.getHeight()) * 0.86f;
            float w = cake.getWidth() * k, h = cake.getHeight() * k;
            float x = b.exactCenterX() - w * 0.5f, y = b.exactCenterY() - h * 0.5f - b.height() * 0.1f;
            to.set(x, y, x + w, y + h);
            pix.setColorFilter(lit ? null : grey);
            c.drawBitmap(cake, null, to, pix);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter f) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

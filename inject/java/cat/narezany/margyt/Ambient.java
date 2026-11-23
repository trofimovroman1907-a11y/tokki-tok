package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Ambient mode (2.4), the way YouTube has it: the black bars round a video that does not fill the screen glow with the
 * video's own colours.
 *
 * A few times a second the playing video (GlassVideo finds it) is read as a tiny picture, softened, and laid as the
 * background of the frame round it, stretched over the whole of it: the bars get the colours of the video's own edges,
 * dimmed, and a new picture fades over the last one, so it moves as slowly as YouTube's. A video that fills the screen
 * has no bars and nothing is done; the frame's own background is put back when the mode is off.
 */
final class Ambient {
    private Ambient() {}

    static final String KEY = "ambient_mode";
    private static final long TICK = 350L;
    private static final int SIDE = 20;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<View, Drawable> before = new WeakHashMap<>();
    private static WeakReference<Activity> screen = new WeakReference<>(null);
    private static volatile int known = -1;
    private static boolean running;

    static boolean on() {
        int k = known;
        if (k < 0) {
            Context c = Margy.context();
            if (c == null) return false;
            k = c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, !DevicePerformance.limited(c)) ? 1 : 0;
            known = k;
        }
        return k == 1;
    }

    static void set(boolean value) {
        known = value ? 1 : 0;
        Context c = Margy.context();
        if (c != null) c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply();
        if (!value) {
            running = false;
            main.removeCallbacks(tick);
            main.post(Ambient::restore);
        }
    }

    /** The main screen came up (SettingsRow): the loop runs while it is there. */
    static void resumed(Activity activity) {
        if (!on() || activity == null || !activity.getClass().getName().endsWith(".MainActivity")) return;
        screen = new WeakReference<>(activity);
        if (running) return;
        running = true;
        main.post(tick);
    }

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            Activity activity = screen.get();
            if (!running || !on() || activity == null || activity.isFinishing() || activity.isDestroyed()) {
                running = false;
                return;
            }
            if (activity.hasWindowFocus()) step(activity);
            main.postDelayed(this, TICK);
        }
    };

    /**
     * The glow goes on the frame of the video on screen and on nothing else. A frame with a background of its own is
     * a view that draws, and Android then takes it for opaque when it works out what a SurfaceView may show through:
     * a video played into a SurfaceView under such a frame is not composited at all -- a black video. So a SurfaceView
     * never gets a glow, and every other frame, a cell scrolled away and used again for another video, has its own
     * background back at once.
     */
    private static void step(Activity activity) {
        try {
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (root == null) return;
            if (surfaceShown(root)) { restore(); return; }
            if (photos()) { photoStep(activity, root); return; }
            View video = GlassVideo.render(root);
            ViewGroup frame = video == null || video instanceof SurfaceView || video.getWidth() <= 0 || video.getHeight() <= 0
                    || under(video, SurfaceView.class) ? null : frame(video);
            Glow glow = dress(frame, video);
            if (glow != null) sample(video, glow);
        } catch (Throwable error) {
            Diary.note("ambient: " + error);
        }
    }

    /**
     * The glow on the whole cell of the feed round `frame`, not only the frame: what lies between them (the theme's
     * own colour above and below) is made see-through for as long. Every other view the mod dressed is put back.
     * `video` is the part never painted over (null for a photo, which is drawn above the glow anyway).
     */
    private static Glow dress(ViewGroup frame, View video) {
        ViewGroup host = frame == null ? null : cellOf(frame);
        if (host != null && holds(host, SurfaceView.class, 0)) host = null;
        java.util.Set<View> keep = new java.util.HashSet<>();
        if (host != null) {
            keep.add(host);
            for (View v = frame; v != null && v != host; v = v.getParent() instanceof View ? (View) v.getParent() : null) keep.add(v);
        }
        restore(keep);
        if (host == null) return null;
        for (View v : keep) {
            if (v == host) continue;
            Drawable bg = v.getBackground();
            if (bg == null || bg instanceof Glow) continue;
            if (!before.containsKey(v)) before.put(v, bg);
            v.setBackground(null);
        }
        Glow glow;
        Drawable now = host.getBackground();
        if (now instanceof Glow) glow = (Glow) now;
        else {
            if (!before.containsKey(host)) before.put(host, now);
            glow = new Glow(host, before.get(host));
            host.setBackground(glow);
        }
        glow.video = new WeakReference<>(video);
        glow.inside = new WeakReference<>(frame);
        glow.post = new WeakReference<>(PlayerTools.post());
        return glow;
    }

    // ------------------------------------------------------------------ photo posts

    private static int pagerId, pageFrameId;

    /**
     * A photo post: its pictures are pages of a pager of their own (r9x), each in a frame (r9l) with the picture drawn
     * fit inside it. The page on screen is the one dressed, and its colours are read off the screen itself, from the
     * middle of the picture (the bars round it would be the glow), since its pictures are hardware bitmaps.
     */
    private static void photoStep(Activity activity, View root) {
        if (pagerId == 0) {
            pagerId = root.getResources().getIdentifier("r9x", "id", root.getContext().getPackageName());
            pageFrameId = root.getResources().getIdentifier("r9l", "id", root.getContext().getPackageName());
        }
        ViewGroup frame = null;
        View pager = pagerId == 0 ? null : largestShown(root, pagerId);
        if (pager instanceof ViewGroup) {
            ViewGroup pages = (ViewGroup) pager;
            int[] at = new int[2];
            for (int i = 0; i < pages.getChildCount() && frame == null; i++) {
                View page = pages.getChildAt(i);
                if (!(page instanceof ViewGroup) || page.getVisibility() != View.VISIBLE) continue;
                page.getLocationOnScreen(at);
                if (Math.abs(at[0]) > page.getWidth() / 2) continue;
                View f = page.findViewById(pageFrameId);
                if (f instanceof ViewGroup && f.isShown() && f.getHeight() > 0) frame = (ViewGroup) f;
            }
        }
        Glow glow = dress(frame, null);
        if (glow == null || android.os.Build.VERSION.SDK_INT < 26 || activity.getWindow() == null) return;
        int[] at = new int[2];
        frame.getLocationInWindow(at);
        int w = frame.getWidth(), h = frame.getHeight();
        Rect middle = new Rect(at[0] + w / 6, at[1] + h * 42 / 100, at[0] + w * 5 / 6, at[1] + h * 58 / 100);
        if (middle.width() <= 0 || middle.height() <= 0) return;
        final Bitmap picture = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888);
        try {
            PixelCopy.request(activity.getWindow(), middle, picture, result -> {
                if (result == PixelCopy.SUCCESS) glow.take(soften(picture));
                else picture.recycle();
            }, main);
        } catch (Throwable error) {
            picture.recycle();
        }
    }

    /** The view with that id that shows the most of itself on screen. */
    private static View largestShown(View root, int id) {
        java.util.List<View> found = new java.util.ArrayList<>();
        collect(root, id, found, new int[]{6000});
        View best = null;
        int most = 0;
        Rect seen = new Rect();
        for (View v : found) {
            if (!v.isShown() || !v.getGlobalVisibleRect(seen)) continue;
            int area = seen.width() * seen.height();
            if (area > most) { most = area; best = v; }
        }
        return best;
    }

    private static void collect(View view, int id, java.util.List<View> out, int[] budget) {
        if (budget[0]-- <= 0 || view.getVisibility() != View.VISIBLE) return;
        if (view.getId() == id) out.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), id, out, budget);
        }
    }

    /**
     * The feed moved to another video (GlassVideo): every glow is taken off at once, not on the next step. A glow left
     * on the cell scrolled away overlaps the next one while the feed moves, and if that one plays into a SurfaceView
     * the window takes the glow for opaque over it: a black video, or a black blink, on going back up the feed.
     */
    static void videoChanged() {
        if (!running && before.isEmpty()) return;
        if (Looper.myLooper() == Looper.getMainLooper()) restore();
        else main.post(Ambient::restore);
    }

    /**
     * A photo post plays its music through a player with a video view of its own that shows nothing, and the photo
     * itself is drawn by the frame round it: a glow there would read no colours and take the photo's own background
     * away -- a black post. Photo posts are left as they are.
     */
    private static boolean photos() {
        Object post = PlayerTools.post();
        try {
            return post instanceof com.ss.android.ugc.aweme.feed.model.Aweme && Feed.isPhotos((com.ss.android.ugc.aweme.feed.model.Aweme) post);
        } catch (Throwable error) {
            return false;
        }
    }

    private static long surfaceLooked;
    private static boolean surfaceSeen;

    /** Whether a SurfaceView shows anywhere in the window: then nothing is painted at all. Looked at every 1.5 s. */
    private static boolean surfaceShown(View root) {
        long now = SystemClock.uptimeMillis();
        if (now - surfaceLooked < 1500L) return surfaceSeen;
        surfaceLooked = now;
        int[] budget = {6000};
        surfaceSeen = anySurface(root, budget);
        return surfaceSeen;
    }

    private static boolean anySurface(View view, int[] budget) {
        if (budget[0]-- <= 0 || view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof SurfaceView) return view.getWidth() > 0 && view.getHeight() > 0 && view.isShown();
        if (!(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) if (anySurface(group.getChildAt(i), budget)) return true;
        return false;
    }

    /** Whether a view of that kind lies anywhere in the frame's cell beside the video (a second player). */
    private static boolean under(View video, Class<?> kind) {
        View cell = video.getParent() instanceof View ? (View) video.getParent() : null;
        for (int i = 0; i < 3 && cell != null && cell.getParent() instanceof View; i++) cell = (View) cell.getParent();
        return cell != null && holds(cell, kind, 0);
    }

    private static boolean holds(View view, Class<?> kind, int depth) {
        if (kind.isInstance(view)) return true;
        if (depth > 8 || !(view instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) if (holds(group.getChildAt(i), kind, depth + 1)) return true;
        return false;
    }

    /**
     * The nearest parent with bars round the video: taller or wider than it by a few per cent, with the video in its
     * middle. A video filling the screen over the tab bar has room only on one side and is left alone.
     */
    private static ViewGroup frame(View video) {
        View at = video;
        float x = 0f, y = 0f;
        for (int i = 0; i < 4; i++) {
            if (!(at.getParent() instanceof ViewGroup)) return null;
            x += at.getLeft() + at.getTranslationX();
            y += at.getTop() + at.getTranslationY();
            ViewGroup up = (ViewGroup) at.getParent();
            float top = y, bottom = up.getHeight() - y - video.getHeight();
            float left = x, right = up.getWidth() - x - video.getWidth();
            if (barred(top, bottom, up.getHeight()) || barred(left, right, up.getWidth())) return up;
            at = up;
        }
        return null;
    }

    /** The frame's cell in the feed: the view right under the list or pager that scrolls; the frame itself if none. */
    private static ViewGroup cellOf(ViewGroup frame) {
        View at = frame;
        for (int i = 0; i < 8 && at.getParent() instanceof ViewGroup; i++) {
            ViewGroup up = (ViewGroup) at.getParent();
            if (scroller(up)) return at instanceof ViewGroup ? (ViewGroup) at : frame;
            at = up;
        }
        return frame;
    }

    private static boolean scroller(View view) {
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.endsWith("RecyclerView") || name.endsWith("ViewPager") || name.endsWith("ViewPager2")) return true;
        }
        return false;
    }

    private static boolean barred(float a, float b, int whole) {
        float least = Math.min(a, b), most = Math.max(a, b);
        return least > whole * 0.03f && least >= most * 0.5f;
    }

    private static void sample(View video, Glow glow) {
        if (video instanceof TextureView) {
            Bitmap picture = ((TextureView) video).getBitmap(SIDE, SIDE);
            if (picture != null) glow.take(soften(picture));
        } else if (video instanceof SurfaceView && android.os.Build.VERSION.SDK_INT >= 24) {
            SurfaceView surface = (SurfaceView) video;
            if (surface.getHolder() == null || surface.getHolder().getSurface() == null || !surface.getHolder().getSurface().isValid()) return;
            final Bitmap picture = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888);
            PixelCopy.request(surface, picture, result -> {
                if (result == PixelCopy.SUCCESS) glow.take(soften(picture));
                else picture.recycle();
            }, main);
        }
    }

    /** Three box passes over the little picture: stretched over a screen it is a glow, not squares. */
    private static Bitmap soften(Bitmap picture) {
        int w = picture.getWidth(), h = picture.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        picture.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, true);
            box(tmp, px, w, h, false);
        }
        Bitmap out = picture.isMutable() ? picture : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        if (out != picture) picture.recycle();
        return out;
    }

    private static void box(int[] from, int[] to, int w, int h, boolean across) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, n = 0;
                for (int d = -2; d <= 2; d++) {
                    int xx = across ? x + d : x, yy = across ? y : y + d;
                    if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
                    int c = from[yy * w + xx];
                    r += (c >> 16) & 255; g += (c >> 8) & 255; b += c & 255; n++;
                }
                to[y * w + x] = 0xFF000000 | (r / n) << 16 | (g / n) << 8 | (b / n);
            }
        }
    }

    /** Every view's own background back where it was, but those of the cell on screen (or all of them, for null). */
    private static void restore(java.util.Set<View> keep) {
        for (java.util.Map.Entry<View, Drawable> e : new java.util.ArrayList<>(before.entrySet())) {
            View view = e.getKey();
            if (view == null || keep != null && keep.contains(view)) continue;
            Drawable bg = view.getBackground();
            // the glow, or a background the mod took away; one TikTok has put there since is left alone
            if (bg instanceof Glow || bg == null) view.setBackground(e.getValue());
            before.remove(view);
        }
    }

    private static void restore() { restore(null); }

    /** Black, and the video's colours over it: the last picture fading into the new one. */
    private static final class Glow extends Drawable {
        private static final long FADE = 900L;
        private final WeakReference<View> frame;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Rect whole = new Rect();
        WeakReference<View> video = new WeakReference<>(null), inside = new WeakReference<>(null);
        WeakReference<Object> post = new WeakReference<>(null);
        private Bitmap last, next;
        private long since;

        private final Drawable own;

        Glow(View frame, Drawable own) {
            this.frame = new WeakReference<>(frame);
            this.own = own;
        }

        /** The frame as TikTok had it, for whenever there is no glow to draw. */
        private void plain(Canvas canvas) {
            if (own == null) return;
            own.setBounds(getBounds());
            own.draw(canvas);
        }

        void take(Bitmap picture) {
            if (last != null && last != next) last.recycle();
            last = next;
            next = picture;
            since = SystemClock.uptimeMillis();
            View f = frame.get();
            if (f != null) f.invalidate();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            View f = frame.get(), v = video.get(), in = inside.get();
            // only for the post it was made for: a cell that has moved on to another one is drawn as TikTok had it
            // at once, not a step later, or the last glow blinks over the next post
            if (next == null || f == null || in == null || !in.isAttachedToWindow() || !in.isShown()
                    || post.get() != PlayerTools.post()) { plain(canvas); return; }
            canvas.save();
            if (v != null) {
                if (!v.isAttachedToWindow() || v.getVisibility() != View.VISIBLE) { canvas.restore(); plain(canvas); return; }
                // where the video is in the frame: nothing is ever drawn there. A SurfaceView shows its picture
                // through a hole in the window, and a background painted over that hole is a black video.
                float x = 0f, y = 0f;
                View at = v;
                while (at != null && at != f) {
                    x += at.getLeft() + at.getTranslationX();
                    y += at.getTop() + at.getTranslationY();
                    at = at.getParent() instanceof View ? (View) at.getParent() : null;
                }
                if (at != f) { canvas.restore(); plain(canvas); return; }
                int left = Math.round(x), top = Math.round(y), right = left + v.getWidth(), bottom = top + v.getHeight();
                // a video that fills the frame (a cell used again for another one) has no bars to light
                if (left <= b.left + 2 && top <= b.top + 2 && right >= b.right - 2 && bottom >= b.bottom - 2) { canvas.restore(); plain(canvas); return; }
                if (android.os.Build.VERSION.SDK_INT >= 26) canvas.clipOutRect(left, top, right, bottom);
                else canvas.clipRect(left, top, right, bottom, android.graphics.Region.Op.DIFFERENCE);
            }
            canvas.drawColor(0xFF000000);
            whole.set(b);
            float t = Math.min(1f, (SystemClock.uptimeMillis() - since) / (float) FADE);
            if (last != null && t < 1f) {
                paint.setAlpha(255);
                canvas.drawBitmap(last, null, whole, paint);
            }
            paint.setAlpha(last == null || t >= 1f ? 255 : Math.round(255 * t));
            canvas.drawBitmap(next, null, whole, paint);
            // dimmed as YouTube's is: the video stays the brightest thing on screen
            canvas.drawColor(0x6E000000);
            canvas.restore();
            if (t < 1f) f.postInvalidateOnAnimation();
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

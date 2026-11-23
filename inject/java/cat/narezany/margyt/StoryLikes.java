package cat.narezany.margyt;
// Идея из ttcuz (автор tremorttcuz), с его разрешения; в 2.4 переписано: число рисуется над сердечком.

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

/**
 * The likes of a story (2.4): a story shows its heart and no number, so the number is drawn over it here.
 *
 * The heart of a story is ImmersiveStoryDiggAssem; each time it is given a post (its jq, see dexpatch) the post comes
 * here, and a small label drawn in the overlay of the nearest parent with room above the heart says how many likes it
 * has. Nothing of TikTok's is taken off or moved; the label only draws, it is never touched.
 */
public final class StoryLikes {
    private StoryLikes() {}

    private static final int OURS = 0x7e0c0371;

    static long count(Object post) {
        Object n = NativeRead.get(NativeRead.get(post, "getStatistics"), "getDiggCount");
        return n instanceof Number ? Math.max(-1L, ((Number) n).longValue()) : -1L;
    }

    /** The heart of a story was given its post. */
    public static void story(Object component, Object params) {
        try {
            Object content = NativeRead.get(component, "getContentView");
            if (!(content instanceof View)) return;
            View heart = (View) content;
            Object post = NativeRead.get(params, "getAweme");
            Object known = heart.getTag(OURS);
            Label label = known instanceof Label ? (Label) known : null;
            if (label == null) {
                label = new Label(heart);
                heart.setTag(OURS, label);
            }
            label.post = new WeakReference<>(post);
            if (heart.getWidth() > 0) label.place();
            else heart.post(label::place);
        } catch (Throwable error) {
            Diary.note("story likes: " + error);
        }
    }

    private static final class Label extends Drawable {
        final WeakReference<View> heart;
        WeakReference<Object> post = new WeakReference<>(null);
        WeakReference<ViewGroup> host = new WeakReference<>(null);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float density;

        Label(View heart) {
            this.heart = new WeakReference<>(heart);
            density = heart.getResources().getDisplayMetrics().density;
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(13f * heart.getResources().getDisplayMetrics().scaledDensity);
            paint.setShadowLayer(3f * density, 0f, density, 0x66000000);
        }

        /** Into the overlay of the first parent with room for a line above the heart. */
        void place() {
            View h = heart.get();
            if (h == null) return;
            float need = paint.getTextSize() + 6f * density;
            float top = 0f;
            View at = h;
            ViewGroup found = null;
            for (int i = 0; i < 6; i++) {
                top += at.getTop() + at.getTranslationY();
                ViewParent parent = at.getParent();
                if (!(parent instanceof ViewGroup)) break;
                found = (ViewGroup) parent;
                if (top >= need) break;
                at = found;
            }
            ViewGroup old = host.get();
            if (old == found) {
                if (found != null) found.invalidate();
                return;
            }
            if (old != null) old.getOverlay().remove(this);
            host = new WeakReference<>(found);
            if (found != null) {
                setBounds(0, 0, Math.max(1, found.getWidth()), Math.max(1, found.getHeight()));
                found.getOverlay().add(this);
                found.invalidate();
            }
        }

        @Override
        public void draw(Canvas canvas) {
            View h = heart.get();
            ViewGroup g = host.get();
            Object p = post.get();
            if (h == null || g == null || p == null || !h.isShown() || h.getAlpha() <= 0f) return;
            long n = count(p);
            if (n < 0) return;
            if (getBounds().width() != g.getWidth() || getBounds().height() != g.getHeight())
                setBounds(0, 0, g.getWidth(), g.getHeight());
            // where the heart is in the host, its own moves included
            float x = 0f, y = 0f;
            for (View v = h; v != null && v != g; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
                x += v.getLeft() + v.getTranslationX();
                y += v.getTop() + v.getTranslationY();
            }
            float cx = x + h.getWidth() / 2f;
            float base = y - 4f * density;
            paint.setAlpha(Math.round(255 * h.getAlpha()));
            canvas.drawText(NumberStyle.LIZ(n), cx, base, paint);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

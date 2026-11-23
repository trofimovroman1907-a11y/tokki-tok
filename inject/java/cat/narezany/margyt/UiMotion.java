package cat.narezany.margyt;
// Идея из SkyTok (автор «небесный»), с его разрешения; написано для Android заново.

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Locale;

/**
 * The interface animations (2.4): TikTok's buttons spring under a finger, a follow button pops when it is pressed,
 * and the rows of comments and chats ease in as they come up.
 *
 * Nothing of TikTok's is replaced: a touch is only looked at on its way (TouchHook), the button it lands on is scaled
 * while held and let go with a little overshoot, and the scale it had is put back. A view TikTok is animating itself
 * (scale not 1 already) is left alone, and so are text fields, sliders, switches and anything as big as a page.
 */
final class UiMotion {
    private UiMotion() {}

    static final String KEY_PRESS = "fx_press", KEY_FOLLOW = "fx_follow", KEY_LISTS = "fx_lists", KEY_STRENGTH = "fx_strength";
    static final String[] STRENGTHS = {"Мягко", "Обычно", "Сильно"};
    private static final float[] DEPTH = {0.94f, 0.90f, 0.84f};
    private static final int LISTENING = 0x7e0c0372;

    private static SharedPreferences prefs() {
        Context c = Margy.context();
        return c == null ? null : c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    static boolean on(String key) {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(key, true);
    }

    static void set(String key, boolean value) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putBoolean(key, value).apply();
    }

    static int strength() {
        SharedPreferences p = prefs();
        return p == null ? 1 : Math.max(0, Math.min(2, p.getInt(KEY_STRENGTH, 1)));
    }

    static void setStrength(int value) {
        SharedPreferences p = prefs();
        if (p != null) p.edit().putInt(KEY_STRENGTH, Math.max(0, Math.min(2, value))).apply();
    }

    /** The phone's own "remove animations": then nothing moves here either. */
    private static boolean systemAllows(Context c) {
        try {
            return android.provider.Settings.Global.getFloat(c.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static WeakReference<View> pressed = new WeakReference<>(null);
    private static float downX, downY;

    /** Every touch of a TikTok screen, before TikTok sees it (TouchHook). */
    static void touch(Activity activity, MotionEvent event) {
        boolean press = on(KEY_PRESS), follow = on(KEY_FOLLOW), lists = on(KEY_LISTS);
        if (!press && !follow && !lists) return;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!systemAllows(activity)) return;
                View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
                if (root == null) return;
                downX = event.getRawX();
                downY = event.getRawY();
                View target = clickableAt(root, root, downX, downY, 0);
                if (target == null) return;
                if (lists) listen(target);
                if (press && springy(root, target)) {
                    pressed = new WeakReference<>(target);
                    float depth = DEPTH[strength()];
                    target.animate().cancel();
                    target.animate().scaleX(depth).scaleY(depth).setDuration(110L)
                            .setInterpolator(Motion.OUT).start();
                } else {
                    pressed = new WeakReference<>(follow && isFollow(target) ? target : null);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                View target = pressed.get();
                // a scroll is not a press: the button goes back at once
                if (target != null && Math.hypot(event.getRawX() - downX, event.getRawY() - downY) > 24 * target.getResources().getDisplayMetrics().density) {
                    release(target, false);
                    pressed = new WeakReference<>(null);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                View target = pressed.get();
                pressed = new WeakReference<>(null);
                if (target != null) release(target, event.getActionMasked() == MotionEvent.ACTION_UP && follow && isFollow(target));
                break;
            }
            default:
                break;
        }
    }

    /** Back to its own size with a small overshoot; a follow button pops past it first. */
    private static void release(View target, boolean pop) {
        target.animate().cancel();
        if (pop) {
            target.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            target.animate().scaleX(1.14f).scaleY(1.14f).setDuration(130L).setInterpolator(Motion.OUT)
                    .withEndAction(() -> target.animate().scaleX(1f).scaleY(1f).setDuration(260L)
                            .setInterpolator(new OvershootInterpolator(2.4f)).start()).start();
            return;
        }
        target.animate().scaleX(1f).scaleY(1f).setDuration(320L).setInterpolator(new OvershootInterpolator(2.2f + strength())).start();
    }

    /** A button worth springing: clickable, not huge, not a field or a slider, and not scaled by TikTok already. */
    private static boolean springy(View root, View view) {
        if (view instanceof EditText || view instanceof SeekBar || view instanceof CompoundButton) return false;
        if (view.getScaleX() != 1f || view.getScaleY() != 1f) return false;
        long area = (long) view.getWidth() * view.getHeight(), screen = (long) root.getWidth() * root.getHeight();
        return area > 0 && area < screen / 6;
    }

    private static boolean isFollow(View view) {
        CharSequence said = view instanceof TextView ? ((TextView) view).getText() : view.getContentDescription();
        if (said == null && view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && said == null; i++)
                if (group.getChildAt(i) instanceof TextView) said = ((TextView) group.getChildAt(i)).getText();
        }
        if (said == null) return false;
        String text = said.toString().toLowerCase(Locale.ROOT).trim();
        return text.equals("подписаться") || text.equals("follow") || text.equals("підписатися")
                || text.equals("подписаться в ответ") || text.equals("follow back");
    }

    /** The deepest clickable view under a point. */
    private static View clickableAt(View root, View view, float x, float y, int depth) {
        if (depth > 60 || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f) return null;
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        if (x < at[0] || y < at[1] || x >= at[0] + view.getWidth() * view.getScaleX() || y >= at[1] + view.getHeight() * view.getScaleY()) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View found = clickableAt(root, group.getChildAt(i), x, y, depth + 1);
                if (found != null) return found;
            }
        }
        return view != root && (view.isClickable() || view.isLongClickable()) ? view : null;
    }

    // ------------------------------------------------------------------ rows easing in

    /**
     * The list a touched row belongs to (comments, chats): from now on the rows it brings onto the screen come in with
     * a short fade and rise. Only lists of whole-width rows; grids and the feed itself are left as they are.
     */
    private static void listen(View touched) {
        for (View v = touched; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (!recycler(v)) continue;
            final ViewGroup list = (ViewGroup) v;
            if (list.getTag(LISTENING) != null) return;
            list.setTag(LISTENING, Boolean.TRUE);
            try {
                Class<?> listener = Class.forName("androidx.recyclerview.widget.RecyclerView$OnChildAttachStateChangeListener");
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p, m, a) -> {
                    if (m.getName().equals("onChildViewAttachedToWindow") && a != null && a[0] instanceof View) easeIn(list, (View) a[0]);
                    if (m.getName().equals("equals")) return p == a[0];
                    if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                    return null;
                });
                list.getClass().getMethod("addOnChildAttachStateChangeListener", listener).invoke(list, proxy);
            } catch (Throwable error) {
                Diary.note("ui motion lists: " + error);
            }
            return;
        }
    }

    private static void easeIn(ViewGroup list, View row) {
        if (!on(KEY_LISTS) || row.getWidth() > 0 && row.getWidth() < list.getWidth() * 0.8f) return;
        float rise = 14 * row.getResources().getDisplayMetrics().density * (strength() + 1) / 2f;
        row.setAlpha(0f);
        row.setTranslationY(rise);
        row.animate().alpha(1f).translationY(0f).setDuration(260L).setInterpolator(Motion.OUT).start();
    }

    private static boolean recycler(View view) {
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass())
            if (type.getName().equals("androidx.recyclerview.widget.RecyclerView")) return true;
        return false;
    }
}

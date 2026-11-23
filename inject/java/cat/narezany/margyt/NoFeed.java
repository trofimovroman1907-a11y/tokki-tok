package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/**
 * No recommendations (2.4, in the silly things): the Home tab, and the For You feed behind it, are gone from the bar
 * at the bottom, and TikTok opens on the tab next to it instead.
 *
 * Nothing of TikTok's is taken out: the first tab of the bar is only not shown, and whenever TikTok selects it anyway
 * (it starts there) the next tab is pressed for it. Turned off, the tab is back as it was.
 */
final class NoFeed {
    private NoFeed() {}

    static final String KEY = "fun_no_feed";
    private static final int TRIES = 12;
    private static final long BETWEEN = 250L;
    private static volatile int known = -1;
    private static WeakReference<View> hidden = new WeakReference<>(null);
    private static WeakReference<ViewGroup> bar = new WeakReference<>(null);
    private static long pressed;

    static boolean on() {
        int k = known;
        if (k < 0) {
            Context c = Margy.context();
            if (c == null) return false;
            k = c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) ? 1 : 0;
            known = k;
        }
        return k == 1;
    }

    static void set(boolean value) {
        known = value ? 1 : 0;
        Context c = Margy.context();
        if (c != null) c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply();
        if (!value) {
            View home = hidden.get();
            if (home != null) home.post(() -> home.setVisibility(View.VISIBLE));
            hidden = new WeakReference<>(null);
        }
    }

    /** The main screen came up (SettingsRow): the bar is found once it is built, and kept watch over. */
    static void attach(Activity activity) {
        if (!on() || activity == null || activity.getWindow() == null) return;
        look(activity.getWindow().getDecorView(), 0);
    }

    private static void look(final View root, final int attempt) {
        try {
            if (!on()) return;
            ViewGroup found = find(root, root, 0);
            if (found == null) {
                if (attempt < TRIES) root.postDelayed(() -> look(root, attempt + 1), BETWEEN);
                return;
            }
            if (bar.get() != found) {
                bar = new WeakReference<>(found);
                found.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        ViewGroup b = bar.get();
                        if (b == null || !on()) {
                            if (b != null) b.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        }
                        keep(b);
                        return true;
                    }
                });
            }
            keep(found);
        } catch (Throwable error) {
            Diary.note("no feed: " + error);
        }
    }

    /** The home tab hidden, and never the one selected. */
    private static void keep(ViewGroup b) {
        if (b.getChildCount() < 3) return;
        View home = b.getChildAt(0);
        if (home.getVisibility() != View.GONE) {
            home.setVisibility(View.GONE);
            hidden = new WeakReference<>(home);
        }
        if (home.isSelected()) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - pressed < 600L) return;
            pressed = now;
            for (int i = 1; i < b.getChildCount(); i++) {
                View next = b.getChildAt(i);
                // the next tab with a name: the middle "+" opens the camera, and is not a place to land on
                if (next.getVisibility() == View.VISIBLE && next.isClickable() && named(next)) {
                    next.post(next::performClick);
                    return;
                }
            }
        }
    }

    private static boolean named(View tab) {
        if (!(tab instanceof ViewGroup)) return false;
        ViewGroup g = (ViewGroup) tab;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof android.widget.TextView) {
                CharSequence t = ((android.widget.TextView) c).getText();
                if (t != null && t.length() > 0 && TabCount.of(t) == 0) return true;
            }
        }
        return false;
    }

    private static ViewGroup find(View view, View root, int depth) {
        if (!(view instanceof ViewGroup) || depth > 30 || view.getVisibility() != View.VISIBLE) return null;
        ViewGroup group = (ViewGroup) view;
        if (isBar(group, root)) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = find(group.getChildAt(i), root, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    /** As NavBar knows the bar: a row of clickable groups across the bottom, one of them selected. */
    private static boolean isBar(ViewGroup group, View root) {
        int wide = group.getWidth(), tall = group.getHeight();
        if (tall <= 0 || wide < root.getWidth() * 0.9f || tall > root.getHeight() * 0.15f) return false;
        int[] at = new int[2], origin = new int[2];
        group.getLocationOnScreen(at);
        root.getLocationOnScreen(origin);
        if (at[1] - origin[1] < root.getHeight() * 0.8f) return false;
        int tabs = 0, named = 0, selected = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ViewGroup) || !child.isClickable()) return false;
            tabs++;
            if (named(child)) named++;
            if (child.isSelected()) selected++;
        }
        return tabs >= 3 && tabs <= 6 && named >= 2 && selected == 1;
    }
}

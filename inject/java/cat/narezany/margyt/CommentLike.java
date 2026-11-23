package cat.narezany.margyt;
// Из ttcuz (автор tremorttcuz), перенесено в MargyT с разрешения автора.

import android.content.Context;
import java.lang.reflect.Method;

/** Enables TikTok's own double-tap gesture on comment rows. */
public final class CommentLike {
    private static final String KEY = "comment_double_tap_like";
    private static volatile Method nativeEnabled;

    private CommentLike() {}

    /**
     * The double-tap window TikTok's comment gesture waits for. It comes from the experiment's config, which is not
     * there for an account the experiment was not given to: then TikTok has -1, and no two taps are ever close enough.
     */
    public static Long interval(long fromConfig) {
        return fromConfig > 0 ? fromConfig : (long) android.view.ViewConfiguration.getDoubleTapTimeout() + 60L;
    }

    static boolean enabled() {
        Context context = Margy.context();
        return context != null && context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    static void set(boolean on) {
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    /** Always on since 2.4: no switch for it any more. */
    public static boolean LIZ() {
        if (true) return true;
        try {
            Method method = nativeEnabled;
            if (method == null) {
                method = Class.forName("X.0oOe").getMethod("LIZ");
                nativeEnabled = method;
            }
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException error) {
            Diary.note("comment double tap: " + error);
            return false;
        }
    }
}

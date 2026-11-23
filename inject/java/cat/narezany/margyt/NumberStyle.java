package cat.narezany.margyt;
// Из ttcuz (автор tremorttcuz), перенесено в MargyT с разрешения автора.

import android.content.Context;

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Counts written out: "1 234 567" in place of "1,2 млн".
 *
 * Two helpers in the target APK shorten counts: X.17GB.LIZ (localised) and X.16FO.LIZ / LIZIZ (K, M, B).
 * Three formats, chosen in the settings:
 *   0  as TikTok shortens them;
 *   1  always in full, grouped the way the phone's language groups thousands;
 *   2  in full below a million, shortened from there (the buttons beside a video stay narrow).
 * Only whole numbers are written out; a fraction (a rating, a percentage) is left to TikTok.
 */
public final class NumberStyle {
    private NumberStyle() {}

    static final String KEY = "numbers_style";
    private static volatile int style = -1;

    static int style() {
        int known = style;
        if (known >= 0) return known;
        Context context = Margy.context();
        if (context == null) return 0;
        int value = context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getInt(KEY, 0);
        style = Math.max(0, Math.min(2, value));
        return style;
    }

    static void setStyle(int value) {
        style = Math.max(0, Math.min(2, value));
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, style).apply();
    }

    static String[] names() {
        return new String[]{Local.of("Как в TikTok (1,2 тыс.)"), Local.of("Полностью (1 234)"), Local.of("Полностью до миллиона")};
    }

    /** The count written out, or null when TikTok's own short form is wanted for it. */
    private static String full(Number number) {
        int how = style();
        if (how == 0 || number == null) return null;
        double value = number.doubleValue();
        if (Double.isNaN(value) || Double.isInfinite(value) || value != Math.rint(value)) return null;
        long whole = number.longValue();
        if (how == 2 && Math.abs(whole) >= 1_000_000L) return null;
        if (Math.abs(whole) < 1000) return Long.toString(whole);
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.getDefault());
        format.setGroupingUsed(true);
        // a no-break space between the groups, so a count never wraps in the middle
        return format.format(whole).replace(' ', ' ').replace(' ', ' ');
    }

    private static volatile Method localised, shortK, shortKAlt;

    /** X.17GB.LIZ(number, style, ...): the localised short count. */
    public static CharSequence LIZ(Number number, Object format, int a, Boolean b, int c) {
        String written = full(number);
        if (written != null) return written;
        try {
            Method method = localised;
            if (method == null) {
                method = Class.forName("X.17GB").getMethod("LIZ", Number.class, Class.forName("X.17CR"),
                        int.class, Boolean.class, int.class);
                localised = method;
            }
            return (CharSequence) method.invoke(null, number, format, a, b, c);
        } catch (Throwable error) {
            Diary.note("numbers: " + error);
            return String.valueOf(number);
        }
    }

    /** X.16FO.LIZIZ(count): "1.2K". */
    public static String LIZIZ(long count) { return k("LIZIZ", count); }

    /** X.16FO.LIZ(count). */
    public static String LIZ(long count) { return k("LIZ", count); }

    private static String k(String name, long count) {
        String written = full(count);
        if (written != null) return written;
        try {
            Method method = name.equals("LIZ") ? shortKAlt : shortK;
            if (method == null) {
                method = Class.forName("X.16FO").getMethod(name, long.class);
                if (name.equals("LIZ")) shortKAlt = method; else shortK = method;
            }
            return (String) method.invoke(null, count);
        } catch (Throwable error) {
            Diary.note("numbers: " + error);
            return Long.toString(count);
        }
    }
}

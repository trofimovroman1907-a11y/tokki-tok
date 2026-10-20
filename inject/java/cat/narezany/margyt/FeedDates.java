package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Leaving posts out of the feed by when they were made.
 *
 * Two ways, and they mean different things. "No older than" is counted back
 * from now, so a day is always the last twenty-four hours. "Between" is fixed
 * dates on the calendar, so 2024 stays 2024 whatever today is. A person's own
 * posts and live rooms are never left out.
 */
final class FeedDates {

    private FeedDates() {}

    static final int OFF = 0, RECENT = 1, BETWEEN = 2;
    static final int HOURS = 0, DAYS = 1, WEEKS = 2, MONTHS = 3, YEARS = 4;

    private static final String MODE = "date_filter_mode", AMOUNT = "date_filter_amount", UNIT = "date_filter_unit",
            FROM = "date_filter_from", TO = "date_filter_to";

    private static volatile long[] rule;

    static int mode() { SharedPreferences p = prefs(); return p == null ? OFF : p.getInt(MODE, OFF); }
    static int amount() { SharedPreferences p = prefs(); return p == null ? 1 : Math.max(1, p.getInt(AMOUNT, 1)); }
    static int unit() { SharedPreferences p = prefs(); return p == null ? DAYS : p.getInt(UNIT, DAYS); }

    /** Start of the first day, in seconds. Defaults to the first of January of last year. */
    static long from() {
        SharedPreferences p = prefs();
        long saved = p == null ? 0 : p.getLong(FROM, 0);
        if (saved > 0) return saved;
        Calendar c = Calendar.getInstance();
        c.set(c.get(Calendar.YEAR) - 1, Calendar.JANUARY, 1, 0, 0, 0);
        return c.getTimeInMillis() / 1000L;
    }

    /** End of the last day, in seconds. Defaults to the last of December of last year. */
    static long to() {
        SharedPreferences p = prefs();
        long saved = p == null ? 0 : p.getLong(TO, 0);
        if (saved > 0) return saved;
        Calendar c = Calendar.getInstance();
        c.set(c.get(Calendar.YEAR) - 1, Calendar.DECEMBER, 31, 23, 59, 59);
        return c.getTimeInMillis() / 1000L;
    }

    static void setMode(int mode) { edit().putInt(MODE, mode).apply(); rule = null; }
    static void setRecent(int amount, int unit) { edit().putInt(AMOUNT, Math.max(1, amount)).putInt(UNIT, unit).apply(); rule = null; }
    static void setFrom(int year, int month, int day) {
        Calendar c = Calendar.getInstance(); c.set(year, month, day, 0, 0, 0);
        edit().putLong(FROM, c.getTimeInMillis() / 1000L).apply(); rule = null;
    }
    static void setTo(int year, int month, int day) {
        Calendar c = Calendar.getInstance(); c.set(year, month, day, 23, 59, 59);
        edit().putLong(TO, c.getTimeInMillis() / 1000L).apply(); rule = null;
    }
    /** One whole calendar year. */
    static void setYear(int year) { setFrom(year, Calendar.JANUARY, 1); setTo(year, Calendar.DECEMBER, 31); }

    static boolean active() { return mode() != OFF; }

    /** Whether a post made at `created` (seconds) is outside what the person asked for. */
    static boolean leavesOut(long created) {
        if (created <= 0) return false;
        if (created > 100000000000L) created /= 1000L;
        long[] r = rule;
        if (r == null) { r = make(); rule = r; }
        if (r[0] == OFF) return false;
        if (r[0] == RECENT) return System.currentTimeMillis() / 1000L - created > r[1];
        return created < r[1] || created > r[2];
    }

    private static long[] make() {
        int mode = mode();
        if (mode == RECENT) return new long[]{RECENT, span(amount(), unit())};
        if (mode == BETWEEN) return new long[]{BETWEEN, Math.min(from(), to()), Math.max(from(), to())};
        return new long[]{OFF};
    }

    private static long span(int amount, int unit) {
        long one = unit == HOURS ? 3600L : unit == DAYS ? 86400L : unit == WEEKS ? 7 * 86400L
                : unit == MONTHS ? 30 * 86400L : 365 * 86400L;
        return amount * one;
    }

    static String date(long seconds) {
        return new java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(new java.util.Date(seconds * 1000L));
    }

    /** What the filter is set to, in a few words. */
    static String describe() {
        int mode = mode();
        if (mode == RECENT) return Text.dateFilterRecent(amount(), unit());
        if (mode == BETWEEN) return date(Math.min(from(), to())) + " – " + date(Math.max(from(), to()));
        return Text.DATE_FILTER_OFF;
    }

    private static SharedPreferences prefs() {
        Context c = Margy.context();
        return c == null ? null : c.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor edit() { return prefs().edit(); }
}

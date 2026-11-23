package cat.narezany.margyt;
// Из ttcuz (автор tremorttcuz), перенесено в MargyT с разрешения автора.

/**
 * TikTok's TabChangeManager writes two lines to its own log on every switch of the bottom tabs ("isSafeTabHost: ...",
 * "changeTab: last: ..."); nobody reads them on a phone. The build sends those two calls here, where they end.
 */
public final class TabChange {
    private TabChange() {}

    /** X.0VdL.LIZ(message, extra): the tab log. */
    public static void LIZ(Object log, String message, Object extra) {}

    /** X.051c.LJI(tag, message): the feed tab's log. */
    public static void LJI(String tag, String message) {}
}

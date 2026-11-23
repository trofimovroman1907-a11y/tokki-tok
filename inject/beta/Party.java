package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.view.View;

/** Not in this build. */
public final class Party {
    private Party() {}

    static boolean open() { return false; }
    static String title() { return ""; }
    static String note() { return ""; }
    static void play(Context c) {}
    static View letter(Activity a, Runnable gone) { return null; }
    static void invitation(java.util.List<Mailbox.Letter> out) {}
    static boolean opened(Activity a, String id) { return false; }
    static void trimMemory() {}
}

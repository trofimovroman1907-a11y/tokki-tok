package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The mailbox at the top of the settings (2.3.1): every letter the mod has had, the ones put away with the cross
 * too. The birthday invitation is one; the rest come from the server, to everybody or to some accounts, and the
 * owner writes them from here (admin_mod, ops mail_*).
 *
 * A dot on the button while something is unread. A letter that comes while TikTok is open says so once.
 */
final class Mailbox {
    private Mailbox() {}

    private static final String PREFS = "margyt_mail";
    private static final long AGAIN = 10 * 60_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Letter {
        final String id, title, body, from;
        final long sent;
        Letter(String id, String title, String body, String from, long sent) {
            this.id = id; this.title = title; this.body = body; this.from = from; this.sent = sent;
        }
    }

    private static SharedPreferences prefs() {
        return Margy.context().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ what is in it

    static List<Letter> all() {
        List<Letter> out = new ArrayList<>();
        try {
            JSONArray kept = new JSONArray(prefs().getString("letters", "[]"));
            for (int i = 0; i < kept.length(); i++) {
                JSONObject one = kept.optJSONObject(i);
                if (one == null) continue;
                out.add(new Letter(String.valueOf(one.optLong("id")), one.optString("title"), one.optString("body"),
                        one.optString("from"), one.optLong("sent") * 1000L));
            }
        } catch (Throwable error) {
            Diary.note("mail: " + error);
        }
        Party.invitation(out);
        return out;
    }

    private static Set<String> read() {
        try {
            return new HashSet<>(prefs().getStringSet("read", new HashSet<>()));
        } catch (Throwable ignored) {
            return new HashSet<>();
        }
    }

    static boolean unread() {
        Set<String> read = read();
        for (Letter one : all()) if (!read.contains(one.id)) return true;
        return false;
    }

    static void markRead(String id) {
        try {
            Set<String> read = read();
            if (read.add(id)) prefs().edit().putStringSet("read", read).apply();
        } catch (Throwable ignored) {
        }
        dots();
    }

    // ------------------------------------------------------------ the server

    /** Asks for new letters, at most every ten minutes unless `now`. */
    static void refresh(boolean now) {
        long last = prefs().getLong("fetched", 0);
        if (!now && System.currentTimeMillis() - last < AGAIN) return;
        prefs().edit().putLong("fetched", System.currentTimeMillis()).apply();
        final String uid = Account.id();
        Net.away("mail", () -> {
            try {
                Net.Said said = Net.talk(Badges.SERVER + "/mail", new JSONObject()
                        .put("uid", uid == null ? "" : uid).put("version", Version.MOD).toString());
                if (!said.ok()) return;
                JSONArray mail = new JSONObject(said.body).optJSONArray("mail");
                if (mail == null) return;
                prefs().edit().putString("letters", mail.toString()).apply();
                long newest = 0;
                for (int i = 0; i < mail.length(); i++) newest = Math.max(newest, mail.optJSONObject(i).optLong("id"));
                long told = prefs().getLong("told", -1);
                if (told < 0) {
                    // the first time: what was already there is not news
                    prefs().edit().putLong("told", newest).apply();
                } else if (newest > told) {
                    prefs().edit().putLong("told", newest).apply();
                    if (!read().contains(String.valueOf(newest))) MAIN.post(() -> Screen.say(Local.of("Новое письмо в почтовом ящике MargyT")));
                }
                MAIN.post(Mailbox::dots);
            } catch (Throwable error) {
                Diary.note("mail: " + error);
            }
        });
    }

    /** At start: once TikTok has settled, then the regular ten minutes from the settings. */
    static void start() {
        if (Version.LITE) return;
        MAIN.postDelayed(() -> refresh(true), 15_000L);
    }

    // ------------------------------------------------------------ the button

    private static final List<WeakReference<Dot>> buttons = new ArrayList<>();

    /** The envelope for the app bar, with a dot while something is unread. */
    static View button(final Activity a, int ink, int size) {
        Dot dot = new Dot(a);
        ImageView glyph = new ImageView(a);
        glyph.setImageDrawable(SettingsGlyph.make("mail", ink));
        int pad = Math.round(size * 0.25f);
        glyph.setPadding(pad, pad, pad, pad);
        dot.addView(glyph, new FrameLayout.LayoutParams(size, size));
        dot.setContentDescription(Local.of("Почтовый ящик"));
        dot.setOnClickListener(v -> open(a));
        Motion.press(dot);
        dot.on = unread();
        buttons.add(new WeakReference<>(dot));
        refresh(false);
        return dot;
    }

    private static void dots() {
        boolean on = unread();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Dot dot = buttons.get(i).get();
            if (dot == null) { buttons.remove(i); continue; }
            if (dot.on != on) { dot.on = on; dot.invalidate(); }
        }
    }

    private static final class Dot extends FrameLayout {
        boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Dot(Context c) { super(c); setWillNotDraw(false); }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (!on) return;
            float d = getResources().getDisplayMetrics().density, r = 4.5f * d;
            float x = getWidth() * 0.73f, y = getHeight() * 0.3f;
            paint.setColor(Accent.colour() | 0xFF000000);
            canvas.drawCircle(x, y, r, paint);
        }
    }

    // ------------------------------------------------------------ the list

    static void open(final Activity a) {
        final Panel panel = Panel.with(a, Skin.remembered(a), Local.of("Почтовый ящик"));
        List<Letter> letters = all();
        Set<String> read = read();
        if (letters.isEmpty()) panel.text(Local.of("Писем пока нет. Когда Нарезанный что-нибудь напишет, оно придёт сюда."));
        else panel.section(null);
        for (final Letter one : letters) {
            boolean fresh = !read.contains(one.id);
            String title = one.title != null && one.title.length() > 0 ? one.title : firstLine(one.body);
            String detail = (fresh ? Local.of("Новое") + " · " : "") + date(one.sent);
            panel.item(fresh ? "mail" : "drafts", title, detail, () -> letter(a, one));
        }
        if (AdminPanel.owner()) {
            panel.section(Local.of("Админка"));
            panel.item("chat", Local.of("Написать письмо"), null, () -> write(a));
            panel.item("inbox", Local.of("Отправленные"), null, () -> sent(a));
        }
        panel.quiet(Text.CLOSE, null).show();
        refresh(false);
    }

    private static String firstLine(String body) {
        if (body == null) return "";
        String line = body.trim();
        int cut = line.indexOf('\n');
        if (cut > 0) line = line.substring(0, cut);
        return line.length() > 40 ? line.substring(0, 40) + "…" : line;
    }

    private static String date(long ms) {
        try {
            return android.text.format.DateFormat.format("d MMMM", ms).toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    static void letter(Activity a, Letter one) {
        if (Party.opened(a, one.id)) return;
        markRead(one.id);
        Paper.show(a, date(one.sent), one.title, one.body, one.from, Text.CLOSE, null, null);
    }

    // ------------------------------------------------------------ the owner's

    private static void write(final Activity a) {
        final Panel panel = Panel.with(a, Skin.remembered(a), Local.of("Написать письмо"));
        panel.text(Local.of("Кому: id аккаунтов через пробел, пусто значит всем. Версия: например 2.3, пусто значит всем. Срок в днях, пусто значит навсегда."));
        final android.widget.EditText to = panel.field(Local.of("Кому (пусто = всем)"));
        final android.widget.EditText title = panel.field(Local.of("Заголовок"));
        final android.widget.EditText body = panel.field(Local.of("Текст письма"));
        body.setSingleLine(false);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(4);
        body.setMaxLines(12);
        body.setGravity(Gravity.TOP | Gravity.START);
        title.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        final android.widget.EditText from = panel.field(Local.of("Подпись"));
        from.setText(Local.of("Нарезанный"));
        final android.widget.EditText version = panel.field(Local.of("Версия (пусто = все)"));
        final android.widget.EditText days = panel.field(Local.of("Срок в днях (пусто = навсегда)"));
        days.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        panel.primaryKeepOpen(Local.of("Отправить"), () -> {
            if (body.getText().toString().trim().length() == 0) { panel.busy(false, Local.of("Письмо пустое")); return; }
            panel.busy(true, null);
            Net.away("mail send", () -> {
                String trouble = null;
                try {
                    String d = days.getText().toString().trim();
                    AdminDevice.act("mail_send", new JSONObject()
                            .put("to", to.getText().toString().trim())
                            .put("title", title.getText().toString().trim())
                            .put("body", body.getText().toString())
                            .put("from", from.getText().toString().trim())
                            .put("version", version.getText().toString().trim())
                            .put("days", d.length() == 0 ? 0 : Integer.parseInt(d)));
                } catch (AdminDevice.Refused refused) {
                    trouble = "target".equals(refused.why) ? Local.of("Кому: только цифры id") : Text.ADMIN_FAILED;
                } catch (Throwable error) {
                    Diary.note("mail send: " + error);
                    trouble = Text.ADMIN_FAILED;
                }
                final String said = trouble;
                MAIN.post(() -> {
                    if (said != null) { panel.busy(false, said); return; }
                    panel.close();
                    Screen.say(Local.of("Письмо отправлено"));
                    refresh(true);
                });
            });
        });
        panel.quiet(Text.CANCEL, null).show();
    }

    private static void sent(final Activity a) {
        Net.away("mail sent", () -> {
            try {
                JSONObject said = AdminDevice.act("mail_list", null);
                MAIN.post(() -> listSent(a, said.optJSONArray("mail")));
            } catch (Throwable error) {
                Diary.note("mail sent: " + error);
                MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
            }
        });
    }

    private static void listSent(final Activity a, JSONArray list) {
        Panel panel = Panel.with(a, Skin.remembered(a), Local.of("Отправленные"));
        if (list == null || list.length() == 0) panel.text(Local.of("Пока ничего не отправлено."));
        else panel.section(null);
        if (list != null) for (int i = 0; i < list.length(); i++) {
            final JSONObject one = list.optJSONObject(i);
            if (one == null) continue;
            String to = one.optString("to");
            String version = one.optString("version");
            String title = one.optString("title");
            if (title.length() == 0) title = firstLine(one.optString("body"));
            String detail = date(one.optLong("sent") * 1000L) + " · " + (to.length() == 0 ? Local.of("всем") : to)
                    + (version.length() > 0 ? " · " + version : "");
            final String name = title;
            panel.item("mail", title, detail, "close", () -> {
                Panel sure = Panel.with(a, Skin.remembered(a), Local.of("Удалить письмо?")).text(
                        name + "\n\n" + Local.of("Оно пропадёт из почтовых ящиков у всех."));
                sure.primary(Local.of("Удалить"), () -> Net.away("mail drop", () -> {
                    try {
                        JSONObject left = AdminDevice.act("mail_drop", new JSONObject().put("id", one.optLong("id")));
                        refresh(true);
                        MAIN.post(() -> listSent(a, left.optJSONArray("mail")));
                    } catch (Throwable error) {
                        Diary.note("mail drop: " + error);
                        MAIN.post(() -> Screen.say(Text.ADMIN_FAILED));
                    }
                }));
                sure.quiet(Text.CANCEL, null).show();
            });
        }
        panel.quiet(Text.CLOSE, null).show();
    }
}

package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

/**
 * Нарезанный's birthday, 24 November (MargyT 2.3.1 Birthday Bash): an envelope at the top of the settings that
 * opens a letter inviting to the party, the party itself (the game, PartyActivity, also in the silly things for
 * good), and a cake instead of the like button on his own videos for the whole day. Everything waits for the day
 * itself, Moscow time; a preview build (MARGYT_PARTY_PREVIEW=1) has it all now.
 */
public final class Party {
    private Party() {}

    /** 24.11.2026 00:00 and 25.11.2026 00:00, Moscow time, and 27.11 00:00 when the invitation runs out. */
    static final long OPENS = 1795467600000L, CAKE_ENDS = 1795554000000L, LETTER_ENDS = 1795726800000L;
    private static final String KEY_LETTER = "party_letter_closed";
    /** His handle: the videos that get a cake. */
    private static final String OWNER = "narezany";

    static boolean preview() { return Version.PARTY_PREVIEW; }

    /** The game: from the day on, for good. */
    static boolean open() { return !Version.LITE && (preview() || System.currentTimeMillis() >= OPENS); }

    /** The invitation has run out: the game is still there, the hat is not given any more. */
    static boolean expired() { return !preview() && System.currentTimeMillis() >= LETTER_ENDS; }

    /** The cake on his videos: the birthday itself, all day. */
    static boolean cakeDay() {
        long now = System.currentTimeMillis();
        return !Version.LITE && (preview() || (now >= OPENS && now < CAKE_ENDS));
    }

    /** Into the game; it wants a proved account, as the badges do, and says where to get one. */
    static void play(Context c) {
        try {
            if (!Proof.proved()) {
                Popup.ask(c, Text.pick("Сначала подтверди аккаунт", "Спочатку підтверди акаунт", "Verify your account first"),
                        Text.pick("На праздник пускают только с подтверждённым аккаунтом TikTok, как и за значками. Это минута: мод проверит, что аккаунт твой.", "На свято пускають тільки з підтвердженим акаунтом TikTok, як і по значки. Це хвилина: мод перевірить, що акаунт твій.", "The party lets in only verified TikTok accounts, as the badges do. It takes a minute: the mod checks the account is yours."),
                        Text.pick("Подтвердить", "Підтвердити", "Verify"), () -> {
                            Intent i = new Intent(c, SettingsActivity.class).putExtra("margyt_prove", true);
                            if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            c.startActivity(i);
                        },
                        Text.CANCEL, null, null, null);
                return;
            }
            Intent i = new Intent(c, PartyActivity.class);
            if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable error) {
            Diary.note("party: " + error);
        }
    }

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static Typeface hand(Context c) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "margyt/fonts/caveat.ttf");
        } catch (Throwable error) {
            return Typeface.SERIF;
        }
    }

    private static final int PAPER = 0xFFF6EFE1, PAPER_EDGE = 0xFFD9CDB4, INK = 0xFF2E2A33, FLAP = 0xFFE5D9BF, WAX = 0xFFB02A2A;

    // ------------------------------------------------------------ the envelope in the settings

    /** An envelope under the title of the settings. A tap opens the letter; the cross puts it away for good. */
    static View letter(final Activity a, final Runnable gone) {
        if (!open() || expired()) return null;
        try {
            if (a.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LETTER, false)) return null;
        } catch (Throwable ignored) {
        }
        final FrameLayout card = new FrameLayout(a);
        final Envelope drawn = new Envelope(a, hand(a));
        card.addView(drawn, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 118)));
        TextView close = new TextView(a);
        close.setText("✕");
        close.setTextSize(14);
        close.setTextColor(0x99403A30);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription(Text.pick("Убрать письмо", "Прибрати лист", "Put the letter away"));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(a, 36), dp(a, 36), Gravity.START | Gravity.TOP);
        card.addView(close, cp);
        close.setOnClickListener(v -> {
            try {
                a.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_LETTER, true).apply();
                Mailbox.markRead(LETTER_ID);
            } catch (Throwable ignored) {
            }
            card.animate().alpha(0f).translationY(-dp(a, 8)).setDuration(180).withEndAction(() -> {
                ViewGroup parent = (ViewGroup) card.getParent();
                if (parent != null) parent.removeView(card);
                if (gone != null) gone.run();
            }).start();
        });
        card.setOnClickListener(v -> postcard(a));
        card.setContentDescription(Text.pick("Письмо от Нарезанного", "Лист від Нарізаного", "A letter from Narezany"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(a, 16), dp(a, 4), dp(a, 16), dp(a, 12));
        card.setLayoutParams(lp);
        return card;
    }

    /** A paper envelope seen from the back: flap, wax seal, a stamp, who it is for. */
    private static final class Envelope extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pix = new Paint();
        private final Typeface hand;
        private final Path path = new Path();

        Envelope(Context c, Typeface hand) {
            super(c);
            this.hand = hand;
            pix.setFilterBitmap(false);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), d = getResources().getDisplayMetrics().density;
            float r = 4 * d;
            RectF body = new RectF(d, d, w - d, h - 2 * d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x22000000);
            c.drawRoundRect(body.left, body.top + 2 * d, body.right, body.bottom + 2 * d, r, r, p);
            p.setColor(PAPER);
            c.drawRoundRect(body, r, r, p);
            // the side folds and the flap, as lines and a slightly darker triangle
            p.setColor(FLAP);
            path.reset();
            path.moveTo(body.left + r, body.top);
            path.lineTo(body.right - r, body.top);
            path.lineTo(w * 0.5f, body.top + body.height() * 0.55f);
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(d);
            p.setColor(PAPER_EDGE);
            c.drawLine(body.left, body.bottom, w * 0.5f - body.height() * 0.1f, body.top + body.height() * 0.5f, p);
            c.drawLine(body.right, body.bottom, w * 0.5f + body.height() * 0.1f, body.top + body.height() * 0.5f, p);
            c.drawPath(path, p);
            c.drawRoundRect(body, r, r, p);
            // the wax seal where the flap ends
            float sx = w * 0.5f, sy = body.top + body.height() * 0.55f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(WAX);
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                c.drawCircle(sx + (float) Math.cos(a) * 9 * d, sy + (float) Math.sin(a) * 9 * d, 4.5f * d, p);
            }
            c.drawCircle(sx, sy, 11 * d, p);
            p.setColor(0xFF8E1F1F);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * d);
            c.drawCircle(sx, sy, 7.5f * d, p);
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(11 * d);
            p.setColor(0xFFE9B9A8);
            c.drawText("Н", sx, sy + 4 * d, p);
            // a stamp with the cake on it, top right
            float sw = 34 * d, sh = 40 * d, stx = body.right - sw - 10 * d, sty = body.top + 10 * d;
            p.setColor(0xFFFFFFFF);
            c.drawRect(stx, sty, stx + sw, sty + sh, p);
            p.setColor(0xFFE8F4EE);
            c.drawRect(stx + 3 * d, sty + 3 * d, stx + sw - 3 * d, sty + sh - 3 * d, p);
            p.setColor(PAPER);
            for (float x = stx; x <= stx + sw; x += 4 * d) {
                c.drawCircle(x, sty, 1.3f * d, p);
                c.drawCircle(x, sty + sh, 1.3f * d, p);
            }
            for (float y = sty; y <= sty + sh; y += 4 * d) {
                c.drawCircle(stx, y, 1.3f * d, p);
                c.drawCircle(stx + sw, y, 1.3f * d, p);
            }
            Bitmap cake = PartyDeco.sprite("cake_lit_0");
            if (cake != null) {
                float cw = sw - 10 * d, ch = cw * cake.getHeight() / cake.getWidth();
                c.drawBitmap(cake, null, new RectF(stx + 5 * d, sty + sh - ch - 6 * d, stx + 5 * d + cw, sty + sh - 6 * d), pix);
            }
            p.setTextSize(6.5f * d);
            p.setTypeface(Typeface.MONOSPACE);
            p.setColor(0xFF5C8C7A);
            c.drawText("24.11", stx + sw * 0.5f, sty + 10 * d, p);
            // who it is for and from, by hand
            p.setTextAlign(Paint.Align.LEFT);
            p.setTypeface(hand);
            p.setColor(INK);
            p.setTextSize(20 * d);
            c.drawText(Text.pick("Тебе письмо", "Тобі лист", "You've got a letter"), body.left + 16 * d, body.bottom - 30 * d, p);
            p.setTextSize(15 * d);
            p.setColor(0xCC2E2A33);
            c.drawText(Text.pick("от Нарезанного", "від Нарізаного", "from Narezany"), body.left + 18 * d, body.bottom - 12 * d, p);
        }
    }


    // ------------------------------------------------------------ what the rest of the mod asks of the party
    // (a beta for testers is built with an empty Party in place of all of this: MARGYT_NO_PARTY=1, see build.py)

    static String title() { return Text.pick("Нарезанный против Селфкодинга", "Нарізаний проти Селфкодингу", "Narezany vs Selfcoding"); }

    static String note() { return Text.pick("Мини-игра ко дню рождения Нарезанного", "Міні-гра до дня народження Нарізаного", "A mini game for Narezany's birthday"); }

    /** The invitation among the letters of the mailbox, by its date: the 24th. */
    static void invitation(java.util.List<Mailbox.Letter> out) {
        if (!open()) return;
        long when = OPENS;
        Mailbox.Letter party = new Mailbox.Letter(LETTER_ID, Text.pick("Приглашение на день рождения", "Запрошення на день народження", "Birthday invitation"), null, Text.pick("Нарезанный", "Нарізаний", "Narezany"), when);
        int at = 0;
        while (at < out.size() && out.get(at).sent > when) at++;
        out.add(at, party);
    }

    /** The invitation opened from the mailbox; false for any other letter. */
    static boolean opened(Activity a, String id) {
        if (!LETTER_ID.equals(id)) return false;
        postcard(a);
        return true;
    }

    static void trimMemory() { PartyDeco.trimMemory(); }

    static final String LETTER_ID = "party";

    // ------------------------------------------------------------ the letter

    static void postcard(final Activity a) {
        Mailbox.markRead(LETTER_ID);
        Paper.show(a, Text.pick("24 ноября", "24 листопада", "24 November"), null,
                Text.pick("Привет!\n\nУ меня сегодня день рождения, и я зову тебя на праздник. Будут торт, гости со всего района и свинки.\n\n"
                        + "Есть одна проблема: какой-то Селфкодинг забанил меня прямо с утра и, кажется, утащил мой торт. Поможешь разобраться?\n\n"
                        + "Тому, кто дойдёт до свечей, подарю праздничный колпак в профиль.",
                        "Привіт!\n\nУ мене сьогодні день народження, і я кличу тебе на свято. Будуть торт, гості з усього району і свинки.\n\n"
                        + "Є одна проблема: якийсь Селфкодинг забанив мене просто зранку і, здається, поцупив мій торт. Допоможеш розібратися?\n\n"
                        + "Тому, хто дійде до свічок, подарую святковий ковпак у профіль.",
                        "Hi!\n\nIt's my birthday today, and I'm inviting you to the party. There'll be cake, guests from all over the neighbourhood, and piggies.\n\n"
                        + "There's one problem: some Selfcoding banned me first thing this morning and, it seems, ran off with my cake. Will you help sort it out?\n\n"
                        + "Whoever makes it to the candles gets a party hat for their profile."),
                Text.pick("Нарезанный", "Нарізаний", "Narezany"), Text.pick("Потом", "Потім", "Later"), Text.pick("Пойти на праздник", "Піти на свято", "Go to the party"), () -> play(a));
    }
}

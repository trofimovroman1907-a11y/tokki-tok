package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Adverts read out by a video's author, skipped like SponsorBlock skips them on YouTube.
 *
 * People with a proved account mark the part of a video where the advert is; the server takes a
 * part for everyone once several of them have marked about the same stretch (the owner's mark at
 * once). While a video plays, its parts are asked for, drawn red on the bar, and jumped over when
 * the video reaches them, with a moment to go back.
 */
final class SponsorBlock {

    private SponsorBlock() {}

    static final String KEY_SKIP = "sb_skip", KEY_MARKS = "sb_marks";

    static boolean skipOn() {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(KEY_SKIP, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean marksOn() {
        try {
            return AccountAppearance.prefs(Margy.context()).getBoolean(KEY_MARKS, true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void set(String key, boolean on) {
        try {
            AccountAppearance.prefs(Margy.context()).edit().putBoolean(key, on).apply();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ the parts of videos

    /** The parts of each video asked about: start, stop and the part's id, in milliseconds. */
    private static final Map<String, long[][]> known = new LinkedHashMap<String, long[][]>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, long[][]> eldest) {
            return size() > 400;
        }
    };
    private static final Set<String> asking = new HashSet<String>();
    /** Parts the person chose to watch after all. */
    private static final Set<Long> allowed = new HashSet<Long>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String playing = "";
    private static boolean ticking;

    static synchronized long[][] partsOf(String aid) {
        return aid == null ? null : known.get(aid);
    }

    private static synchronized void keep(String aid, long[][] parts) {
        known.put(aid, parts);
    }

    /** A video began to play. */
    static void playing(String aid) {
        if (aid == null || aid.length() == 0) return;
        playing = aid;
        ask(aid, false);
        if (!ticking) {
            ticking = true;
            main.post(tick);
        }
    }

    private static void ask(final String aid, boolean again) {
        synchronized (SponsorBlock.class) {
            if ((!again && known.containsKey(aid)) || !asking.add(aid)) return;
        }
        Net.away("sponsor parts", new Runnable() {
            @Override public void run() {
                try {
                    String said = Net.text(Badges.SERVER + "/sb?ids=" + aid);
                    if (said == null) return;
                    JSONArray list = new JSONObject(said).optJSONObject("segments") == null ? null
                            : new JSONObject(said).getJSONObject("segments").optJSONArray(aid);
                    keep(aid, parse(list));
                } catch (Throwable error) {
                    Diary.note("sponsor parts: " + error);
                } finally {
                    synchronized (SponsorBlock.class) {
                        asking.remove(aid);
                    }
                }
            }
        });
    }

    private static long[][] parse(JSONArray list) throws Exception {
        if (list == null) return new long[0][];
        long[][] out = new long[list.length()][];
        for (int i = 0; i < list.length(); i++) {
            JSONArray one = list.getJSONArray(i);
            out[i] = new long[] {one.getLong(0), one.getLong(1), one.optLong(2)};
        }
        return out;
    }

    // ------------------------------------------------------------ skipping

    private static long[] lastSkipped;

    private static final Runnable tick = new Runnable() {
        @Override public void run() {
            long next = 1000L;
            try {
                next = look();
            } catch (Throwable ignored) {
            }
            main.postDelayed(this, next);
        }
    };

    /** Looks where the video is; returns how soon to look again. */
    private static long look() {
        if (!skipOn() || Watch.here() == null) return 1000L;
        Object manager = PlayerTools.manager();
        String aid = PlayerTools.aid();
        long[][] parts = partsOf(aid);
        if (manager == null || parts == null || parts.length == 0) return 600L;
        long position = PlayerTools.position(manager), length = PlayerTools.duration(manager);
        if (position < 0 || length <= 0) return 300L;
        for (long[] part : parts) {
            if (allowed.contains(part[2])) continue;
            if (position >= part[0] && position < part[1] - 400) {
                long to = Math.min(length - 1, part[1]);
                PlayerTools.seekTo(manager, to, length);
                lastSkipped = part;
                undo(aid, part);
                return 600L;
            }
        }
        return 150L;
    }

    /** "Реклама пропущена · Вернуть", for a few seconds over the video. */
    private static void undo(final String aid, final long[] part) {
        Activity activity = Watch.here();
        if (activity == null) return;
        final ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View old = decor.findViewWithTag(TAG_UNDO);
        if (old != null) decor.removeView(old);
        final TextView pill = new TextView(activity);
        pill.setTag(TAG_UNDO);
        float unit = activity.getResources().getDisplayMetrics().density;
        pill.setText(Local.of("Реклама пропущена") + "  ·  " + Local.of("Вернуть"));
        pill.setTextColor(0xFFFFFFFF);
        pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        pill.setPadding(Math.round(18 * unit), Math.round(10 * unit), Math.round(18 * unit), Math.round(10 * unit));
        GradientDrawable ground = new GradientDrawable();
        ground.setColor(0xE6202020);
        ground.setCornerRadius(24 * unit);
        pill.setBackground(ground);
        Fonts.apply(pill);
        pill.setOnClickListener(v -> {
            allowed.add(part[2]);
            Object manager = PlayerTools.manager();
            if (manager != null && aid.equals(PlayerTools.aid())) {
                PlayerTools.seekTo(manager, part[0], PlayerTools.duration(manager));
            }
            decor.removeView(pill);
        });
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        place.bottomMargin = Math.round(150 * unit);
        decor.addView(pill, place);
        pill.setAlpha(0f);
        pill.animate().alpha(1f).setDuration(180).start();
        pill.postDelayed(() -> pill.animate().alpha(0f).setDuration(240).withEndAction(() -> decor.removeView(pill)).start(), 4000);
    }

    private static final String TAG_UNDO = "margyt-sponsor-undo";

    // ------------------------------------------------------------ red on the bar

    private static final int TAG_MARKED = 0x7e0c0450;
    private static long lookedForBars;

    static void attach(Activity activity) {
        if (Version.LITE || activity == null || !activity.getClass().getName().equals("com.ss.android.ugc.aweme.main.MainActivity")) return;
        final View root = activity.getWindow().getDecorView();
        if (root.getTag(TAG_MARKED) != null) return;
        root.setTag(TAG_MARKED, Boolean.TRUE);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lookedForBars < 400L) return;
            lookedForBars = now;
            try {
                bars(root, 0);
            } catch (Throwable ignored) {
            }
        });
    }

    private static void bars(View view, int depth) {
        if (view == null || depth > 40) return;
        if (view instanceof SeekBar) {
            if (view.getTag(TAG_MARKED) == null) {
                view.setTag(TAG_MARKED, Boolean.TRUE);
                view.getOverlay().add(new Red((SeekBar) view));
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) bars(group.getChildAt(i), depth + 1);
        }
    }

    /** The parts of the video playing, red on its bar. */
    private static final class Red extends Drawable {
        private final WeakReference<SeekBar> bar;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Red(SeekBar bar) {
            this.bar = new WeakReference<SeekBar>(bar);
            setBounds(0, 0, 20000, 20000);
            paint.setColor(0xFFFF3B3B);
        }

        @Override public void draw(Canvas canvas) {
            SeekBar seek = bar.get();
            if (seek == null || !marksOn() || !seek.isShown()) return;
            Object manager = PlayerTools.manager();
            long[][] parts = partsOf(PlayerTools.aid());
            if (manager == null || parts == null || parts.length == 0) return;
            long length = PlayerTools.duration(manager);
            if (length <= 0) return;
            float left = seek.getPaddingLeft(), right = seek.getWidth() - seek.getPaddingRight();
            float middle = seek.getHeight() / 2f, half = seek.getResources().getDisplayMetrics().density * 1.6f;
            for (long[] part : parts) {
                float a = left + (right - left) * Math.min(1f, part[0] / (float) length);
                float b = left + (right - left) * Math.min(1f, part[1] / (float) length);
                box.set(a, middle - half, Math.max(a + half, b), middle + half);
                canvas.drawRoundRect(box, half, half, paint);
            }
        }

        @Override public void setAlpha(int alpha) {}

        @Override public void setColorFilter(ColorFilter filter) {}

        @Override public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ------------------------------------------------------------ marking

    /** From the video's menu: mark where the advert is, or say a red part is no advert. */
    static void mark(final Context context, Object post) {
        final String aid = NativeRead.aid(post);
        final Object manager = PlayerTools.manager();
        if (aid.length() == 0 || manager == null || !aid.equals(PlayerTools.aid())) {
            Screen.say(Local.of("Включи это видео и попробуй ещё раз"));
            return;
        }
        final String uid = Account.id();
        final String token = Mine.token();
        if (uid == null || token.length() == 0) {
            Panel.with(context, Skin.remembered(context), Local.of("Нужен подтверждённый аккаунт"))
                    .text(Local.of("Отмечать рекламу могут только подтверждённые аккаунты. Подтвердить можно в настройках мода, в разделе «Значки»."))
                    .primary(Local.of("Понятно"), null).show();
            return;
        }
        final long length = PlayerTools.duration(manager);
        long now = PlayerTools.position(manager);
        if (length <= 0) {
            Screen.say(Text.PLAYER_NOT_READY);
            return;
        }
        final Range range = new Range(context, length, Math.max(0, now - 1000), Math.min(length, now + 9000), manager);
        Panel panel = Panel.with(context, Skin.remembered(context), Local.of("Где реклама"));
        panel.text(Local.of("Двигай ползунки к началу и концу рекламы. Видео перематывается за ними."));
        panel.view(range);
        long[][] parts = partsOf(aid);
        if (parts != null) {
            for (final long[] part : parts) {
                panel.item("block", Local.of("Это не реклама"), Local.of("с") + " " + time(part[0]) + " " + Local.of("до") + " " + time(part[1]),
                        () -> send(context, aid, part[0], part[1], length, true));
            }
        }
        panel.primary(Local.of("Отправить"), () -> send(context, aid, range.start, range.stop, length, false));
        panel.quiet(Text.CLOSE, null);
        panel.show();
    }

    static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static void send(final Context context, final String aid, final long start, final long stop, final long length, final boolean wrong) {
        final String uid = Account.id();
        final String token = Mine.token();
        Net.away("sponsor mark", new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject().put("uid", uid).put("token", token).put("aid", aid)
                            .put("start", start).put("stop", stop).put("length", length).put("wrong", wrong);
                    Net.Said said = Net.talk(Badges.SERVER + "/sb/report", body.toString());
                    if (said.ok()) {
                        JSONObject answer = new JSONObject(said.body);
                        keep(aid, parse(answer.optJSONArray("segments")));
                        Screen.say(wrong ? Local.of("Спасибо, учтём")
                                : answer.optBoolean("taken") ? Local.of("Готово, реклама будет пропускаться")
                                : Local.of("Спасибо! Реклама начнёт пропускаться, когда её отметят ещё несколько человек"));
                    } else if (Mine.asksForProof(said)) {
                        Screen.say(Local.of("Сначала подтверди аккаунт в разделе «Значки»"));
                    } else if (said.code == 429) {
                        Screen.say(Local.of("Слишком много отметок, попробуй позже"));
                    } else {
                        Screen.say(Local.of("Не получилось отправить, попробуй позже"));
                    }
                } catch (Throwable error) {
                    Diary.note("sponsor mark: " + error);
                    Screen.say(Local.of("Не получилось отправить, попробуй позже"));
                }
            }
        });
    }

    /** A bar of the whole video with two handles; moving one takes the video there. */
    static final class Range extends View {
        final long length;
        long start, stop;
        private final Object manager;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float d;
        private int held = -1;
        private long lastSeek;

        Range(Context context, long length, long start, long stop, Object manager) {
            super(context);
            this.length = length;
            this.start = start;
            this.stop = Math.max(start + 1000, stop);
            this.manager = manager;
            d = context.getResources().getDisplayMetrics().density;
            words.setTextSize(13 * d);
            words.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), Math.round(76 * d));
        }

        private float left() { return 22 * d; }

        private float right() { return getWidth() - 22 * d; }

        private float x(long ms) { return left() + (right() - left()) * ms / (float) length; }

        private long ms(float x) { return Math.max(0, Math.min(length, Math.round((x - left()) / (right() - left()) * length))); }

        @Override protected void onDraw(Canvas canvas) {
            Skin skin = Skin.remembered(getContext());
            float y = 30 * d;
            paint.setColor((skin.text & 0xFFFFFF) | 0x33000000);
            box.set(left(), y - 3 * d, right(), y + 3 * d);
            canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
            paint.setColor(0xFFFF3B3B);
            box.set(x(start), y - 3 * d, x(stop), y + 3 * d);
            canvas.drawRect(box, paint);
            for (long at : new long[] {start, stop}) {
                paint.setColor(0xFFFFFFFF);
                canvas.drawCircle(x(at), y, 11 * d, paint);
                paint.setColor(0xFFFF3B3B);
                canvas.drawCircle(x(at), y, 7 * d, paint);
            }
            words.setColor(skin.text);
            canvas.drawText(Local.of("с") + " " + time(start) + " " + Local.of("до") + " " + time(stop), getWidth() / 2f, 66 * d, words);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    held = Math.abs(x - x(start)) <= Math.abs(x - x(stop)) ? 0 : 1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    // falls through
                case MotionEvent.ACTION_MOVE:
                    long at = ms(x);
                    if (held == 0) start = Math.min(at, stop - 1000);
                    else if (held == 1) stop = Math.max(at, start + 1000);
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now - lastSeek > 120) {
                        lastSeek = now;
                        PlayerTools.seekTo(manager, held == 0 ? start : stop, length);
                    }
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (held >= 0) PlayerTools.seekTo(manager, held == 0 ? start : stop, length);
                    held = -1;
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }
}

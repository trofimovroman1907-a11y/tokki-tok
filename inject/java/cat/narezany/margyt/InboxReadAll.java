package cat.narezany.margyt;
// Из ttcuz (автор tremorttcuz), перенесено в MargyT с разрешения автора.

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adds the bulk read action to TikTok's inbox menu. */
public final class InboxReadAll {
    private static final String KEY = "inbox_read_all";
    private static final int MENU_TAG = 0x54545441;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    private InboxReadAll() {}

    static boolean enabled() {
        Context context = Margy.context();
        return context != null && context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    static void set(boolean on) {
        Context context = Margy.context();
        if (context != null) context.getSharedPreferences(Margy.PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    public static void addMenu(ViewGroup menu) {
        if (!enabled() || menu == null || Boolean.TRUE.equals(menu.getTag(MENU_TAG))) return;
        try {
            Class<?> rowType = Class.forName("X.0lnT");
            View row = (View) rowType.getConstructor(Context.class, AttributeSet.class, int.class)
                    .newInstance(menu.getContext(), null, 6);
            rowType.getMethod("setTitle", CharSequence.class).invoke(row, Local.of("Прочитать всё"));
            row.setContentDescription(Local.of("Прочитать все сообщения и активность"));

            Class<?> listenerType = Class.forName("kotlin.jvm.functions.Function1");
            Object unit = Class.forName("kotlin.Unit").getField("LIZ").get(null);
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "invoke": start(); return unit;
                            case "hashCode": return System.identityHashCode(proxy);
                            case "equals": return proxy == args[0];
                            case "toString": return "MargyT inbox read all";
                            default: return null;
                        }
                    });
            rowType.getMethod("setOnClickListener", listenerType).invoke(row, listener);
            menu.addView(row, new ViewGroup.LayoutParams(-1, -2));
            menu.setTag(MENU_TAG, Boolean.TRUE);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Diary.note("inbox read-all menu: " + error);
        }
    }

    static void start() {
        String account = Account.liveId();
        if (account == null) {
            Screen.say(Local.of("Сначала войдите в аккаунт TikTok"));
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;
        Screen.say(Local.of("Отмечаю сообщения и активность прочитанными…"));
        Net.away("inbox read all", () -> readAll(account));
    }

    private static void readAll(String account) {
        try {
            boolean messages = false, notices = false;
            try {
                messages = readMessages(account);
            } catch (Exception error) {
                Diary.note("inbox read-all messages: " + error);
            }
            try {
                readNotices(account);
                notices = true;
            } catch (Exception error) {
                Diary.note("inbox read-all activity: " + error);
            }
            String result = resultMessage(messages, notices);
            MAIN.post(() -> {
                if (account.equals(Account.liveId())) Screen.say(result);
            });
        } finally {
            BUSY.set(false);
        }
    }

    /** The social IM service used by TikTok's inbox, with its default conversation filter. */
    private static boolean readMessages(String account) throws Exception {
        checkAccount(account);
        Class<?> sceneType = Class.forName("X.0Rub");
        Object scene = sceneType.getField("TIKTOK_SOCIAL_IM").get(null);
        Object im = Class.forName("X.06WS").getMethod("LIZ", sceneType).invoke(null, scene);
        if (im == null) throw new IllegalStateException("IM SDK unavailable");

        Class<?> queryType = Class.forName("X.06kl");
        Class<?> sortType = Class.forName("X.0RxE");
        Object filter = Class.forName("X.0Rr5").getConstructor().newInstance();
        Object query = queryType.getConstructor(List.class, sortType, int.class, Class.forName("X.06Yr"),
                boolean.class, String.class).newInstance(Collections.singletonList(filter),
                        sortType.getField("SORT_ORDER_DESC").get(null), -1, null, true, "margyt_read_all");
        Object result = im.getClass().getMethod("LJIIJ", queryType).invoke(im, query);
        Object page = NativeRead.field(result, "LIZ");
        Object conversations = NativeRead.field(page, "LIZIZ");
        if (NativeRead.field(result, "LIZIZ") != null || !(conversations instanceof List)) {
            throw new IllegalStateException("Conversation query failed");
        }

        List<Object> unread = new ArrayList<>();
        for (Object chat : new ArrayList<>((List<?>) conversations)) {
            if (chat != null && unreadCount(chat) > 0) unread.add(chat);
        }
        if (unread.isEmpty()) return true;

        FutureTask<Void> mark = new FutureTask<>(() -> {
            checkAccount(account);
            im.getClass().getMethod("LJIJI", List.class, Class.forName("X.0Tf0")).invoke(im, unread, null);
            return null;
        });
        MAIN.post(mark);
        try {
            mark.get(20, TimeUnit.SECONDS);
        } finally {
            mark.cancel(false);
        }

        long deadline = SystemClock.uptimeMillis() + 15_000;
        do {
            checkAccount(account);
            if (!stillUnread(im, unread)) return true;
            Thread.sleep(150);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private static boolean stillUnread(Object im, List<Object> unread) throws Exception {
        for (Object chat : unread) {
            String id = (String) chat.getClass().getMethod("getConversationId").invoke(chat);
            Object current = im.getClass().getMethod("LIZ", String.class).invoke(im, id);
            if (unreadCount(current != null ? current : chat) > 0) return true;
        }
        return false;
    }

    private static long unreadCount(Object chat) throws Exception {
        return ((Number) chat.getClass().getMethod("getUnreadCount").invoke(chat)).longValue();
    }

    /** TikTok's inbox entrance request with its own mark-read option. */
    private static void readNotices(String account) throws Exception {
        checkAccount(account);
        Class<?> experiments = Class.forName("X.0mFh");
        boolean full = Boolean.TRUE.equals(Class.forName("X.09BV").getMethod("LIZ").invoke(null));
        String parameters = (String) (full ? experiments.getMethod("LIZLLL").invoke(null)
                : experiments.getMethod("LIZJ").invoke(experiments.getField("LL").get(null)));
        String transformed = (String) Class.forName("X.0mG6").getMethod("LIZ", String.class).invoke(null, parameters);
        Object groups = Class.forName("com.ss.android.ugc.aweme.inbox.widget.multi.MultiViewModel")
                .getField("LLLLLLZZ").get(null);
        String additionalGroups = (String) Class.forName("X.01SE").getMethod("getValue").invoke(groups);
        String business = (String) Class.forName("X.05C0").getMethod("LIZ").invoke(null);
        Class<?> apiType = Class.forName("com.ss.android.ugc.aweme.inbox.api.MultiApiManager$API");
        Object api = Class.forName("com.ss.android.ugc.aweme.inbox.api.MultiApiManager").getMethod("LIZ").invoke(null);
        Object request = apiType.getMethod("LIZIZ", String.class, String.class, int.class, Boolean.class,
                Boolean.class, Boolean.class, String.class).invoke(api, transformed, additionalGroups,
                        1, Boolean.TRUE, Boolean.FALSE, Boolean.FALSE, business);
        Class<?> stream = Class.forName("X.19ZK");
        Object timed = stream.getMethod("LJLJJI", long.class, TimeUnit.class).invoke(request, 25L, TimeUnit.SECONDS);
        checkAccount(account);
        Object response = stream.getMethod("LIZLLL").invoke(timed);
        Object status = NativeRead.field(response, "status_code");
        if (!(status instanceof Number) || ((Number) status).intValue() != 0) {
            throw new IllegalStateException("Notice mark-read rejected: " + status);
        }
    }

    private static void checkAccount(String account) {
        if (!account.equals(Account.liveId())) throw new IllegalStateException("Account changed");
    }

    private static String resultMessage(boolean messages, boolean notices) {
        if (messages && notices) return Local.of("Сообщения и активность отмечены прочитанными");
        if (messages) return Local.of("Сообщения прочитаны. Активность прочитать не удалось — повторите.");
        if (notices) return Local.of("Активность прочитана. Часть сообщений пока остаётся непрочитанной.");
        return Local.of("Не удалось прочитать сообщения и активность. Повторите; подробности в журнале мода.");
    }
}

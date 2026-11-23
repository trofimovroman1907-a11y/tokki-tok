package cat.narezany.margyt;

import android.content.Context;
import android.os.Build;
import android.os.storage.StorageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Keeps TikTok's cache below the phone's cache quota.
 *
 * Some firmware gives an app a small cache quota (HONOR's MagicOS: 64 MiB, which the system settings show as
 * 67,11 MB) and, once the app is over it, deletes its cache files while the app is using them. TikTok's picture and
 * video caches then lose files under its feet, and it stalls and freezes until the cache is cleared by hand. People
 * on such phones said: "at 67,11 MB it starts to hang; clear the cache and it is fine again".
 *
 * So the mod does the clearing itself, a little at a time and before the system has to: every minute while TikTok
 * is open it looks at how big the cache is, and over 80% of a small quota it deletes the oldest files until it is
 * under 55%. Phones with a big quota (most) are left alone.
 */
final class CacheGuard {
    private CacheGuard() {}

    /** A quota under this is a small one, worth minding. */
    private static final long SMALL = 256L * 1024 * 1024;
    private static final long EVERY = 60_000L;
    private static final long HONOR = 64L * 1024 * 1024;
    private static volatile boolean started;

    static void start(final Context context) {
        if (started || Build.VERSION.SDK_INT < 26) return;
        started = true;
        final Context app = context.getApplicationContext();
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // a TikTok put away keeps running for hours: nothing to mind for a screen nobody sees
                if (Watch.here() != null) Net.away("cache guard", () -> look(app));
                handler.postDelayed(this, EVERY);
            }
        }, 20_000L);
    }

    private static boolean said;

    private static void look(Context app) {
        try {
            StorageManager storage = (StorageManager) app.getSystemService(Context.STORAGE_SERVICE);
            if (storage == null) return;
            long quota = storage.getCacheQuotaBytes(storage.getUuidForPath(app.getCacheDir()));
            // MagicOS and EMUI may cap the cache on their own without saying so through the quota: the freeze comes
            // at exactly 64 MiB there, so those phones are held under it whatever the system reports
            String maker = String.valueOf(Build.MANUFACTURER).toLowerCase(java.util.Locale.ROOT);
            if ((maker.contains("honor") || maker.contains("huawei")) && (quota <= 0 || quota > HONOR)) quota = HONOR;
            if (!said) {
                said = true;
                Diary.note("cache guard: quota " + (quota >> 20) + " MiB" + (quota > 0 && quota < SMALL ? ", minding it" : ", big enough"));
            }
            if (quota <= 0 || quota >= SMALL) return;
            List<File> files = new ArrayList<>();
            long total = collect(app.getCacheDir(), files, 0) + collect(app.getExternalCacheDir(), files, 0);
            if (total < quota * 0.8) return;
            // the oldest first: what was looked at longest ago is the least missed
            Collections.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            long goal = (long) (quota * 0.55), freed = 0;
            int gone = 0;
            long now = System.currentTimeMillis();
            for (File f : files) {
                if (total - freed <= goal) break;
                // a file written just now is likely still open
                if (now - f.lastModified() < 30_000L) continue;
                long size = f.length();
                if (f.delete()) {
                    freed += size;
                    gone++;
                }
            }
            Diary.note("cache guard: " + (total >> 20) + " of " + (quota >> 20) + " MiB, " + gone + " old files out, " + (freed >> 20) + " MiB freed");
        } catch (Throwable error) {
            Diary.note("cache guard: " + error);
        }
    }

    /** Every file under a folder, with the bytes they take; not into the mod's own working files. */
    private static long collect(File dir, List<File> out, int depth) {
        if (dir == null || depth > 8) return 0;
        File[] list = dir.listFiles();
        if (list == null) return 0;
        long total = 0;
        for (File f : list) {
            if (f.getName().startsWith("margyt") || f.getName().startsWith("mtp-")) continue;
            if (f.isDirectory()) {
                total += collect(f, out, depth + 1);
            } else {
                total += f.length();
                out.add(f);
            }
        }
        return total;
    }
}

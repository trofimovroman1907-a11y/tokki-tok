package cat.narezany.margyt;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.Window;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The touches of a TikTok screen, seen on their way in and never taken (2.4).
 *
 * The window's callback is wrapped once per window: every touch is shown to the parts of the mod that watch gestures
 * (the profile video opening from its card, VideoOpenTransition) and then goes to TikTok exactly as it came.
 */
final class TouchHook {
    private TouchHook() {}

    private static final Map<Window, Boolean> hooked = new WeakHashMap<>();

    static void hook(final Activity activity) {
        if (activity == null) return;
        final Window window = activity.getWindow();
        if (window == null) return;
        final Window.Callback base = window.getCallback();
        if (base == null) return;
        synchronized (hooked) {
            if (hooked.containsKey(window)) return;
            hooked.put(window, Boolean.TRUE);
        }
        try {
            window.setCallback((Window.Callback) Proxy.newProxyInstance(base.getClass().getClassLoader(),
                    new Class<?>[]{Window.Callback.class}, (proxy, method, args) -> {
                        if (args != null && args.length == 1 && args[0] instanceof MotionEvent
                                && method.getName().equals("dispatchTouchEvent")) {
                            try {
                                UiMotion.touch(activity, (MotionEvent) args[0]);
                            } catch (Throwable error) {
                                Diary.note("ui motion: " + error);
                            }
                            try {
                                VideoOpenTransition.touch(activity, (MotionEvent) args[0]);
                            } catch (Throwable error) {
                                VideoOpenTransition.clear(activity);
                            }
                        }
                        try {
                            return method.invoke(base, args);
                        } catch (InvocationTargetException thrown) {
                            throw thrown.getCause();
                        }
                    }));
        } catch (Throwable error) {
            Diary.note("touch hook: " + error);
        }
    }
}

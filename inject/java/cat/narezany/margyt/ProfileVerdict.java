package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The plate on a profile that says what the administration of MargyT thinks of
 * the account: recommended, or not, and why.
 *
 * It goes at the bottom of the block with the name and the counts, as one more
 * row of it, so whatever comes below moves down to make room instead of being
 * covered. Whose profile it is comes from the profile's own view model, asked
 * by the types it answers with rather than by its scrambled names; the user is
 * often not loaded yet when the block is made, so it is asked again each time
 * the block is laid out.
 */
final class ProfileVerdict {

    private ProfileVerdict() {}

    private static final int PLATE = 0x7e0c0337;
    private static final Map<View, WeakReference<Object>> owners = new WeakHashMap<>();
    private static final Map<Class<?>, Method[]> roads = new java.util.HashMap<>();

    static void bind(Object component, View view) {
        // The rows hang in the container's own column, which is not always the view the
        // component was handed: that one can be a frame round it. The column is found by
        // its type on the container class, then by looking inside the view.
        View block = column(component, view);
        android.util.Log.e("MargyTVerdict", "bind " + component.getClass().getSimpleName() + " view=" + view.getClass().getName()
                + " column=" + (block == null ? "none" : block.getClass().getName()) + " uid=" + uid(component));
        if (block == null) return;
        synchronized (owners) { owners.put(block, new WeakReference<>(component)); }
        if (block != view && !Boolean.TRUE.equals(block.getTag(WATCHED)))
            { block.setTag(WATCHED, Boolean.TRUE); block.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> apply(v)); }
        apply(block);
    }

    private static final int WATCHED = 0x7e0c0339, LOGGED = 0x7e0c033b;

    private static boolean vertical(View view) {
        return view instanceof LinearLayout && ((LinearLayout) view).getOrientation() == LinearLayout.VERTICAL;
    }

    private static View column(Object component, View view) {
        // TikTok 47.2.41: the header's own column, holding the name and counts, the bio,
        // the chips and the highlights. The container's fields hold the whole page instead.
        View header = named(view, "t7l", 0);
        if (vertical(header)) return header;
        for (Class<?> step = component.getClass(); step != null && step != Object.class; step = step.getSuperclass()) {
            if (!step.getName().endsWith("HeaderBaseContainerComponent")) continue;
            for (java.lang.reflect.Field field : step.getDeclaredFields()) {
                if (field.getType() != ViewGroup.class) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(component);
                    if (vertical((View) value)) return (View) value;
                } catch (Throwable ignored) {}
            }
        }
        if (vertical(view)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (vertical(group.getChildAt(i))) return group.getChildAt(i);
        }
        return null;
    }

    /** Whether a view is the plate, so nothing else rearranges it. */
    static boolean plate(View view) { return view != null && view.getTag(PLATE) != null; }

    static void apply(View view) {
        try {
            WeakReference<Object> held;
            synchronized (owners) { held = owners.get(view); }
            Object component = held == null ? null : held.get();
            if (component == null) return;
            LinearLayout block = (LinearLayout) view;
            String uid = uid(component);
            Badges.Verdict verdict = Badges.verdict(uid);
            View plate = find(block);
            if (verdict == null) {
                if (plate != null) block.removeView(plate);
                return;
            }
            String key = (verdict.good ? "1" : "0") + verdict.reason;
            if (plate != null && !key.equals(plate.getTag(PLATE))) { block.removeView(plate); plate = null; }
            if (plate == null) {
                plate = make(block.getContext(), verdict);
                plate.setTag(PLATE, key);
                LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                int side = dp(block.getContext(), 16);
                where.setMargins(side, dp(block.getContext(), 10), side, dp(block.getContext(), 4));
                // posted: the block is in the middle of being laid out when it is asked
                final View made = plate;
                // right under the name and the counts, above the bio
                block.post(() -> { if (made.getParent() == null) block.addView(made, Math.min(1, block.getChildCount()), where); });
                return;
            }
            // Where it stands is left alone once it is in: moving it back to the end on every
            // layout fought TikTok, which keeps its rows in an order of its own, and the two
            // took turns forever.
            if (!Boolean.TRUE.equals(plate.getTag(LOGGED))) {
                plate.setTag(LOGGED, Boolean.TRUE);
                final View shown = plate;
                plate.postDelayed(() -> {
                    int[] at = new int[2]; shown.getLocationOnScreen(at);
                    StringBuilder chain = new StringBuilder();
                    for (View step = block; step != null; step = step.getParent() instanceof View ? (View) step.getParent() : null) {
                        chain.append(step.getClass().getSimpleName()).append(step.getVisibility() == View.VISIBLE ? "" : "(hidden)")
                                .append(' ').append(step.getHeight()).append(" > ");
                        if (chain.length() > 600) break;
                    }
                    android.util.Log.e("MargyTVerdict", "plate at " + at[0] + "," + at[1] + " " + shown.getWidth() + "x" + shown.getHeight()
                            + " index " + block.indexOfChild(shown) + "/" + block.getChildCount() + " shown=" + shown.isShown() + " chain " + chain);
                }, 1500);
            }
        } catch (Throwable error) {
            Diary.note("profile verdict: " + error);
        }
    }

    private static View named(View view, String name, int depth) {
        if (view == null || depth > 12) return null;
        if (name.equals(Names.of(view))) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = named(group.getChildAt(i), name, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View find(ViewGroup block) {
        for (int i = 0; i < block.getChildCount(); i++) if (plate(block.getChildAt(i))) return block.getChildAt(i);
        return null;
    }

    /** The account the profile shows: component → its view model → the user on it → the user's id. */
    private static String uid(Object component) {
        Method[] road;
        synchronized (roads) { road = roads.get(component.getClass()); }
        try {
            if (road == null) {
                Method model = returning(component.getClass(), "com.ss.android.ugc.profile.platform.base.viewmodel.ProfilePlatformViewModel");
                Method user = model == null ? null : returning(model.getReturnType(), "com.ss.android.ugc.aweme.profile.model.User");
                road = new Method[]{model, user};
                synchronized (roads) { roads.put(component.getClass(), road); }
            }
            if (road[0] == null || road[1] == null) return null;
            Object vm = road[0].invoke(component);
            Object user = vm == null ? null : road[1].invoke(vm);
            Object uid = user == null ? null : user.getClass().getMethod("getUid").invoke(user);
            return uid instanceof String && ((String) uid).length() > 0 ? (String) uid : null;
        } catch (Throwable error) {
            return null;
        }
    }

    private static Method returning(Class<?> type, String name) {
        for (Class<?> step = type; step != null && step != Object.class; step = step.getSuperclass()) {
            for (Method method : step.getDeclaredMethods()) {
                if (method.getParameterTypes().length != 0 || !method.getReturnType().getName().equals(name)) continue;
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static View make(Context c, Badges.Verdict verdict) {
        Skin skin = Skin.remembered(c);
        int tone = verdict.good ? 0xFF2EAD5B : 0xFFE5484D;
        LinearLayout plate = new LinearLayout(c);
        plate.setOrientation(LinearLayout.VERTICAL);
        plate.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 11));
        GradientDrawable back = new GradientDrawable();
        back.setCornerRadius(dp(c, 16));
        back.setColor((tone & 0xFFFFFF) | 0x24000000);
        back.setStroke(Math.max(1, dp(c, 1)), (tone & 0xFFFFFF) | 0x66000000);
        plate.setBackground(back);

        TextView title = new TextView(c);
        title.setText(verdict.good ? Text.VERDICT_GOOD : Text.VERDICT_BAD);
        title.setTextColor(skin.text);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        plate.addView(title);

        TextView body = new TextView(c);
        String reason = verdict.reason == null ? "" : verdict.reason.trim();
        body.setText(Text.verdictWhy(verdict.good, reason));
        body.setTextColor(skin.text);
        body.setAlpha(0.8f);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        body.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams under = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        under.topMargin = dp(c, 3);
        plate.addView(body, under);
        plate.setContentDescription(title.getText() + ". " + body.getText());
        Fonts.applyTree(plate);
        return plate;
    }

    private static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }
}

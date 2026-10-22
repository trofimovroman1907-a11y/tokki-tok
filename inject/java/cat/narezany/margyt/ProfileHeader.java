package cat.narezany.margyt;

import com.ss.android.ugc.aweme.profile.model.User;
import com.ss.android.ugc.profile.platform.base.data.ProfileComponents;
import com.ss.android.ugc.profile.platform.base.data.ProfileUser;

import java.util.ArrayList;
import java.util.List;

/**
 * The new profile header, whatever the server sent.
 *
 * TikTok's server builds the profile page as a tree of parts, and puts an
 * account's header together one way or the other by the group the account is
 * in: the new header has the avatar beside the name, the old has it on its
 * own above. The flag on the phone does not change that, so an account the
 * server moved back to the old group stayed old with the switch on.
 *
 * The two differ in one move, as TikTok's own templates for them show:
 *
 *     old:  header [ header_avatar, info [ user_info, user_relation_info ], cta, bio ]
 *     new:  header [ info [ user_basic_info [ header_avatar, user_info ], user_relation_info ], bio, cta ]
 *
 * Every page passes through one of TikTok's methods on its way to the screen,
 * the one that pours the account's details into the tree; the build sends
 * those calls here, the tree is moved into the new shape first, and TikTok's
 * method does the rest. Everything else the server put in the page -- the
 * banner, links, whatever is new -- stays where it was.
 */
public final class ProfileHeader {
    private ProfileHeader() {}

    private static volatile java.lang.reflect.Method original;

    /**
     * Remembered once the server has been seen sending this phone's account the
     * old header: the account is in the old group, and the parts of the new
     * profile that go by their own switches (the avatar and the banner above
     * it) are switched on for it too (Flags).
     */
    static final String OLD_GROUP = "profile_old_group";

    static boolean oldGroup() {
        try {
            return Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).getBoolean(OLD_GROUP, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In place of TikTok's X.0sm9.LJIILIIL(user, page, mine, then). */
    public static ProfileUser LJIILIIL(User user, ProfileUser page, boolean mine, kotlin.jvm.functions.Function1 then) {
        // Nothing is moved for now: a header rearranged without the rest of what
        // the new group has came out as neither (two avatars, the old layout
        // round it). With the developer switch on, the page the server sent and
        // TikTok's answers for the new profile's parts are written to logcat, to
        // build the real thing from an account that has it.
        try {
            if (page != null && DevMode.isOn()) record(page, mine);
        } catch (Throwable error) {
            Diary.note("profile header: " + error);
        }
        try {
            java.lang.reflect.Method method = original;
            if (method == null) {
                method = Class.forName("X.0sm9", true, ProfileHeader.class.getClassLoader()).getDeclaredMethod("LJIILIIL",
                        User.class, ProfileUser.class, boolean.class, kotlin.jvm.functions.Function1.class);
                method.setAccessible(true);
                original = method;
            }
            return (ProfileUser) method.invoke(null, user, page, mine, then);
        } catch (java.lang.reflect.InvocationTargetException thrown) {
            Throwable cause = thrown.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException missing) {
            Diary.note("profile header: TikTok's method is gone, " + missing);
            return page;
        }
    }

    /** The old header in the new shape. Does nothing to a header already new, or one it does not recognise. */
    static boolean rearrange(ProfileUser page) {
        ProfileComponents header = page.headerComponents;
        if (header == null || header.components == null) return false;
        List<ProfileComponents> parts = new ArrayList<ProfileComponents>(header.components);
        ProfileComponents avatar = named(parts, "header_avatar");
        ProfileComponents info = named(parts, "info");
        if (avatar == null || info == null || info.components == null) return false;
        List<ProfileComponents> inside = new ArrayList<ProfileComponents>(info.components);
        if (named(inside, "user_basic_info") != null) return false;
        ProfileComponents names = named(inside, "user_info");
        if (names == null) return false;

        ProfileComponents basic = new ProfileComponents();
        basic.componentId = "user_basic_info";
        basic.componentName = "user_basic_info";
        List<ProfileComponents> pair = new ArrayList<ProfileComponents>(2);
        pair.add(avatar);
        pair.add(names);
        basic.components = pair;
        inside.set(inside.indexOf(names), basic);
        info.components = inside;

        parts.remove(avatar);
        // the new header has the bio before the buttons
        ProfileComponents bio = named(parts, "bio"), cta = named(parts, "cta");
        if (bio != null && cta != null && parts.indexOf(bio) > parts.indexOf(cta)) {
            parts.remove(bio);
            parts.add(parts.indexOf(cta), bio);
        }
        header.components = parts;
        return true;
    }

    /** The other way: a new header put back in the old shape, as the server sends it to the old group. */
    static boolean unrearrange(ProfileUser page) {
        ProfileComponents header = page.headerComponents;
        if (header == null || header.components == null) return false;
        List<ProfileComponents> parts = new ArrayList<ProfileComponents>(header.components);
        ProfileComponents info = named(parts, "info");
        if (info == null || info.components == null || named(parts, "header_avatar") != null) return false;
        List<ProfileComponents> inside = new ArrayList<ProfileComponents>(info.components);
        ProfileComponents basic = named(inside, "user_basic_info");
        if (basic == null || basic.components == null) return false;
        ProfileComponents avatar = named(basic.components, "header_avatar");
        ProfileComponents names = named(basic.components, "user_info");
        if (avatar == null || names == null) return false;
        inside.set(inside.indexOf(basic), names);
        info.components = inside;
        parts.add(0, avatar);
        ProfileComponents bio = named(parts, "bio"), cta = named(parts, "cta");
        if (bio != null && cta != null && parts.indexOf(cta) > parts.indexOf(bio)) {
            parts.remove(cta);
            parts.add(parts.indexOf(bio), cta);
        }
        header.components = parts;
        return true;
    }

    // ------------------------------------------------------------ recording

    private static final java.util.Set<String> RECORDED = java.util.Collections.synchronizedSet(new java.util.HashSet<String>());
    private static volatile boolean flagsRecorded;

    private static final String[] FLAGS = {
            "profile_left_align", "profile_left_align_ab_group",
            "profile_left_align_avatar_opt", "profile_left_align_avatar_at_right_opt",
            "profile_left_align_bio_style_opt", "profile_left_align_cta_style_opt",
            "profile_left_align_advance_feature_style_opt", "profile_left_align_tab_icon_opt",
            "profile_left_align_playlist_style_opt", "profile_left_align_relation_info_font_size_opt",
            "profile_left_align_navbar_icon_small_screen_opt", "profile_left_align_right_avatar_large_font_opt",
            "profile_left_align_right_avatar_nickname_opt", "profile_left_align_right_avatar_nickname_opt_v2",
            "profile_left_align_right_avatar_nickname_wordbreak_opt", "profile_left_align_avatar_and_info_merge",
            "profile_left_align_pronouns", "profile_left_align_small_avatar", "profile_left_align_recommend_card",
            "enable_profile_left_align_second_line_two_lines_show", "profile_optimize_move_avatar_up",
            "profile_bg_in_allow_list", "profile_bg_enable_consumption_group", "profile_banner_platform"};

    /** The page as the server sent it, part by part, and TikTok's own answers for the new profile's switches. */
    private static void record(ProfileUser page, boolean mine) {
        StringBuilder out = new StringBuilder();
        outline(out, "nav", page.navBarComponents, 0);
        outline(out, "header", page.headerComponents, 0);
        outline(out, "tabs", page.tabsComponents, 0);
        outline(out, "background", page.profileBackground, 0);
        String said = out.toString();
        if (RECORDED.size() < 8 && RECORDED.add((mine ? "mine\n" : "other\n") + said)) {
            for (String line : ((mine ? "page, mine" : "page, someone else's") + "\n" + said).split("\n"))
                android.util.Log.i("MargyTProfile", line);
        }
        if (!flagsRecorded) {
            flagsRecorded = true;
            for (String key : FLAGS) {
                Object theirs = Flags.own(new Flags.Known(key, "INT", 0));
                android.util.Log.i("MargyTProfile", "flag " + key + " = " + theirs);
            }
        }
    }

    private static void outline(StringBuilder out, String where, ProfileComponents part, int depth) {
        if (part == null) {
            if (depth == 0) out.append(where).append(": none\n");
            return;
        }
        if (depth == 0) out.append(where).append(":\n");
        for (int i = 0; i <= depth; i++) out.append("  ");
        String id = part.componentId == null ? "" : part.componentId, name = part.componentName == null ? "" : part.componentName;
        out.append(id.equals(name) ? (name.length() == 0 ? "(empty)" : name) : id + "/" + name).append('\n');
        if (part.components != null) for (ProfileComponents child : part.components) outline(out, where, child, depth + 1);
    }

    private static ProfileComponents named(List<? extends ProfileComponents> parts, String name) {
        for (ProfileComponents part : parts) {
            if (part != null && name.equals(part.componentName)) return part;
        }
        return null;
    }
}

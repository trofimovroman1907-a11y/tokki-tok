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

    /**
     * For a developer whose account is in the new group: the header is taken
     * back to the old group's shape and mended, and the account is treated as
     * in the old group, so what such an account will see can be looked at.
     */
    static final String TEST = "profile_header_mend_test";

    static boolean test() {
        try {
            return Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).getBoolean(TEST, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean oldGroup() {
        try {
            return Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).getBoolean(OLD_GROUP, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** In place of TikTok's X.0sm9.LJIILIIL(user, page, mine, then). */
    public static ProfileUser LJIILIIL(User user, ProfileUser page, boolean mine, kotlin.jvm.functions.Function1 then) {
        // With the developer switch on, the page as the server sent it and
        // TikTok's answers for the new profile's switches go to a file adb can
        // read: that is what the move below was built from.
        try {
            if (page != null && DevMode.isOn()) record(page, mine);
            if (page != null) {
                boolean test = test();
                if (test) unrearrange(page);
                if (Flags.newProfile() && rearrange(page, mine) && !test && !oldGroup()) {
                    Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).edit()
                            .putBoolean(OLD_GROUP, true).apply();
                    Flags.forget();
                    Diary.note("profile header: the server sends the old header; the banner is built by TikTok from the next start");
                }
            }
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

    /**
     * The old header in the new shape, as TikTok's server sends it to an account
     * in the new group (read off one that has it):
     *
     *     header [ info [ user_basic_info [ (a spacer, on someone else's page),
     *                                       user_info [ name, @name, user_relation_info ],
     *                                       header_avatar ] ], ...the rest as it was ]
     *
     * The avatar goes after the name, on the right, and the counts go inside the
     * name's part. Does nothing to a header already new, or one it does not know.
     */
    static boolean rearrange(ProfileUser page, boolean mine) {
        ProfileComponents header = page.headerComponents;
        if (header == null || header.components == null) return false;
        List<ProfileComponents> parts = new ArrayList<ProfileComponents>(header.components);
        ProfileComponents avatar = named(parts, "header_avatar");
        ProfileComponents info = named(parts, "info");
        if (avatar == null || info == null || info.components == null) return false;
        List<ProfileComponents> inside = new ArrayList<ProfileComponents>(info.components);
        if (named(inside, "user_basic_info") != null) return false;

        ProfileComponents names = named(inside, "user_info");
        if (names == null) {
            // the name parts loose in the info: gathered into one, as the new header has them
            List<ProfileComponents> loose = new ArrayList<ProfileComponents>();
            for (ProfileComponents part : inside)
                if (part != null && ("user_account_name_info".equals(part.componentName) || "user_account_base_info".equals(part.componentName))) loose.add(part);
            if (loose.isEmpty()) return false;
            names = part("user_info", loose);
            inside.add(inside.indexOf(loose.get(0)), names);
            inside.removeAll(loose);
        }
        List<ProfileComponents> nameParts = names.components == null
                ? new ArrayList<ProfileComponents>() : new ArrayList<ProfileComponents>(names.components);
        ProfileComponents counts = named(inside, "user_relation_info");
        if (counts != null) {
            inside.remove(counts);
            if (named(nameParts, "user_relation_info") == null) nameParts.add(counts);
        }
        names.components = nameParts;

        List<ProfileComponents> row = new ArrayList<ProfileComponents>(3);
        if (!mine) row.add(part("", null));
        row.add(names);
        row.add(avatar);
        inside.set(inside.indexOf(names), part("user_basic_info", row));
        info.components = inside;
        parts.remove(avatar);
        header.components = parts;
        return true;
    }

    /** The new header taken back to the shape the old group gets: for the test only. */
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
        int at = inside.indexOf(basic);
        inside.set(at, names);
        if (names.components != null) {
            List<ProfileComponents> nameParts = new ArrayList<ProfileComponents>(names.components);
            ProfileComponents counts = named(nameParts, "user_relation_info");
            if (counts != null) {
                nameParts.remove(counts);
                inside.add(at + 1, counts);
            }
            names.components = nameParts;
        }
        info.components = inside;
        parts.add(0, avatar);
        header.components = parts;
        return true;
    }

    private static ProfileComponents part(String name, List<ProfileComponents> inside) {
        ProfileComponents made = new ProfileComponents();
        made.componentId = name;
        made.componentName = name;
        made.components = inside;
        return made;
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
            write((mine ? "== page, mine" : "== page, someone else's") + "\n" + said);
        }
        if (!flagsRecorded) {
            flagsRecorded = true;
            StringBuilder flags = new StringBuilder("== TikTok's own answers\n");
            for (String key : FLAGS) flags.append(key).append(" = ").append(Flags.own(new Flags.Known(key, "INT", 0))).append('\n');
            write(flags.toString());
        }
    }

    /**
     * Into a file in TikTok's own folder on the shared storage, which adb can
     * read: some phones keep apps' lines out of logcat altogether.
     */
    private static synchronized void write(String text) {
        try {
            java.io.File folder = new java.io.File(Margy.context().getExternalFilesDir(null), "margyt");
            if (!folder.isDirectory() && !folder.mkdirs()) return;
            try (java.io.FileWriter out = new java.io.FileWriter(new java.io.File(folder, "profile.txt"), true)) {
                out.write(text);
                out.write('\n');
            }
        } catch (Throwable ignored) {
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

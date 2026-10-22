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
     * For a developer whose own account is in the new group, to see what one in
     * the old group sees: 1 the old header and none of the new profile's
     * parts, as such an account gets them; 2 the same, then mended as the mod
     * mends it. 0 is as it is.
     */
    static final String TEST = "profile_header_test";

    static int test() {
        try {
            return Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).getInt(TEST, 0);
        } catch (Throwable ignored) {
            return 0;
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
        try {
            int test = test();
            if (page != null && test > 0) unrearrange(page);
            if (page != null && (test == 2 || (test == 0 && Flags.newProfile())) && rearrange(page) && test == 0 && !oldGroup()) {
                Margy.context().getSharedPreferences(Margy.PREFS, android.content.Context.MODE_PRIVATE).edit()
                        .putBoolean(OLD_GROUP, true).apply();
                Flags.forget();
                Diary.note("profile header: the server sends the old header, the new profile's own parts are switched on from the next start");
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

    private static ProfileComponents named(List<? extends ProfileComponents> parts, String name) {
        for (ProfileComponents part : parts) {
            if (part != null && name.equals(part.componentName)) return part;
        }
        return null;
    }
}

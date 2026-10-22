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

    /** In place of TikTok's X.0sm9.LJIILIIL(user, page, mine, then). */
    public static ProfileUser LJIILIIL(User user, ProfileUser page, boolean mine, kotlin.jvm.functions.Function1 then) {
        try {
            if (page != null && Flags.newProfile()) rearrange(page);
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

    private static ProfileComponents named(List<? extends ProfileComponents> parts, String name) {
        for (ProfileComponents part : parts) {
            if (part != null && name.equals(part.componentName)) return part;
        }
        return null;
    }
}

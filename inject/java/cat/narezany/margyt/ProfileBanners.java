package cat.narezany.margyt;
// Идея из SkyTok (автор «небесный»), с его разрешения; написано для Android заново.

/**
 * Video backgrounds of profiles play at once (2.4).
 *
 * TikTok keeps a profile's video background as a still unless the server marked it to play, and never plays it on a
 * phone its own benchmark scores low. The build sends both reads of "autoplay" here, where the answer is yes, and
 * stubs the low-phone check (ProfileBackgroundComponent.Rq) to no.
 */
public final class ProfileBanners {
    private ProfileBanners() {}

    /** ProfileBgBizData.getAutoPlay(). */
    public static Boolean getAutoPlay(Object data) {
        return Boolean.TRUE;
    }

    /** ProfileBgVideoInfo.getAutoPlay(). */
    public static boolean videoAutoPlay(Object info) {
        return true;
    }
}

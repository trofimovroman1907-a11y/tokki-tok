package cat.narezany.margyt;
// Из LotusTok (автор pikmis / @saintcursed, GPL-форк MargyT): палитра Material 3 и формы, нужные MD3-чату.

import android.graphics.Outline;
import android.graphics.Path;
import android.view.View;
import android.view.ViewOutlineProvider;

/** The Material 3 roles of a page and the shapes the MD3 chat cuts things to (the part of LotusTok's ProfileM3 it uses). */
final class ProfileM3 {
    private ProfileM3() {}

    static final class Roles {
        int ground, ink, primary, onPrimary, container, high, secondary, onSecondary, variant, outline;
        int tertiaryContainer, error, primaryContainer;
        boolean dark;
    }

    static Roles roles(View view) {
        return rolesFor(Md3.behind(view) | 0xFF000000);
    }

    static Roles rolesFor(int ground) {
        Roles r = new Roles();
        r.ground = ground | 0xFF000000;
        r.ink = Md3.ink(r.ground);
        r.dark = NavBar.bright(r.ground) < 0.5f;
        r.primary = Accent.colour() | 0xFF000000;
        r.onPrimary = NavBar.contrast(0xFFFFFFFF, r.primary) >= NavBar.contrast(0xFF161823, r.primary)
                ? 0xFFFFFFFF : 0xFF161823;
        r.container = NavBar.mix(r.ground, r.ink, r.dark ? 0.055f : 0.045f);
        r.high = NavBar.mix(r.ground, r.ink, r.dark ? 0.10f : 0.07f);
        r.secondary = NavBar.mix(r.ground, r.primary, r.dark ? 0.30f : 0.17f);
        r.primaryContainer = NavBar.mix(r.ground, r.primary, r.dark ? 0.38f : 0.26f);
        r.onSecondary = NavBar.readable(NavBar.mix(r.ink, r.primary, 0.20f), r.secondary, r.ink, 4.5f);
        r.variant = NavBar.readable(NavBar.mix(r.ground, r.ink, r.dark ? 0.72f : 0.74f), r.ground, r.ink, 4.5f);
        r.outline = NavBar.mix(r.ground, r.ink, r.dark ? 0.26f : 0.20f);
        // the tertiary of the accent: its hue turned a fifth of the way round the wheel, as Material 3 derives it
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(r.primary, hsv);
        hsv[0] = (hsv[0] + 60f) % 360f;
        int tertiary = android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
        r.tertiaryContainer = NavBar.mix(r.ground, tertiary, r.dark ? 0.32f : 0.24f);
        r.error = r.dark ? 0xFFF2B8B5 : 0xFFB3261E;
        return r;
    }

    static ViewOutlineProvider polar(final int waves, final double depth, final double turn) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth(), h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                Path path = polarPath(waves, depth, turn, w, h);
                if (android.os.Build.VERSION.SDK_INT >= 30) outline.setPath(path);
                else outline.setOval(0, 0, w, h);
            }
        };
    }

    static Path polarPath(int waves, double depth, double turn, int w, int h) {
        Path path = new Path();
        for (int i = 0; i <= 240; i++) {
            double t = i * 2 * Math.PI / 240;
            double r = 0.5 * (1 + depth * Math.cos(waves * (t + turn))) / (1 + depth);
            float x = (float) (w * (0.5 + r * Math.cos(t))), y = (float) (h * (0.5 + r * Math.sin(t)));
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    static final ViewOutlineProvider AVATAR = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) return;
            android.graphics.Matrix fit = new android.graphics.Matrix();
            fit.setScale(w / 2f, h / 2f);
            fit.postTranslate(w / 2f, h / 2f);
            Path path = new Path();
            AvatarShape.chosenUnit().transform(fit, path);
            if (android.os.Build.VERSION.SDK_INT >= 30) outline.setPath(path);
            else outline.setOval(0, 0, w, h);
        }
    };

    private static final int SHAPE_OF = 0x7e0c033e;

    static void avatarShape(View photo) {
        if (photo.getOutlineProvider() != AVATAR) {
            photo.setOutlineProvider(AVATAR);
            photo.setClipToOutline(true);
        }
        String now = AvatarShape.chosen();
        if (!now.equals(photo.getTag(SHAPE_OF))) {
            photo.setTag(SHAPE_OF, now);
            photo.invalidateOutline();
        }
    }

    static final ViewOutlineProvider SUNNY = polar(8, 0.045, 0);
}

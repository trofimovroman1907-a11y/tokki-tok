package cat.narezany.margyt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The birthday on top of the game, in the game's own pixels: cake, balloons, garlands, party hats, confetti.
 * Sprites are drawn at whole multiples of their size, like everything else in it.
 */
final class PartyDeco {
    private PartyDeco() {}

    static final int[] COLOURS = {
            PartyGfx.rgb(0.95f, 0.38f, 0.49f), PartyGame.MINT, PartyGfx.rgb(0.98f, 0.81f, 0.33f),
            PartyGfx.rgb(0.5f, 0.59f, 0.98f), PartyGfx.rgb(0.73f, 0.47f, 0.96f), PartyGfx.rgb(0.98f, 0.59f, 0.27f)};
    static final int N = COLOURS.length;

    private static final Map<String, Bitmap> SPRITES = new HashMap<>();
    private static final Map<Bitmap, int[]> HEADS = new HashMap<>();

    /** Sprites are read again from the apk when next drawn; the game keeps its own while it runs. */
    static void trimMemory() { synchronized (SPRITES) { SPRITES.clear(); HEADS.clear(); } }

    static Bitmap sprite(String name) {
        synchronized (SPRITES) {
            if (SPRITES.containsKey(name)) return SPRITES.get(name);
        }
        Bitmap b = null;
        try (InputStream in = Margy.context().getAssets().open(PartyGame.ASSETS + name + ".png")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) {
        }
        synchronized (SPRITES) {
            SPRITES.put(name, b);
        }
        return b;
    }

    /**
     * Where a hat goes on a sprite, in the sprite's own pixels: the middle of its top few rows and the row a little
     * below the very top, so the hat sits on the head between the ears rather than floating above them.
     */
    static int[] head(Bitmap b) {
        if (b == null) return new int[]{12, 2};
        synchronized (HEADS) {
            int[] known = HEADS.get(b);
            if (known != null) return known;
        }
        int w = b.getWidth(), h = b.getHeight(), top = -1;
        for (int y = 0; y < h && top < 0; y++) {
            for (int x = 0; x < w; x++) {
                if ((b.getPixel(x, y) >>> 24) > 128) {
                    top = y;
                    break;
                }
            }
        }
        if (top < 0) top = 0;
        // the head's width a few rows down, where the ears have joined it
        int row = Math.min(h - 1, top + 5), lo = w, hi = -1;
        for (int x = 0; x < w; x++) {
            if ((b.getPixel(x, row) >>> 24) > 128) {
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
        }
        int[] out = {hi >= lo ? (lo + hi + 1) / 2 : w / 2, top + 4};
        synchronized (HEADS) {
            HEADS.put(b, out);
        }
        return out;
    }

    /** A party hat on a sprite drawn at (x, y) with whole-pixel scale s. */
    static void hatOn(PartyGfx g, Bitmap who, float x, float y, int s, int colour) {
        int[] at = head(who);
        hat(g, x + at[0] * s, y + at[1] * s, s, colour);
    }

    /** A party hat whose brim bottom sits at (cx, y), at pixel scale s. */
    static void hat(PartyGfx g, float cx, float y, int s, int colour) { hat(g, cx, y, s, colour, 1f); }

    static void hat(PartyGfx g, float cx, float y, int s, int colour, float alpha) {
        Bitmap h = sprite("hat_" + Math.floorMod(colour, N));
        if (h == null) return;
        g.tex(h, Math.round(cx - h.getWidth() * s * 0.5f), Math.round(y - h.getHeight() * s), h.getWidth() * s, h.getHeight() * s,
                PartyGfx.alpha(PartyGfx.WHITE, alpha));
    }

    /** The cake on its plate, the plate's bottom at (cx, y); lit: candles flicker. */
    static void cake(PartyGfx g, float cx, float y, float s, boolean lit, float t) {
        Bitmap c = sprite(lit ? "cake_lit_" + ((int) (t * 6f) % 2) : "cake");
        if (c == null) return;
        float w = c.getWidth() * s, h = c.getHeight() * s;
        g.tex(c, Math.round(cx - w * 0.5f), Math.round(y - h), w, h);
    }

    /** A balloon tied with a pixel string of `string` length, its knot at (x, y). */
    static void balloon(PartyGfx g, float x, float y, int colour, float t, float string, int s) {
        Bitmap b = sprite("balloon_" + Math.floorMod(colour, N));
        if (b == null) return;
        int sway = (int) Math.round(Math.sin(t * 1.4f + x * 0.05f) * 1.4f);
        int str = PartyGfx.rgb(0.85f, 0.85f, 0.85f);
        for (float k = 0; k < string; k += s) {
            int dx = (int) Math.round(sway * (1f - k / string)) * s;
            g.rect(Math.round(x) + dx, Math.round(y + k), s, s, str);
        }
        float bx = Math.round(x + sway * s - b.getWidth() * s * 0.5f), by = Math.round(y - b.getHeight() * s + s * 2);
        g.tex(b, bx, by, b.getWidth() * s, b.getHeight() * s);
    }

    /** Little flags on a string, sagging between two nails. */
    static void garland(PartyGfx g, float x0, float x1, float y, float sag) {
        int s = 2;
        int str = PartyGfx.rgb(0.9f, 0.9f, 0.85f);
        for (float x = x0; x < x1; x += s) {
            float u = (x - x0) / (x1 - x0);
            g.rect(Math.round(x / s) * s, Math.round((y + sag * 4f * u * (1f - u)) / s) * s, s, s, str);
        }
        int n = Math.max(2, (int) ((x1 - x0) / 26f));
        for (int k = 0; k < n; k++) {
            float u = (k + 0.5f) / n;
            float fx = x0 + (x1 - x0) * u, fy = y + sag * 4f * u * (1f - u);
            Bitmap f = sprite("flag_" + (k % N));
            if (f != null) g.tex(f, Math.round(fx / s) * s - f.getWidth(), Math.round(fy / s) * s, f.getWidth() * s, f.getHeight() * s);
        }
    }

    /** A sign on the wall: paper, pixel letters. */
    static void banner(PartyGfx g, float cx, float y, String text, float size) {
        float tw = g.width(text, size);
        float w = tw + 24f;
        g.rect(Math.round(cx - w * 0.5f), Math.round(y - size - 6), Math.round(w), Math.round(size + 12), PartyGfx.rgb(0.98f, 0.93f, 0.8f));
        g.frame(Math.round(cx - w * 0.5f), Math.round(y - size - 6), Math.round(w), Math.round(size + 12), PartyGfx.rgb(0.62f, 0.38f, 0.2f), 2f);
        float x = cx - tw * 0.5f;
        for (int k = 0; k < text.length(); k++) {
            String ch = text.substring(k, k + 1);
            int col = PartyGfx.darkened(COLOURS[k % N], 0.25f);
            g.text(ch, x, y, size, col, false);
            x += g.width(ch, size);
        }
    }

    /** Falling paper bits in square pixels. Kept in the screen's own coordinates. */
    static final class Confetti {
        private final List<float[]> bits = new ArrayList<>();
        private final Random rng = new Random();
        float rate;
        final float w, h;

        Confetti(float w, float h, float rate) {
            this.w = w;
            this.h = h;
            this.rate = rate;
        }

        void burst(float x, float y, int n) {
            for (int k = 0; k < n; k++) {
                float a = (float) (rng.nextFloat() * Math.PI * 2);
                float sp = 80 + rng.nextFloat() * 240;
                bits.add(new float[]{x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp - 160, rng.nextInt(N), rng.nextFloat() * 6, 0});
            }
        }

        void update(float dt) {
            float want = rate * dt;
            while (want > 0) {
                if (rng.nextFloat() < want) {
                    bits.add(new float[]{rng.nextFloat() * w, -8, (rng.nextFloat() - 0.5f) * 40, 40 + rng.nextFloat() * 50, rng.nextInt(N), rng.nextFloat() * 6, 0});
                }
                want -= 1f;
            }
            for (int i = bits.size() - 1; i >= 0; i--) {
                float[] b = bits.get(i);
                b[3] = Math.min(b[3] + 260 * dt, 120);
                b[2] *= (1f - 1.5f * dt);
                b[0] += b[2] * dt + (float) Math.sin(b[5] + b[6] * 3f) * 20f * dt;
                b[1] += b[3] * dt;
                b[6] += dt;
                if (b[1] > h + 10) bits.remove(i);
            }
        }

        void draw(PartyGfx g) {
            for (float[] b : bits) {
                boolean flat = Math.sin(b[5] + b[6] * 6f) > 0;
                g.rect(Math.round(b[0] / 2f) * 2, Math.round(b[1] / 2f) * 2, flat ? 4 : 2, 2, COLOURS[(int) b[4]]);
            }
        }
    }
}

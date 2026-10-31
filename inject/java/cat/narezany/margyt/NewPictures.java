package cat.narezany.margyt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

/**
 * The pictures at the top of the pages that came in 2.2: the ghost, chat folders, the typing
 * animation, the interface editor and taking back reposts. Each shows what the function does,
 * moving, in the colours of the settings, the way the older pages' pictures do.
 */
final class NewPictures {

    private NewPictures() {}

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** A spring past its mark and back: 0 at the start, 1 at rest. */
    private static float spring(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return (float) (1 - Math.exp(-6 * t) * Math.cos(9 * t));
    }

    private static void glyph(Canvas canvas, String name, int colour, float cx, float cy, float size, int alpha) {
        Drawable picture = SettingsGlyph.make(name, colour);
        int half = Math.round(size / 2f);
        picture.setBounds(Math.round(cx) - half, Math.round(cy) - half, Math.round(cx) + half, Math.round(cy) + half);
        picture.setAlpha(alpha);
        picture.draw(canvas);
    }

    // ============================================================ the ghost

    /** A chat as the other person sees it: online, reading, typing; and with the ghost on, none of it. */
    static final class Ghosting extends SettingsPictures.Art {
        private final SettingsPictures.Eased on;
        private final Bitmap avatar;

        Ghosting(Context context, Skin skin, boolean on) {
            super(context, skin, 150);
            this.on = new SettingsPictures.Eased(on ? 1f : 0f);
            avatar = Launcher.preview("tilcayo");
        }

        void set(boolean value) {
            on.to(this, value ? 1f : 0f, 420);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float g = on.value, t = clock();
            float left = 22 * d, top = 20 * d;
            round(canvas, avatar, left + 18 * d, top + 18 * d, 18 * d, 255);
            words(canvas, "narezany", left + 46 * d, top + 13 * d, 14, skin.text, true, Paint.Align.LEFT);
            // online, or nothing to see
            paint.setColor(faint(0xFF35C46A, 1f - g));
            canvas.drawCircle(left + 31 * d, top + 31 * d, 5 * d, paint);
            words(canvas, Local.of("в сети"), left + 46 * d, top + 31 * d, 12, faint(skin.muted(), 1f - g), false, Paint.Align.LEFT);
            words(canvas, Local.of("был(а) давно"), left + 46 * d, top + 31 * d, 12, faint(skin.muted(), g), false, Paint.Align.LEFT);

            // your message and what it says under it
            float right = getWidth() - 22 * d, y = top + 56 * d;
            String said = Local.of("ты тут?");
            text.setTextSize(14 * d);
            float w = text.measureText(said) + 28 * d;
            paint.setColor(Accent.colour());
            box.set(right - w, y, right, y + 32 * d);
            canvas.drawRoundRect(box, 16 * d, 16 * d, paint);
            words(canvas, said, right - w / 2f, y + 21 * d, 14, 0xFFFFFFFF, false, Paint.Align.CENTER);
            words(canvas, Local.of("Прочитано"), right, y + 48 * d, 11, faint(skin.muted(), 1f - g), false, Paint.Align.RIGHT);
            words(canvas, Local.of("Отправлено"), right, y + 48 * d, 11, faint(skin.muted(), g), false, Paint.Align.RIGHT);

            // typing, which the ghost keeps to itself
            float bx = left, by = y + 28 * d;
            paint.setColor(faint(surface(0.12f), 1f - g * 0.85f));
            box.set(bx, by, bx + 62 * d, by + 28 * d);
            canvas.drawRoundRect(box, 14 * d, 14 * d, paint);
            for (int i = 0; i < 3; i++) {
                float hop = (float) Math.sin((t * 6f) - i * 0.9f);
                paint.setColor(faint(skin.muted(), (1f - g) * 0.9f + 0.1f * (1f - g)));
                canvas.drawCircle(bx + 18 * d + i * 13 * d, by + 14 * d - Math.max(0f, hop) * 4 * d * (1f - g), 3.5f * d, paint);
            }
            // the ghost itself, drifting
            if (g > 0.01f) {
                float drift = (float) Math.sin(t * 2f) * 4 * d;
                glyph(canvas, "visibility_off", Accent.colour(), bx + 31 * d, by + 14 * d + drift, 22 * d, Math.round(255 * g));
            }
            again();
        }
    }

    // ============================================================ chat folders

    /** Chips over a list of chats; with folders on, the chosen folder leaves only its chats. */
    static final class Folders extends SettingsPictures.Art {
        private final SettingsPictures.Eased on;
        private final float[] shown = {1, 1, 1, 1};

        Folders(Context context, Skin skin, boolean on) {
            super(context, skin, 176);
            this.on = new SettingsPictures.Eased(on ? 1f : 0f);
        }

        void set(boolean value) {
            on.to(this, value ? 1f : 0f, 420);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float f = on.value, t = clock();
            String[] chips = {Local.of("Основные"), Local.of("Друзья"), Local.of("Учёба")};
            // which chip is chosen: with folders on, it goes round them
            int chosen = f > 0.5f ? (int) (t / 1.8f) % 3 : 0;
            boolean[][] in = {{true, true, true, true}, {true, false, true, false}, {false, true, false, true}};
            float x = 22 * d, y = 16 * d;
            for (int i = 0; i < chips.length; i++) {
                float alpha = i == 0 ? 1f : f;
                if (alpha < 0.02f) continue;
                text.setTextSize(13 * d);
                float w = text.measureText(chips[i]) + 26 * d;
                box.set(x, y, x + w, y + 30 * d);
                boolean pick = i == chosen;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(faint(pick ? faint(Accent.colour(), 0.3f) : surface(0.08f), alpha));
                canvas.drawRoundRect(box, 9 * d, 9 * d, paint);
                words(canvas, chips[i], x + w / 2f, y + 20 * d, 13, faint(pick ? skin.text : skin.muted(), alpha), pick, Paint.Align.CENTER);
                x += w + 8 * d;
            }
            // the chats: those not in the folder close up
            float row = y + 42 * d, width = getWidth() - 44 * d;
            int[] tints = {0xFFF2A65A, 0xFF7FB7E8, 0xFF9CD08F, 0xFFD78FD1};
            float[] names = {0.42f, 0.30f, 0.50f, 0.36f};
            for (int i = 0; i < 4; i++) {
                float want = in[chosen][i] ? 1f : 0f;
                shown[i] += (want - shown[i]) * 0.18f;
                float h = 30 * d * shown[i];
                if (h < 0.5f) continue;
                int a = Math.round(255 * shown[i]);
                paint.setColor(tints[i]);
                paint.setAlpha(a);
                canvas.drawCircle(22 * d + 14 * d, row + h / 2f, 12 * d * shown[i], paint);
                paint.setColor(faint(skin.text, 0.75f * shown[i]));
                box.set(22 * d + 36 * d, row + h / 2f - 9 * d * shown[i], 22 * d + 36 * d + width * names[i], row + h / 2f - 3 * d * shown[i]);
                canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
                paint.setColor(faint(skin.muted(), 0.45f * shown[i]));
                box.set(22 * d + 36 * d, row + h / 2f + 2 * d * shown[i], 22 * d + 36 * d + width * (names[i] + 0.2f), row + h / 2f + 7 * d * shown[i]);
                canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
                row += h + 2 * d * shown[i];
            }
            again();
        }
    }

    // ============================================================ the typing animation

    /** A word being typed: with the animation, each letter springs in and the cursor glides after it. */
    static final class Typing extends SettingsPictures.Art {
        private final SettingsPictures.Eased on;

        Typing(Context context, Skin skin, boolean on) {
            super(context, skin, 96);
            this.on = new SettingsPictures.Eased(on ? 1f : 0f);
        }

        void set(boolean value) {
            on.to(this, value ? 1f : 0f, 300);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float a = on.value, t = clock();
            String word = Local.of("привет, это я");
            float step = 0.16f, cycle = word.length() * step + 1.6f;
            float local = t % cycle;
            float left = 22 * d, top = 22 * d, right = getWidth() - 22 * d;
            paint.setColor(surface(0.08f));
            box.set(left, top, right, top + 48 * d);
            canvas.drawRoundRect(box, 24 * d, 24 * d, paint);
            text.setTextSize(16 * d);
            text.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
            text.setTextAlign(Paint.Align.LEFT);
            float x = left + 20 * d, base = top + 30 * d;
            float cursorAt = x;
            for (int i = 0; i < word.length(); i++) {
                float since = local - i * step;
                if (since < 0) break;
                String letter = word.substring(i, i + 1);
                float w = text.measureText(letter);
                float grow = a > 0.5f ? spring(since / 0.45f) : 1f;
                float rise = a > 0.5f ? (1f - ease(since / 0.3f)) * 10 * d : 0f;
                canvas.save();
                canvas.translate(x + w / 2f, base + rise);
                canvas.scale(grow, grow);
                text.setColor(faint(skin.text, Math.min(1f, a > 0.5f ? since / 0.15f : 1f)));
                canvas.drawText(letter, -w / 2f, 0, text);
                canvas.restore();
                x += w;
                cursorAt = a > 0.5f ? cursorAt + (x - cursorAt) * Math.min(1f, since / 0.2f) : x;
            }
            boolean blink = a > 0.5f || (t % 1f) < 0.5f;
            if (blink) {
                paint.setColor(Accent.colour());
                box.set(cursorAt + 1 * d, base - 16 * d, cursorAt + 3 * d, base + 4 * d);
                canvas.drawRoundRect(box, 1 * d, 1 * d, paint);
            }
            again();
        }
    }

    // ============================================================ the interface editor

    /** A screen with buttons on it: one is picked out, then moved, made bigger and painted. */
    static final class Editor extends SettingsPictures.Art {
        private final Paint dashes = new Paint(Paint.ANTI_ALIAS_FLAG);

        Editor(Context context, Skin skin) {
            super(context, skin, 170);
            dashes.setStyle(Paint.Style.STROKE);
            dashes.setPathEffect(new DashPathEffect(new float[] {6, 5}, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = clock() % 4.8f;
            float w = 108 * d, h = 146 * d, left = (getWidth() - w) / 2f, top = 12 * d;
            // a phone
            paint.setColor(surface(0.08f));
            box.set(left, top, left + w, top + h);
            canvas.drawRoundRect(box, 18 * d, 18 * d, paint);
            // its buttons down the right, and a caption
            float cx = left + w - 20 * d;
            String[] icons = {"favorite_border", "chat", "share"};
            // the second button: picked at 0.6 s, moved by 1.6 s, grown by 2.6 s, painted by 3.4 s
            float move = ease((t - 1.0f) / 0.6f) * -34 * d, grow = 1f + 0.5f * ease((t - 2.0f) / 0.6f);
            float paintIn = ease((t - 2.8f) / 0.6f);
            for (int i = 0; i < 3; i++) {
                float cy = top + 52 * d + i * 30 * d;
                boolean edited = i == 1;
                float x = edited ? cx + move : cx, s = edited ? grow : 1f;
                int colour = edited ? Expressive.mix(skin.text, Accent.colour(), paintIn) : skin.text;
                glyph(canvas, icons[i], colour, x, cy, 20 * d * s, 255);
                if (edited && t > 0.6f) {
                    dashes.setColor(Accent.colour());
                    dashes.setStrokeWidth(1.5f * d);
                    float r = 15 * d * s;
                    box.set(x - r, cy - r, x + r, cy + r);
                    canvas.drawRoundRect(box, 6 * d, 6 * d, dashes);
                }
            }
            paint.setColor(faint(skin.text, 0.5f));
            box.set(left + 12 * d, top + h - 30 * d, left + 70 * d, top + h - 25 * d);
            canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
            paint.setColor(faint(skin.muted(), 0.4f));
            box.set(left + 12 * d, top + h - 20 * d, left + 56 * d, top + h - 15 * d);
            canvas.drawRoundRect(box, 3 * d, 3 * d, paint);
            // the finger
            if (t > 0.3f && t < 1.0f) {
                float tap = ease((t - 0.3f) / 0.3f);
                paint.setColor(faint(Accent.colour(), 0.35f * (1f - Math.max(0f, (t - 0.7f) / 0.3f))));
                canvas.drawCircle(cx, top + 82 * d, 14 * d * tap, paint);
            }
            again();
        }
    }

    // ============================================================ reposts

    /** A profile's tab of reposts emptying, one after another, and filling again. */
    static final class Reposts extends SettingsPictures.Art {

        Reposts(Context context, Skin skin) {
            super(context, skin, 128);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float t = clock() % 4.2f;
            int many = 4;
            float gap = 6 * d, side = Math.min(64 * d, (getWidth() - 44 * d - gap * (many - 1)) / many);
            float total = side * many + gap * (many - 1), left = (getWidth() - total) / 2f, top = 18 * d;
            int[] tints = {0xFF7FB7E8, 0xFFF2A65A, 0xFF9CD08F, 0xFFD78FD1};
            for (int i = 0; i < many; i++) {
                float gone = ease((t - 0.6f - i * 0.45f) / 0.35f);
                float back = ease((t - 3.5f) / 0.5f);
                float s = 1f - gone + gone * back;
                if (s < 0.02f) continue;
                float x = left + i * (side + gap) + side / 2f, y = top + side * 0.65f;
                paint.setColor(tints[i]);
                paint.setAlpha(Math.round(255 * s));
                box.set(x - side / 2f * s, y - side * 0.65f * s, x + side / 2f * s, y + side * 0.65f * s);
                canvas.drawRoundRect(box, 8 * d, 8 * d, paint);
                glyph(canvas, "repeat", 0xFFFFFFFF, x, y, 22 * d * s, Math.round(230 * s));
            }
            words(canvas, Local.of("Репосты"), getWidth() / 2f, top + side * 1.3f + 22 * d, 12, skin.muted(), true, Paint.Align.CENTER);
            again();
        }
    }
}

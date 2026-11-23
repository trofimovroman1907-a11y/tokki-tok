package cat.narezany.margyt;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The dialogue box at the bottom of the screen: text typed out letter by letter, the speaker's face on the left,
 * their name on a plate above, and a choice of answers when there is one.
 */
final class PartyText {
    /** One line: plain words, or with a face, a voice, a name, answers to choose from. */
    static final class Line {
        String t, face, voice = "sys", name;
        String[] choices;
        boolean plain;

        Line(String t) { this.t = t; }

        Line face(String f) { face = f; return this; }
        Line voice(String v) { voice = v; return this; }
        Line name(String n) { name = n; return this; }
        Line choices(String... c) { choices = c; return this; }
        Line plain() { plain = true; return this; }
    }

    private final PartyGame game;
    List<Line> lines = new ArrayList<>();
    int i;
    float shown;
    boolean active;
    Runnable done;
    int choice;
    int lastChoice = -1;
    private int blip;
    private boolean top;
    private long opened = -1;

    PartyText(PartyGame game) { this.game = game; }

    static List<Line> of(Object... said) {
        List<Line> out = new ArrayList<>();
        for (Object o : said) out.add(o instanceof Line ? (Line) o : new Line(String.valueOf(o)));
        return out;
    }

    void say(List<Line> l, Runnable cb) { say(l, cb, false); }

    void say(List<Line> l, Runnable cb, boolean atTop) {
        // 2.4: in Ukrainian and English, before the box starts typing it out (PartyTr)
        for (Line one : l) {
            one.t = PartyTr.t(one.t);
            one.name = PartyTr.t(one.name);
            one.choices = PartyTr.t(one.choices);
        }
        lines = l;
        i = 0;
        shown = 0f;
        active = !l.isEmpty();
        done = cb;
        choice = 0;
        top = atTop;
        opened = game.frame;
        if (!active && cb != null) cb.run();
    }

    private Line cur() { return lines.get(i); }

    private String text() {
        Line c = cur();
        return c.face == null && !c.plain ? "> " + c.t : c.t;
    }

    void update(float dt) {
        if (!active) return;
        PartyInput in = game.input;
        String full = text();
        int before = (int) shown;
        if (shown < full.length()) {
            char ch = full.charAt(Math.min((int) shown, full.length() - 1));
            float speed = ".!?,".indexOf(ch) >= 0 ? 9f : 34f;
            shown = Math.min(shown + speed * dt, full.length());
            if ((int) shown != before && full.charAt(before) != ' ' && blip % 2 == 0) {
                game.sfx.play("blip_" + cur().voice, game.randf(0.95f, 1.05f));
            }
            blip += (int) shown - before;
        }
        Line c = cur();
        if (game.frame == opened) return;
        if (c.choices != null && shown >= full.length()) {
            if (in.justPressed(PartyInput.LEFT) || in.justPressed(PartyInput.RIGHT)) {
                choice = (choice + 1) % c.choices.length;
                game.sfx.play("move");
            }
        }
        if (in.justPressed(PartyInput.CANCEL) && shown < full.length()) {
            shown = full.length();
        } else if (in.justPressed(PartyInput.OK)) {
            if (shown < full.length()) {
                shown = full.length();
            } else {
                if (c.choices != null) {
                    lastChoice = choice;
                    game.sfx.play("ok");
                }
                i++;
                shown = 0f;
                choice = 0;
                if (i >= lines.size()) {
                    active = false;
                    Runnable cb = done;
                    done = null;
                    if (cb != null) cb.run();
                }
            }
        }
    }

    Bitmap face(String key) {
        Bitmap b = game.tex("face_" + key);
        return b != null ? b : game.tex("face_normal");
    }

    void draw(PartyGfx g) {
        if (!active || i >= lines.size()) return;
        PartyRect r = new PartyRect(16, top ? 16 : 324, 608, 140);
        g.rect(r, PartyGfx.BLACK);
        g.frame(r.grow(-3), PartyGfx.WHITE, 6f);
        Line c = cur();
        float x = r.x + 26f;
        if (c.face != null) {
            g.tex(face(c.face), r.x + 18, r.y + 22, 96, 96);
            x = r.x + 132f;
        }
        // the speaker's name on a plate above the box
        if (c.name != null) {
            float nw = g.width(c.name, 18) + 28f;
            PartyRect nr = new PartyRect(r.x + 12, r.y - 30, nw, 32);
            g.rect(nr, PartyGfx.BLACK);
            g.frame(nr.grow(-2), PartyGfx.WHITE, 4f);
            g.text(c.name, nr.x + 14, nr.y + 22, 18, PartyGame.MINT, false);
        }
        float w = r.ex() - x - 24f;
        // long text smaller, so it stays inside the box
        float size = 22, step = 30f, y = r.y + 42f;
        if (g.wrap(text(), w, 22).size() > 3) {
            size = 18;
            step = 24f;
            y = r.y + 36f;
        }
        String txt = text().substring(0, Math.min((int) shown, text().length()));
        for (String ln : g.wrap(txt, w, size)) {
            g.text(ln, x, y, size, PartyGfx.WHITE, false);
            y += step;
        }
        if (c.choices != null && shown >= text().length()) {
            float cx = x + 20f;
            for (int k = 0; k < c.choices.length; k++) {
                int col = k == choice ? PartyGame.MINT : PartyGfx.WHITE;
                if (k == choice) g.tex(game.tex("soul"), cx - 26, r.ey() - 44, 16, 16);
                g.text(c.choices[k], cx, r.ey() - 30, 22, col, false);
                cx += 200f;
            }
        }
    }

    static List<String> list(String... s) { return new ArrayList<>(Arrays.asList(s)); }
}

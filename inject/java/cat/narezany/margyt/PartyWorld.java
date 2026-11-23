package cat.narezany.margyt;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The world from above: Нарезанный walks through rooms, talks to the neighbours, fights at random.
 * Rooms are in PartyRooms, talk and plot in PartyStory; here are walking, the menu, the shop and drawing helpers.
 */
final class PartyWorld implements PartyGame.Scene {
    private static final float SPEED = 150f;
    static final int MINT = PartyGame.MINT, GREEN = PartyGame.GREEN;

    final PartyGame game;
    PartyGfx g;
    String id = "";
    PartyRooms.Def def;
    PartyVec pos = new PartyVec(0, 0);
    String facing = "down";
    float walkT, cam, t;
    float fade = 1f;
    PartyRooms.Exit leaving;
    float encT = -1f;
    String encEnemy = "";
    float encDist, encNext = 900f;
    boolean locked;
    int quiz;
    String menu = "";
    int msel;
    List<Object[]> shop = new ArrayList<>();
    final PartyDeco.Confetti confetti = new PartyDeco.Confetti(640, 480, 0f);

    PartyWorld(PartyGame game) { this.game = game; }

    Bitmap tex(String n) { return game.tex(n); }

    void enter(String room, PartyVec at, String face) {
        id = room;
        def = PartyRooms.def(this, room);
        pos = at.copy();
        facing = face;
        fade = 1f;
        leaving = null;
        encDist = 0f;
        encNext = game.randf(700f, 1300f);
        game.room = room;
        game.spawn = at.copy();
        game.playMusic(def.music);
        cam = clampCam();
        PartyStory.onEnter(this, room);
    }

    void refresh() { def = PartyRooms.def(this, id); }

    private float clampCam() {
        return Math.max(0f, Math.min(def.size.x - 640f, pos.x - 320f));
    }

    // ------------------------------------------------------------ moving

    @Override
    public void update(float dt) {
        t += dt;
        confetti.update(dt);
        PartyInput in = game.input;
        if (leaving != null) {
            fade = Math.min(fade + dt * 5f, 1f);
            if (fade >= 1f) enter(leaving.to, leaving.at, leaving.face);
            return;
        }
        fade = Math.max(fade - dt * 4f, 0f);
        if (encT >= 0f) {
            encT += dt;
            if (encT > 0.9f) game.startBattle(encEnemy);
            return;
        }
        if (!menu.isEmpty()) {
            menuInput();
            return;
        }
        if (game.box.active || locked) {
            walkT = 0f;
            return;
        }
        if (in.justPressed(PartyInput.MENU)) {
            menu = "main";
            msel = 0;
            game.sfx.play("ok");
            return;
        }
        PartyVec v = new PartyVec(in.axisX(), in.axisY());
        if (!v.zero()) {
            if (Math.abs(v.x) > Math.abs(v.y)) facing = v.x > 0 ? "right" : "left";
            else if (v.y != 0f) facing = v.y > 0 ? "down" : "up";
            PartyVec before = pos.copy();
            PartyVec step = v.normalized().mul(SPEED * dt);
            move(step.x, 0);
            move(0, step.y);
            walkT += dt;
            encDist += pos.dist(before);
        } else {
            walkT = 0f;
        }
        PartyRect feet = feet(pos);
        for (PartyRooms.Exit e : def.exits) {
            if (e.r.intersects(feet)) {
                if (e.need != null && !game.flag(e.need)) {
                    PartyVec back = v.normalized().mul(14f);
                    pos = pos.sub(back);
                    say(e.deny, null);
                    return;
                }
                leaving = e;
                return;
            }
        }
        for (PartyRooms.Talk tr : def.triggers) {
            if (tr.r.intersects(feet)) PartyStory.trigger(this, tr.id);
        }
        if (in.justPressed(PartyInput.OK)) {
            PartyRect probe = feet.copy();
            switch (facing) {
                case "down": probe = probe.moved(0, 22); break;
                case "up": probe = probe.moved(0, -26); break;
                case "left": probe = probe.moved(-22, -6); break;
                default: probe = probe.moved(22, -6); break;
            }
            for (PartyRooms.Talk k : talks()) {
                if (k.r.intersects(probe)) {
                    game.sfx.play("ok");
                    PartyStory.talk(this, k.id);
                    break;
                }
            }
        }
        // random meetings
        if (def.enc.length > 0 && encDist > encNext && !game.box.active && encT < 0f && leaving == null) {
            encDist = 0f;
            encNext = game.randf(900f, 1500f);
            encounter(def.enc[game.randi(def.enc.length)]);
        }
        cam = clampCam();
    }

    /** Everything that can be talked to: things in the room, people, the diskette, a diary page. */
    private List<PartyRooms.Talk> talks() {
        // people first: a bench or a sign behind somebody must not answer for them
        List<PartyRooms.Talk> out = new ArrayList<>();
        for (PartyRooms.Npc n : def.npcs) {
            if (n.show) out.add(new PartyRooms.Talk(new PartyRect(n.p.x - 26, n.p.y - 46, 52, 56), n.id));
        }
        if (def.disk != null) out.add(new PartyRooms.Talk(new PartyRect(def.disk.x - 4, def.disk.y - 4, 40, 40), "disk"));
        if (def.diaryPos != null && !game.flags.containsKey("diary_" + def.diaryN)) {
            out.add(new PartyRooms.Talk(new PartyRect(def.diaryPos.x - 6, def.diaryPos.y - 6, 44, 44), "diary"));
        }
        out.addAll(def.talk);
        return out;
    }

    private PartyRect feet(PartyVec p) { return new PartyRect(p.x - 12, p.y - 10, 24, 10); }

    private List<PartyRect> solids() {
        List<PartyRect> out = new ArrayList<>(def.solids);
        for (PartyRooms.Npc n : def.npcs) if (n.show) out.add(new PartyRect(n.p.x - 14, n.p.y - 10, 28, 12));
        return out;
    }

    private void move(float dx, float dy) {
        PartyVec np = pos.add(dx, dy);
        PartyRect f = feet(np);
        boolean inExit = false;
        for (PartyRooms.Exit e : def.exits) if (e.r.grow(2).intersects(f)) inExit = true;
        if (!inExit && !def.floor.encloses(f)) return;
        for (PartyRect s : solids()) if (s.intersects(f)) return;
        pos = np;
    }

    void say(List<PartyText.Line> lines, Runnable cb) { game.box.say(lines, cb); }

    void say(Object... lines) { game.box.say(PartyText.of(lines), null); }

    void encounter(String enemy) {
        game.spawn = pos.copy();
        game.facing = facing;
        encT = 0f;
        encEnemy = enemy;
        game.playMusic("");
        game.sfx.play("glitch");
    }

    void saveHere() {
        game.hp = game.maxHp;
        game.spawn = pos.copy();
        game.facing = facing;
        game.saveGame();
        game.sfx.play("save");
    }

    // ------------------------------------------------------------ the C menu and the shop

    void openShop(List<Object[]> list) {
        shop = list;
        menu = "shop";
        msel = 0;
    }

    private void menuInput() {
        PartyInput in = game.input;
        List<String> opts = menuOpts();
        if (in.justPressed(PartyInput.UP) && !opts.isEmpty()) {
            msel = (msel - 1 + opts.size()) % opts.size();
            game.sfx.play("move");
        } else if (in.justPressed(PartyInput.DOWN) && !opts.isEmpty()) {
            msel = (msel + 1) % opts.size();
            game.sfx.play("move");
        } else if (in.justPressed(PartyInput.CANCEL) || in.justPressed(PartyInput.MENU)) {
            menu = menu.equals("items") ? "main" : "";
            msel = 0;
        } else if (in.justPressed(PartyInput.OK) && !opts.isEmpty()) {
            game.sfx.play("ok");
            switch (menu) {
                case "main":
                    if (msel == 0) {
                        if (game.items.isEmpty()) {
                            game.sfx.play("miss");
                        } else {
                            menu = "items";
                            msel = 0;
                        }
                    } else {
                        menu = "";
                    }
                    break;
                case "items": {
                    String it = game.items.remove(msel);
                    Object[] d = PartyGame.ITEM_DATA.get(it);
                    if (d == null) d = new Object[]{5, "Ты ешь это. +5 HP."};
                    game.hp = Math.min(game.hp + (Integer) d[0], game.maxHp);
                    game.sfx.play("heal");
                    menu = "";
                    say(d[1]);
                    break;
                }
                case "shop": {
                    if (msel >= shop.size()) {
                        menu = "";
                        say(PartyStory.P("Бариста", "Заходи ещё! Латте само себя не выпьет."));
                        return;
                    }
                    String name = (String) shop.get(msel)[0];
                    int price = (Integer) shop.get(msel)[1];
                    if (game.money < price) {
                        game.sfx.play("miss");
                        menu = "";
                        say(PartyText.of(PartyStory.P("Бариста", "Не хватает донатов. Может, стрим запустишь?")), () -> menu = "shop");
                    } else if (game.items.size() >= PartyGame.MAX_ITEMS) {
                        game.sfx.play("miss");
                        menu = "";
                        say(PartyText.of("Карманы полны."), () -> menu = "shop");
                    } else {
                        game.money -= price;
                        game.items.add(name);
                        game.sfx.play("item");
                    }
                    break;
                }
            }
        }
    }

    private List<String> menuOpts() {
        List<String> out = new ArrayList<>();
        switch (menu) {
            case "main":
                out.add("ВЕЩИ");
                out.add("ЗАКРЫТЬ");
                break;
            case "items":
                out.addAll(game.items);
                break;
            case "shop":
                for (Object[] s : shop) out.add(s[0] + "  " + s[1] + " ₽");
                out.add("Уйти");
                break;
        }
        return out;
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void draw(PartyGfx gfx) {
        g = gfx;
        g.transform(-cam, 0);
        PartyRooms.draw(this, id);
        // people and Нарезанный by depth
        List<Object[]> ents = new ArrayList<>();
        for (PartyRooms.Npc n : def.npcs) if (n.show) ents.add(new Object[]{n.p.y, n});
        ents.add(new Object[]{pos.y, null});
        ents.sort((a, b) -> Float.compare((Float) a[0], (Float) b[0]));
        for (Object[] en : ents) {
            PartyRooms.Npc n = (PartyRooms.Npc) en[1];
            if (n == null) {
                drawPlayer();
            } else {
                Bitmap tx = tex(n.tex);
                if (n.draw != null) {
                    n.draw.run();
                } else if (tx != null) {
                    float bob = (float) Math.sin(t * 2f + n.p.x);
                    float w = tx.getWidth() * 2, h = tx.getHeight() * 2;
                    g.tex(tx, n.p.x - tx.getWidth(), n.p.y - (h - 2 + bob), w, h);
                    if (n.hat >= 0) PartyDeco.hatOn(g, tx, n.p.x - tx.getWidth(), n.p.y - (h - 2 + bob), 2, n.hat);
                }
            }
        }
        if (def.disk != null) g.tex(tex("disk"), def.disk.x, def.disk.y + (float) Math.sin(t * 3f) * 2f, 32, 32);
        if (def.diaryPos != null && !game.flags.containsKey("diary_" + def.diaryN)) {
            g.tex(tex("diary"), def.diaryPos.x, def.diaryPos.y + (float) Math.sin(t * 2.5f) * 2f, 32, 32);
        }
        PartyRooms.drawFront(this, id);
        g.transform(0, 0);
        confetti.draw(g);
        if (!menu.isEmpty()) drawMenu();
        if (encT >= 0f) {
            g.rect(0, 0, 640, 480, PartyGfx.BLACK);
            if ((int) (encT * 8f) % 2 == 0 || encT > 0.5f) {
                PartyVec sp = new PartyVec(pos.x - cam, pos.y - 30).lerp(new PartyVec(320, 360), Math.max(0f, Math.min(1f, (encT - 0.5f) / 0.4f)));
                g.tex(tex("soul"), sp.x - 8, sp.y - 8, 16, 16);
            }
        }
        if (fade > 0f) g.rect(0, 0, 640, 480, PartyGfx.alpha(PartyGfx.BLACK, fade));
    }

    private void drawPlayer() {
        int f = 0;
        if (walkT > 0f) f = 1 + (int) (walkT * 6f) % 2;
        g.tex(tex("narez_" + facing + "_" + f), pos.x - 24, pos.y - 62, 48, 64);
        if (game.flag("hat_on")) PartyDeco.hatOn(g, tex("narez_" + facing + "_" + f), pos.x - 24, pos.y - 62, 2, 0);
    }

    private void drawMenu() {
        PartyRect stat = new PartyRect(24, 24, 200, 120);
        g.rect(stat, PartyGfx.BLACK);
        g.frame(stat.grow(-2), PartyGfx.WHITE, 4f);
        text("НАРЕЗАННЫЙ", 40, 56, 18, PartyGfx.WHITE, false);
        text("LV 1", 40, 84, 16, PartyGfx.WHITE, false);
        text("HP " + game.hp + "/" + game.maxHp, 40, 108, 16, PartyGfx.WHITE, false);
        text("₽  " + game.money, 40, 132, 16, PartyGfx.rgb(1f, 0.85f, 0.4f), false);
        List<String> opts = menuOpts();
        PartyRect r = menu.equals("main") ? new PartyRect(24, 156, 200, 30 + opts.size() * 30) : new PartyRect(240, 24, 360, 40 + opts.size() * 30);
        g.rect(r, PartyGfx.BLACK);
        g.frame(r.grow(-2), PartyGfx.WHITE, 4f);
        if (menu.equals("shop")) {
            text("Кофейня «Мятный латте»", r.x + 24, r.y + 30, 16, MINT, false);
            r = r.moved(0, 22);
        }
        for (int k = 0; k < opts.size(); k++) {
            float y = r.y + 40 + k * 30;
            text(opts.get(k), r.x + 44, y, 18, k == msel ? MINT : PartyGfx.WHITE, false);
            if (k == msel) g.tex(tex("soul"), r.x + 18, y - 15, 16, 16);
        }
    }

    // ------------------------------------------------------------ helpers for the rooms

    void text(String s, float x, float y, float size, int col, boolean center) { g.text(s, x, y, size, col, center); }
    void text(String s, float x, float y, float size, int col) { g.text(s, x, y, size, col, true); }

    void wall(PartyRect r, int base, int line) {
        g.rect(r, base);
        float y = r.y + 10f;
        while (y < r.ey()) {
            g.line(r.x, y, r.ex(), y, line, 2f);
            y += 22f;
        }
    }

    void tiles(PartyRect r, int a, int b, float s) {
        float y = r.y;
        int row = 0;
        while (y < r.ey()) {
            float x = r.x;
            int col = 0;
            while (x < r.ex()) {
                g.rect(x, y, Math.min(s, r.ex() - x), Math.min(s, r.ey() - y), (row + col) % 2 == 0 ? a : b);
                x += s;
                col++;
            }
            y += s;
            row++;
        }
    }

    void tiles(PartyRect r, int a, int b) { tiles(r, a, b, 40f); }

    void planks(PartyRect r, int base, int line) {
        g.rect(r, base);
        float y = r.y;
        while (y < r.ey()) {
            g.line(r.x, y, r.ex(), y, line, 2f);
            y += 18f;
        }
    }

    void plate(PartyRect r, String s, float size) {
        g.rect(r, PartyGfx.rgb(0.75f, 0.7f, 0.55f));
        g.frame(r, PartyGfx.rgb(0.45f, 0.38f, 0.28f), 2f);
        text(s, r.cx(), r.cy() + size * 0.35f, size, PartyGfx.rgb(0.25f, 0.15f, 0.1f));
    }

    void door(PartyRect r, int col, boolean open) {
        g.rect(r, open ? PartyGfx.rgb(0.04f, 0.04f, 0.05f) : col);
        g.frame(r, PartyGfx.darkened(col, 0.4f), 3f);
        if (!open) g.rect(r.ex() - 12, r.cy(), 5, 5, PartyGfx.rgb(0.9f, 0.8f, 0.3f));
    }

    void door(PartyRect r, int col) { door(r, col, false); }

    void window(PartyRect r, boolean lit) {
        g.rect(r, lit ? PartyGfx.rgb(1f, 0.85f, 0.5f) : PartyGfx.rgb(0.12f, 0.14f, 0.22f));
        g.frame(r, PartyGfx.rgb(0.3f, 0.28f, 0.3f), 3f);
        g.line(r.cx(), r.y, r.cx(), r.ey(), PartyGfx.rgb(0.3f, 0.28f, 0.3f), 2f);
    }

    void rack(PartyVec p) {
        g.rect(p.x, p.y, 42, 118, PartyGfx.rgb(0.08f, 0.08f, 0.1f));
        g.frame(p.x, p.y, 42, 118, PartyGfx.rgb(0.25f, 0.26f, 0.3f), 2f);
        int[] cols = {GREEN, PartyGfx.rgb(1f, 0.7f, 0.2f), PartyGfx.rgb(0.3f, 0.6f, 1f)};
        for (int row = 0; row < 6; row++) {
            float y = p.y + 8f + row * 18f;
            g.rect(p.x + 4, y, 34, 12, PartyGfx.rgb(0.14f, 0.15f, 0.18f));
            for (int led = 0; led < 3; led++) {
                boolean on = ((t * (1.3f + led * 0.7f) + row * 0.37f + p.x * 0.01f) % 1f) < 0.5f;
                g.rect(p.x + 8 + led * 8, y + 4, 4, 4, on ? cols[led] : PartyGfx.darkened(cols[led], 0.75f));
            }
        }
    }

    void bot(PartyVec c, String sayText) {
        g.rect(c.x - 18, c.y - 6, 36, 30, PartyGfx.rgb(0.55f, 0.57f, 0.6f));
        g.rect(c.x - 14, c.y - 30, 28, 22, PartyGfx.rgb(0.65f, 0.67f, 0.7f));
        g.rect(c.x - 10, c.y - 26, 20, 12, PartyGfx.rgb(0.05f, 0.1f, 0.08f));
        text((t % 2f) < 1f ? sayText : "...", c.x, c.y - 16, 9, GREEN);
        g.line(c.x, c.y - 30, c.x + 4, c.y - 40, PartyGfx.rgb(0.6f, 0.6f, 0.6f), 2f);
        g.rect(c.x + 2, c.y - 44, 5, 5, (t % 0.6f) < 0.3f ? PartyGfx.rgb(1f, 0.3f, 0.3f) : PartyGfx.rgb(0.4f, 0.1f, 0.1f));
    }

    void desk(PartyRect r, boolean withPc, boolean pcOn) {
        g.rect(r, PartyGfx.rgb(0.5f, 0.42f, 0.32f));
        g.rect(r.x, r.ey() - 6, r.w, 6, PartyGfx.rgb(0.38f, 0.3f, 0.22f));
        if (withPc) {
            PartyRect m = new PartyRect(r.cx() - 18, r.y - 30, 36, 28);
            g.rect(m, PartyGfx.rgb(0.2f, 0.2f, 0.22f));
            g.rect(m.grow(-3), pcOn ? PartyGfx.rgb(0.15f, 0.35f, 0.55f) : PartyGfx.rgb(0.05f, 0.05f, 0.06f));
            g.rect(m.cx() - 3, m.ey(), 6, 4, PartyGfx.rgb(0.2f, 0.2f, 0.22f));
        }
    }

    void tree(PartyVec p, float s) {
        g.rect(p.x - 5 * s, p.y - 40 * s, 10 * s, 40 * s, PartyGfx.rgb(0.36f, 0.24f, 0.15f));
        int leaf = PartyGfx.rgb(0.24f, 0.5f, 0.28f);
        float[][] big = {{0, -62, 28}, {-18, -50, 20}, {18, -50, 20}, {0, -82, 20}};
        for (float[] c : big) g.circle(p.x + c[0] * s, p.y + c[1] * s, c[2] * s, leaf);
        float[][] light = {{-8, -70, 9}, {10, -60, 8}, {-2, -86, 7}};
        for (float[] c : light) g.circle(p.x + c[0] * s, p.y + c[1] * s, c[2] * s, PartyGfx.lightened(leaf, 0.18f));
        g.circle(p.x, p.y + 2, 18 * s, PartyGfx.rgba(0, 0, 0, 0.12f));
    }

    void tree(PartyVec p) { tree(p, 1f); }

    void bush(PartyVec p, float w) {
        int c = PartyGfx.rgb(0.22f, 0.46f, 0.26f);
        for (int k = 0; k < (int) (w / 14f); k++) g.circle(p.x - w * 0.5f + 8 + k * 14, p.y - 10 - (k % 2) * 4, 13, c);
        g.circle(p.x - w * 0.25f, p.y - 16, 5, PartyGfx.rgb(0.9f, 0.4f, 0.5f));
        g.circle(p.x + w * 0.2f, p.y - 12, 4, PartyGfx.rgb(1f, 0.85f, 0.4f));
    }

    void bin(PartyVec p) {
        g.rect(p.x - 12, p.y - 30, 24, 30, PartyGfx.rgb(0.28f, 0.4f, 0.3f));
        g.rect(p.x - 14, p.y - 34, 28, 6, PartyGfx.rgb(0.22f, 0.32f, 0.24f));
        g.line(p.x - 6, p.y - 24, p.x - 6, p.y - 6, PartyGfx.rgb(0.2f, 0.3f, 0.22f), 2f);
        g.line(p.x + 6, p.y - 24, p.x + 6, p.y - 6, PartyGfx.rgb(0.2f, 0.3f, 0.22f), 2f);
    }

    void plant(PartyVec p, boolean big) {
        float k = big ? 1.4f : 1f;
        g.rect(p.x - 10 * k, p.y - 16 * k, 20 * k, 16 * k, PartyGfx.rgb(0.7f, 0.42f, 0.28f));
        g.rect(p.x - 12 * k, p.y - 18 * k, 24 * k, 4 * k, PartyGfx.rgb(0.6f, 0.36f, 0.24f));
        for (float a : new float[]{-1.2f, -0.6f, 0f, 0.6f, 1.2f}) {
            float tx = p.x + (float) Math.sin(a) * 18 * k, ty = p.y - 18 * k - (float) Math.cos(a) * 22 * k;
            g.line(p.x, p.y - 18 * k, tx, ty, PartyGfx.rgb(0.25f, 0.55f, 0.3f), 5f * k);
        }
    }

    void hydrant(PartyVec p) {
        g.rect(p.x - 7, p.y - 24, 14, 24, PartyGfx.rgb(0.8f, 0.2f, 0.2f));
        g.rect(p.x - 10, p.y - 16, 20, 5, PartyGfx.rgb(0.7f, 0.15f, 0.15f));
        g.circle(p.x, p.y - 24, 7, PartyGfx.rgb(0.85f, 0.25f, 0.25f));
    }

    void flowerbed(PartyRect r) {
        g.rect(r, PartyGfx.rgb(0.35f, 0.25f, 0.18f));
        g.frame(r, PartyGfx.rgb(0.55f, 0.55f, 0.55f), 3f);
        float x = r.x + 8;
        int k = 0;
        int[] cols = {PartyGfx.rgb(1f, 0.4f, 0.5f), PartyGfx.rgb(1f, 0.85f, 0.3f), PartyGfx.rgb(0.6f, 0.5f, 1f)};
        while (x < r.ex() - 4) {
            g.line(x, r.ey() - 4, x, r.y + 6, PartyGfx.rgb(0.3f, 0.6f, 0.3f), 2f);
            g.circle(x, r.y + 6, 4, cols[k % 3]);
            x += 12;
            k++;
        }
    }

    void acUnit(PartyVec p) {
        g.rect(p.x, p.y, 30, 20, PartyGfx.rgb(0.85f, 0.86f, 0.88f));
        for (int k = 0; k < 4; k++) g.line(p.x + 4, p.y + 4 + k * 4, p.x + 26, p.y + 4 + k * 4, PartyGfx.rgb(0.6f, 0.62f, 0.65f), 1f);
    }

    void poster(PartyRect r, int col, String s) {
        g.rect(r, col);
        g.frame(r, PartyGfx.darkened(col, 0.4f), 2f);
        text(s, r.cx(), r.cy() + 4, 10, PartyGfx.luminance(col) < 0.5f ? PartyGfx.WHITE : PartyGfx.rgb(0.15f, 0.15f, 0.15f));
    }

    void rug(PartyRect r, int a, int b) {
        g.rect(r, a);
        g.frame(r.grow(-6), b, 3f);
        g.rect(r.grow(-14), PartyGfx.lightened(a, 0.1f));
    }

    void lightPool(PartyVec p, float rad, int col) {
        for (int k = 0; k < 4; k++) g.circle(p.x, p.y, rad * (1f - k * 0.2f), PartyGfx.alpha(col, 0.05f));
    }

    void shelf(PartyRect r) {
        g.rect(r, PartyGfx.rgb(0.42f, 0.3f, 0.2f));
        int rows = (int) (r.h / 26f);
        int[] cols = {PartyGfx.rgb(0.7f, 0.25f, 0.25f), PartyGfx.rgb(0.25f, 0.4f, 0.7f), PartyGfx.rgb(0.85f, 0.75f, 0.35f), PartyGfx.rgb(0.3f, 0.6f, 0.4f)};
        for (int row = 0; row < rows; row++) {
            float y = r.y + 4 + row * 26;
            float x = r.x + 4;
            int k = row * 3;
            while (x < r.ex() - 10) {
                float bw = 6f + (k * 7) % 5;
                g.rect(x, y, bw, 20, cols[k % 4]);
                x += bw + 1;
                k++;
            }
            g.rect(r.x, y + 20, r.w, 4, PartyGfx.rgb(0.32f, 0.22f, 0.14f));
        }
    }

    void chair(PartyVec p, int col) {
        g.rect(p.x - 12, p.y - 34, 24, 8, PartyGfx.darkened(col, 0.2f));
        g.rect(p.x - 12, p.y - 20, 24, 10, col);
        g.rect(p.x - 11, p.y - 10, 4, 10, PartyGfx.darkened(col, 0.3f));
        g.rect(p.x + 7, p.y - 10, 4, 10, PartyGfx.darkened(col, 0.3f));
    }

    void chair(PartyVec p) { chair(p, PartyGfx.rgb(0.4f, 0.3f, 0.22f)); }

    void pipeH(float y, float x0, float x1, int col) {
        g.rect(x0, y, x1 - x0, 10, col);
        g.line(x0, y + 2, x1, y + 2, PartyGfx.lightened(col, 0.3f), 2f);
        float x = x0 + 40f;
        while (x < x1) {
            g.rect(x, y - 2, 6, 14, PartyGfx.darkened(col, 0.3f));
            x += 120f;
        }
    }

    void cables(float y, float x0, float x1) {
        int[] cols = {PartyGfx.rgb(0.15f, 0.15f, 0.18f), PartyGfx.rgb(0.2f, 0.25f, 0.5f), PartyGfx.rgb(0.5f, 0.2f, 0.2f)};
        for (int k = 0; k < 3; k++) {
            int n = (int) ((x1 - x0) / 20f) + 1;
            float[] pts = new float[n * 2];
            for (int i = 0; i < n; i++) {
                float x = x0 + i * 20f;
                pts[i * 2] = x;
                pts[i * 2 + 1] = y + k * 5 + (float) Math.sin(x * 0.03f + k) * 3f;
            }
            g.polyline(pts, cols[k], 3f);
        }
    }
}

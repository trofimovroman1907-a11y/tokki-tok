package cat.narezany.margyt;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rooms: size, floor, walls in the way, exits, people, and how each is drawn.
 * Bedroom, flat, yard, café, the street by the business centre, support (hall, open space, the moderator's office),
 * the data centre (gate, server room, cooling, legacy, the hall), the secret museum of hate, and the party at home.
 */
final class PartyRooms {
    private PartyRooms() {}

    private static final int MINT = PartyGame.MINT, GREEN = PartyGame.GREEN;

    static final class Talk {
        final PartyRect r;
        final String id;

        Talk(PartyRect r, String id) { this.r = r; this.id = id; }
    }

    static final class Npc {
        final String id, tex;
        final PartyVec p;
        boolean show = true;
        /** A party hat's colour (an index into PartyDeco.COLOURS), or -1 for none. */
        int hat = -1;
        /** Drawn by the room itself rather than from a sprite. */
        Runnable draw;

        Npc(String id, String tex, PartyVec p) { this.id = id; this.tex = tex; this.p = p; }

        Npc show(boolean s) { show = s; return this; }
        Npc hat(int h) { hat = h; return this; }
    }

    static final class Exit {
        final PartyRect r;
        final String to, face;
        final PartyVec at;
        String need;
        List<PartyText.Line> deny;

        Exit(PartyRect r, String to, PartyVec at, String face) { this.r = r; this.to = to; this.at = at; this.face = face; }
    }

    static final class Def {
        PartyVec size = new PartyVec(640, 480);
        PartyRect floor;
        String music = "";
        List<PartyRect> solids = new ArrayList<>();
        List<Talk> talk = new ArrayList<>();
        List<Npc> npcs = new ArrayList<>();
        PartyVec disk, diaryPos;
        int diaryN;
        List<Exit> exits = new ArrayList<>();
        List<Talk> triggers = new ArrayList<>();
        String[] enc = new String[0];
    }

    private static PartyRect r(float x, float y, float w, float h) { return new PartyRect(x, y, w, h); }
    private static PartyVec v(float x, float y) { return new PartyVec(x, y); }
    private static Talk tk(float x, float y, float w, float h, String id) { return new Talk(r(x, y, w, h), id); }

    private static Exit ex(PartyRect rect, String to, PartyVec at, String face) { return new Exit(rect, to, at, face); }

    private static Exit ex(PartyRect rect, String to, PartyVec at, String face, String need, Object... deny) {
        Exit e = new Exit(rect, to, at, face);
        e.need = need;
        e.deny = PartyText.of(deny);
        return e;
    }

    private static Def d(float w, PartyRect floor, String music) {
        Def d = new Def();
        d.size = v(w, 480);
        d.floor = floor;
        d.music = music;
        return d;
    }

    static Def def(PartyWorld w, String room) {
        PartyGame game = w.game;
        Def d;
        switch (room) {
            case "bedroom":
                d = d(640, r(40, 150, 560, 290), "home");
                d.solids.addAll(Arrays.asList(r(60, 150, 110, 150), r(420, 150, 160, 56),
                        r(572, 230, 26, 22), r(492, 222, 26, 16), r(458, 374, 54, 24), r(522, 344, 62, 18)));
                d.talk.addAll(Arrays.asList(tk(420, 150, 160, 60, "pc"), tk(60, 150, 110, 150, "bed"), tk(280, 318, 64, 40, "cat"),
                        tk(240, 130, 90, 24, "poster"), tk(110, 130, 60, 24, "window"), tk(520, 330, 70, 50, "balloons")));
                d.exits.add(ex(r(600, 260, 40, 90), "flat", v(60, 300), "right", "pc_seen", "Сначала проверю почту. Что-то тревожно."));
                d.exits.add(ex(r(10, 392, 30, 40), "secret", v(420, 335), "left"));
                return d;
            case "flat":
                d = d(960, r(0, 200, 960, 200), "home");
                d.solids.addAll(Arrays.asList(r(60, 196, 60, 22), r(140, 196, 60, 22), r(300, 292, 110, 40), r(760, 196, 70, 20),
                        r(234, 196, 34, 20), r(930, 196, 24, 20), r(306, 274, 28, 14), r(376, 274, 28, 14)));
                d.talk.addAll(Arrays.asList(tk(60, 190, 60, 34, "fridge"), tk(140, 190, 60, 34, "stove"), tk(300, 286, 110, 52, "note"),
                        tk(560, 186, 44, 20, "mirror"), tk(760, 190, 70, 30, "shoes"), tk(440, 186, 100, 20, "garland")));
                d.exits.add(ex(r(-20, 200, 20, 200), "bedroom", v(570, 300), "left"));
                d.exits.add(ex(r(860, 184, 60, 16), "yard", v(150, 252), "down"));
                return d;
            case "yard":
                d = d(1600, r(0, 230, 1600, 170), "street");
                d.solids.addAll(Arrays.asList(r(520, 262, 100, 20), r(195, 250, 22, 14), r(1200, 250, 22, 14), r(1080, 262, 12, 30), r(890, 380, 20, 14),
                        r(290, 230, 20, 14), r(970, 230, 20, 14), r(1490, 230, 20, 14), r(198, 230, 64, 18), r(1413, 230, 54, 18), r(660, 230, 70, 26)));
                d.talk.addAll(Arrays.asList(tk(1040, 236, 90, 50, "sign_yard"), tk(520, 256, 100, 30, "bench")));
                d.npcs.add(new Npc("grandma", "npc_grandma", v(570, 284)).hat(0));
                d.npcs.add(new Npc("kid", "npc_kid", v(760, 330)).hat(2));
                d.npcs.add(new Npc("mizunya", "npc_mizunya", v(460, 300)).hat(5));
                d.npcs.add(new Npc("courier", "npc_courier", v(1130, 290)).show(!game.flag("moderator_done")));
                d.exits.add(ex(r(120, 220, 60, 14), "flat", v(890, 222), "down"));
                d.exits.add(ex(r(1280, 220, 60, 14), "cafe", v(320, 420), "up"));
                d.exits.add(ex(r(1600, 230, 20, 170), "street2", v(60, 320), "right"));
                if (game.flag("met_kid")) d.enc = new String[]{"spam", "popup"};
                return d;
            case "cafe":
                d = d(640, r(40, 200, 560, 240), "home");
                d.solids.addAll(Arrays.asList(r(200, 196, 240, 40), r(90, 300, 70, 36), r(470, 330, 70, 36),
                        r(44, 196, 34, 20), r(562, 196, 36, 20), r(68, 338, 24, 14), r(158, 338, 24, 14), r(448, 368, 24, 14), r(538, 368, 24, 14)));
                d.talk.addAll(Arrays.asList(tk(200, 196, 240, 56, "barista"), tk(470, 324, 70, 48, "cafe_table")));
                d.npcs.add(new Npc("bear", "npc_banned1", v(560, 360)).hat(3));
                d.disk = v(60, 380);
                d.diaryPos = v(108, 290);
                d.diaryN = 1;
                d.exits.add(ex(r(290, 440, 60, 20), "yard", v(1310, 250), "down"));
                return d;
            case "street2":
                d = d(1280, r(0, 230, 1280, 170), "street");
                d.solids.addAll(Arrays.asList(r(900, 230, 30, 24), r(1030, 230, 30, 24),
                        r(70, 230, 20, 14), r(510, 230, 20, 14), r(618, 230, 64, 18), r(846, 248, 28, 16), r(1100, 230, 120, 26), r(380, 256, 120, 30)));
                d.talk.addAll(Arrays.asList(tk(200, 214, 220, 24, "billboard"), tk(940, 222, 80, 20, "turnstile")));
                d.npcs.add(new Npc("gorilla", "npc_gorilla", v(154, 382)));
                d.npcs.add(new Npc("smoker", "npc_operator", v(600, 300)));
                d.triggers.add(tk(900, 250, 160, 30, "captcha"));
                d.exits.add(ex(r(-20, 230, 20, 170), "yard", v(1560, 320), "left"));
                d.exits.add(ex(r(950, 220, 60, 14), "lobby", v(480, 420), "up", "captcha_done", "Турникет не пускает. На экране: «Докажите, что вы не робот»."));
                d.enc = new String[]{"spam", "popup"};
                return d;
            case "lobby":
                d = d(960, r(0, 200, 960, 240), "office");
                d.solids.addAll(Arrays.asList(r(140, 226, 160, 40), r(860, 200, 40, 30),
                        r(24, 196, 34, 20), r(904, 312, 34, 20), r(558, 280, 278, 52)));
                d.talk.addAll(Arrays.asList(tk(140, 220, 160, 56, "reception"), tk(860, 196, 40, 40, "cooler"), tk(560, 300, 300, 30, "chairs")));
                d.npcs.add(new Npc("fox", "npc_banned2", v(700, 350)).hat(4));
                d.disk = v(60, 380);
                d.exits.add(ex(r(450, 440, 60, 20), "street2", v(980, 252), "down"));
                d.exits.add(ex(r(440, 186, 80, 14), "support", v(100, 330), "right"));
                return d;
            case "support":
                d = d(1280, r(0, 200, 1280, 220), "office");
                d.solids.addAll(Arrays.asList(r(200, 250, 90, 30), r(420, 250, 90, 30), r(640, 250, 90, 30), r(860, 250, 90, 30),
                        r(200, 340, 90, 30), r(420, 340, 90, 30), r(640, 340, 90, 30), r(860, 340, 90, 30), r(1040, 250, 100, 30),
                        r(144, 196, 34, 20), r(1164, 384, 34, 20), r(1100, 330, 50, 34)));
                d.talk.addAll(Arrays.asList(tk(1040, 244, 100, 46, "case"), tk(640, 244, 90, 42, "op_desk")));
                d.npcs.add(new Npc("op1", "npc_operator", v(330, 300)));
                d.npcs.add(new Npc("op2", "npc_operator", v(770, 300)));
                d.npcs.add(new Npc("op3", "npc_operator", v(550, 400)));
                d.diaryPos = v(888, 324);
                d.diaryN = 2;
                d.exits.add(ex(r(-20, 200, 20, 220), "lobby", v(480, 222), "down"));
                d.exits.add(ex(r(1200, 186, 60, 14), "office", v(320, 420), "up"));
                d.enc = new String[]{"ticket", "popup"};
                return d;
            case "office":
                d = d(640, r(60, 200, 520, 240), "office");
                d.solids.addAll(Arrays.asList(r(220, 230, 200, 40), r(544, 212, 34, 20)));
                d.talk.addAll(Arrays.asList(tk(220, 224, 200, 56, "mod_desk"), tk(60, 186, 140, 20, "binders")));
                d.npcs.add(new Npc("owl", "npc_owl", v(320, 226)).show(!game.flag("moderator_kill")));
                d.exits.add(ex(r(290, 440, 60, 20), "support", v(1230, 222), "down"));
                d.exits.add(ex(r(580, 290, 20, 70), "dc_gate", v(80, 320), "right", "pass", "Служебный выход. Заперт на пропуск."));
                return d;
            case "dc_gate":
                d = d(1280, r(0, 230, 1280, 170), "hall");
                d.solids.addAll(Arrays.asList(r(560, 262, 70, 30), r(109, 230, 22, 14), r(409, 230, 22, 14), r(456, 248, 28, 16), r(792, 230, 14, 12)));
                if (!game.flag("gate_open")) d.solids.add(r(700, 230, 16, 170));
                d.talk.addAll(Arrays.asList(tk(690, 230, 36, 170, "gate"), tk(560, 256, 70, 40, "booth")));
                d.npcs.add(new Npc("guard", "npc_guard", v(650, 300)));
                d.npcs.add(new Npc("courier2", "npc_courier", v(300, 330)).show(game.flag("moderator_done")));
                d.disk = v(500, 350);
                d.exits.add(ex(r(-20, 230, 20, 170), "office", v(560, 325), "left"));
                d.exits.add(ex(r(1160, 220, 60, 14), "corridor", v(50, 290), "right"));
                return d;
            case "corridor":
                d = d(1280, r(0, 220, 1280, 140), "hall");
                d.solids.add(r(500, 220, 44, 30));
                d.talk.addAll(Arrays.asList(tk(140, 200, 70, 24, "sign"), tk(500, 220, 44, 34, "bot"), tk(620, 200, 400, 24, "rack")));
                d.disk = v(296, 250);
                d.triggers.add(tk(860, 220, 40, 140, "bug"));
                d.exits.add(ex(r(-20, 220, 20, 140), "dc_gate", v(1190, 250), "down"));
                d.exits.add(ex(r(1280, 220, 20, 140), "cooling", v(60, 300), "right"));
                if (game.flag("bug_done")) d.enc = new String[]{"bug"};
                return d;
            case "cooling":
                d = d(960, r(0, 220, 960, 160), "hall");
                d.talk.addAll(Arrays.asList(tk(290, 204, 30, 24, "valve_a"), tk(370, 204, 30, 24, "valve_b"), tk(450, 204, 30, 24, "valve_c"),
                        tk(110, 204, 70, 24, "cool_note"), tk(560, 204, 100, 24, "thermo")));
                d.disk = v(40, 330);
                d.diaryPos = v(760, 320);
                d.diaryN = 3;
                d.exits.add(ex(r(-20, 220, 20, 160), "corridor", v(1230, 300), "left"));
                d.exits.add(ex(r(960, 220, 20, 160), "legacy", v(70, 330), "right", "cool", "Дверь не открывается. Табло: «ПЕРЕГРЕВ. Доступ закрыт»."));
                return d;
            case "legacy":
                d = d(640, r(40, 150, 560, 290), "hall");
                d.solids.addAll(Arrays.asList(r(410, 150, 70, 40), r(100, 180, 40, 40), r(540, 372, 40, 38)));
                d.talk.addAll(Arrays.asList(tk(410, 150, 70, 44, "terminal"), tk(100, 180, 40, 44, "trash"), tk(280, 130, 80, 24, "door"),
                        tk(60, 130, 120, 24, "papers"), tk(480, 130, 100, 24, "sign_legacy")));
                d.disk = v(504, 372);
                d.exits.add(ex(r(0, 290, 40, 90), "cooling", v(900, 300), "left"));
                if (game.flag("door_open")) d.exits.add(ex(r(290, 130, 60, 22), "hall", v(70, 300), "right"));
                return d;
            case "secret":
                d = d(640, r(200, 230, 240, 170), "");
                d.talk.addAll(Arrays.asList(tk(230, 210, 180, 24, "charlie"), tk(200, 210, 30, 24, "headwall"), tk(410, 210, 30, 24, "headwall")));
                d.exits.add(ex(r(440, 310, 40, 50), "bedroom", v(62, 420), "right"));
                return d;
            case "hall":
                d = d(1600, r(0, 220, 1600, 140), "hall");
                d.solids.add(r(1440, 230, 70, 60));
                d.talk.addAll(Arrays.asList(tk(180, 200, 60, 24, "mon1"), tk(580, 200, 60, 24, "mon2"), tk(980, 200, 60, 24, "mon3")));
                d.exits.add(ex(r(-20, 220, 20, 140), "legacy", v(320, 175), "down"));
                d.triggers.add(tk(1230, 220, 30, 140, "boss"));
                return d;
            case "party":
                return party(w);
        }
        return d(640, r(0, 0, 640, 480), "");
    }

    /** The flat in the evening: everyone came, the cake is on the table. */
    private static Def party(PartyWorld w) {
        PartyGame game = w.game;
        Def d = d(960, r(0, 200, 960, 200), "party");
        d.solids.addAll(Arrays.asList(r(60, 196, 60, 22), r(140, 196, 60, 22), r(400, 282, 160, 40), r(760, 196, 70, 20),
                r(234, 196, 34, 20), r(930, 196, 24, 20)));
        d.talk.addAll(Arrays.asList(tk(400, 276, 160, 52, "cake"), tk(60, 190, 60, 34, "p_fridge"), tk(860, 190, 70, 30, "p_gifts"),
                tk(440, 186, 100, 20, "p_banner")));
        boolean spare = "spare".equals(game.flags.get("ending"));
        d.npcs.add(new Npc("p_grandma", "npc_grandma", v(250, 260)).hat(0));
        d.npcs.add(new Npc("p_kid", "npc_kid", v(330, 360)).hat(2));
        d.npcs.add(new Npc("p_barista", "npc_barista", v(640, 270)).hat(1));
        d.npcs.add(new Npc("p_bear", "npc_banned1", v(700, 360)).hat(3));
        d.npcs.add(new Npc("p_fox", "npc_banned2", v(780, 300)).hat(4));
        d.npcs.add(new Npc("p_guard", "npc_guard", v(880, 350)).hat(5));
        d.npcs.add(new Npc("p_courier", "npc_courier", v(170, 360)).hat(1));
        d.npcs.add(new Npc("p_owl", "npc_owl", v(600, 360)).hat(2).show(!game.flag("moderator_kill")));
        d.npcs.add(new Npc("p_smoker", "npc_operator", v(240, 340)).hat(4));
        d.npcs.add(new Npc("p_mizunya", "npc_mizunya", v(820, 380)).hat(5));
        Npc cat = new Npc("p_cat", "tilcayo", v(480, 372));
        cat.draw = () -> {
            Bitmap ct = w.tex("tilcayo");
            if (ct == null) return;
            float bob = (float) Math.sin(w.t * 2f);
            w.g.tex(ct, 480 - ct.getWidth(), 372 - ct.getHeight() * 2 + bob, ct.getWidth() * 2, ct.getHeight() * 2);
            PartyDeco.hatOn(w.g, ct, 480 - ct.getWidth(), 372 - ct.getHeight() * 2 + bob, 2, 5);
        };
        d.npcs.add(cat);
        if (spare) {
            Npc boss = new Npc("p_boss", "boss_head", v(520, 260));
            boss.draw = () -> {
                float bob = (float) Math.sin(w.t * 2f) * 2f;
                PartyVec b = v(520, 262);
                w.g.tex(w.tex("boss_body"), b.x - 45, b.y - 76 + bob, 90, 82);
                w.g.tex(w.tex("boss_arm_l"), b.x - 40, b.y - 54 + bob, 30, 45);
                w.g.tex(w.tex("boss_arm_r"), b.x + 10, b.y - 54 + bob, 30, 45);
                w.g.tex(w.tex("boss_head"), b.x - 21, b.y - 90 + bob * 1.5f, 42, 35);
                w.text((w.t % 4f) > 0.2f ? "^_^" : "-_-", b.x, b.y - 70 + bob * 1.5f, 11, GREEN);
                PartyDeco.hat(w.g, b.x, b.y - 86 + bob * 1.5f, 2, 3);
            };
            d.npcs.add(boss);
        }
        d.exits.add(ex(r(860, 184, 60, 16), "party", v(890, 230), "down", "never", "Куда? Гости пришли. Сначала торт."));
        return d;
    }

    // ------------------------------------------------------------ drawing

    static void draw(PartyWorld w, String room) {
        switch (room) {
            case "bedroom": bedroom(w); break;
            case "flat": flat(w, false); break;
            case "yard": street(w, 1600, true); break;
            case "cafe": cafe(w); break;
            case "street2": street(w, 1280, false); break;
            case "lobby": lobby(w); break;
            case "support": support(w); break;
            case "office": office(w); break;
            case "dc_gate": gate(w); break;
            case "corridor": corridor(w); break;
            case "cooling": cooling(w); break;
            case "legacy": legacy(w); break;
            case "secret": secret(w); break;
            case "hall": hall(w); break;
            case "party": flat(w, true); break;
        }
        deco(w, room);
    }

    /** What hangs in front of everybody: balloons on strings that would be hidden by the people otherwise. */
    static void drawFront(PartyWorld w, String room) {
        PartyGfx g = w.g;
        if (room.equals("party")) {
            boolean lit = !w.game.flag("cake_done");
            PartyDeco.cake(g, 480, 300, 2, lit, w.t);
        }
    }

    private static int c(float r, float g, float b) { return PartyGfx.rgb(r, g, b); }

    private static void bedroom(PartyWorld w) {
        PartyGfx g = w.g;
        float t = w.t;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        w.wall(r(40, 40, 560, 110), c(0.22f, 0.16f, 0.3f), c(0.27f, 0.2f, 0.36f));
        w.planks(r(40, 150, 560, 290), c(0.36f, 0.24f, 0.16f), c(0.3f, 0.2f, 0.13f));
        g.rect(600, 260, 40, 90, c(0.36f, 0.24f, 0.16f));
        for (int k = 0; k < 4; k++) g.rect(36 - (k % 2) * 2, 396 + k * 8, 2, 6, c(0.22f, 0.16f, 0.12f));
        g.rect(110, 60, 60, 60, c(0.35f, 0.45f, 0.7f));
        g.frame(110, 60, 60, 60, c(0.5f, 0.4f, 0.55f), 4f);
        g.line(140, 60, 140, 120, c(0.5f, 0.4f, 0.55f), 3f);
        g.rect(240, 56, 90, 70, MINT);
        w.text("MARGY", 285, 98, 18, c(0.1f, 0.25f, 0.2f));
        g.rect(60, 150, 110, 150, c(0.5f, 0.35f, 0.25f));
        g.rect(66, 156, 98, 30, c(0.92f, 0.92f, 0.95f));
        g.rect(66, 190, 98, 104, MINT);
        g.rect(66, 190, 98, 8, c(0.3f, 0.6f, 0.5f));
        g.rect(420, 170, 160, 36, c(0.45f, 0.3f, 0.2f));
        g.rect(470, 110, 70, 56, c(0.2f, 0.2f, 0.22f));
        g.rect(476, 116, 58, 42, (t % 1.2f) < 0.6f ? c(0.08f, 0.02f, 0.02f) : c(0.25f, 0.04f, 0.04f));
        w.text("BAN", 505, 145, 18, c(1f, 0.35f, 0.35f));
        g.rect(498, 166, 14, 6, c(0.2f, 0.2f, 0.22f));
        g.rect(250, 300, 130, 70, c(0.55f, 0.2f, 0.3f));
        g.frame(258, 308, 114, 54, c(0.65f, 0.3f, 0.38f), 2f);
        Bitmap ct = w.tex("tilcayo");
        if (ct != null) {
            g.tex(ct, 282, 300 + (float) Math.sin(t * 2f), ct.getWidth() * 2, ct.getHeight() * 2);
            PartyDeco.hatOn(g, ct, 282, 300 + (float) Math.sin(t * 2f), 2, 1);
        }
        if ((t % 3f) < 1.6f) w.text("z", 350, 300 - (t % 3f) * 8f, 16, c(0.8f, 0.8f, 1f));
    }

    private static void flat(PartyWorld w, boolean evening) {
        PartyGfx g = w.g;
        g.rect(0, 0, 960, 480, PartyGfx.BLACK);
        w.wall(r(0, 60, 960, 140), evening ? c(0.34f, 0.26f, 0.3f) : c(0.3f, 0.27f, 0.22f), evening ? c(0.38f, 0.3f, 0.34f) : c(0.34f, 0.31f, 0.25f));
        // kitchen tiles and floor
        w.tiles(r(0, 200, 260, 200), c(0.8f, 0.8f, 0.78f), c(0.65f, 0.66f, 0.66f), 26f);
        w.planks(r(260, 200, 700, 200), c(0.36f, 0.24f, 0.16f), c(0.3f, 0.2f, 0.13f));
        // fridge with magnets, and the stove
        g.rect(60, 100, 60, 118, c(0.88f, 0.9f, 0.92f));
        g.line(60, 150, 120, 150, c(0.6f, 0.6f, 0.62f), 2f);
        g.rect(70, 120, 8, 8, MINT);
        g.rect(90, 165, 10, 7, c(1f, 0.5f, 0.4f));
        g.rect(140, 150, 60, 68, c(0.25f, 0.25f, 0.28f));
        for (float x : new float[]{152f, 176f}) g.circle(x + 6, 160, 7f, c(0.1f, 0.1f, 0.1f));
        g.rect(158, 128, 26, 22, c(0.6f, 0.6f, 0.62f));
        if (evening) {
            // the long table with the cake, a tablecloth, plates and cups
            g.rect(400, 282, 160, 40, c(0.5f, 0.36f, 0.24f));
            g.rect(396, 278, 168, 14, c(0.98f, 0.95f, 0.9f));
            for (int k = 0; k < 5; k++) g.rect(396 + k * 34, 292, 10, 8, c(0.98f, 0.95f, 0.9f));
            for (float x : new float[]{414f, 532f}) {
                g.circle(x, 284, 7, c(0.92f, 0.92f, 0.96f));
                g.rect(x + 10, 276, 6, 8, PartyDeco.COLOURS[(int) x % 6]);
            }
            g.rect(406, 322, 6, 10, c(0.3f, 0.25f, 0.2f));
            g.rect(548, 322, 6, 10, c(0.3f, 0.25f, 0.2f));
            // the pile of presents by the door
            int[] cols = {PartyDeco.COLOURS[0], PartyDeco.COLOURS[1], PartyDeco.COLOURS[3], PartyDeco.COLOURS[2]};
            float[][] boxes = {{866, 196, 26, 20}, {892, 200, 22, 16}, {876, 182, 20, 14}, {904, 186, 18, 14}};
            for (int k = 0; k < boxes.length; k++) {
                float[] b = boxes[k];
                g.rect(b[0], b[1], b[2], b[3], cols[k]);
                g.rect(b[0] + b[2] * 0.5f - 2, b[1], 4, b[3], c(1f, 0.95f, 0.7f));
                g.rect(b[0], b[1] + b[3] * 0.4f, b[2], 3, c(1f, 0.95f, 0.7f));
            }
        } else {
            // the table with the note
            g.rect(300, 286, 110, 46, c(0.5f, 0.36f, 0.24f));
            g.rect(340, 294, 22, 16, c(0.95f, 0.93f, 0.8f));
        }
        // mirror, the shoe shelf, the front door
        g.rect(560, 90, 44, 90, c(0.6f, 0.75f, 0.85f));
        g.frame(560, 90, 44, 90, c(0.5f, 0.4f, 0.3f), 4f);
        g.line(570, 100, 590, 140, c(0.85f, 0.95f, 1f), 2f);
        g.rect(760, 180, 70, 36, c(0.4f, 0.3f, 0.22f));
        int[] shoes = {c(0.9f, 0.9f, 0.9f), c(0.2f, 0.2f, 0.25f), MINT};
        for (int k = 0; k < 3; k++) g.rect(766 + k * 22, 196, 16, 8, shoes[k]);
        w.door(r(860, 100, 60, 100), c(0.45f, 0.32f, 0.22f));
        w.text("ВЫХОД", 890, 92, 12, c(0.8f, 0.75f, 0.6f));
    }

    /** The yard and the street by the business centre: sky, houses, pavement, trees, a road with cars. */
    private static void street(PartyWorld w, float width, boolean yard) {
        PartyGfx g = w.g;
        float t = w.t;
        PartyGame game = w.game;
        for (int k = 0; k < 6; k++) g.rect(0, k * 20, width, 20, PartyGfx.lerp(c(0.95f, 0.6f, 0.45f), c(0.45f, 0.6f, 0.9f), k / 5f));
        for (int k = 0; k < 3; k++) {
            float cx = (80f + k * 420f + t * 6f) % (width + 200f) - 100f;
            float[][] puffs = {{0, 0, 16}, {18, -4, 20}, {38, 2, 14}};
            for (float[] p : puffs) g.circle(cx + p[0], 28 + k * 9 + p[1], p[2], PartyGfx.rgba(1, 1, 1, 0.6f));
        }
        if (yard) {
            float x = 0f;
            int hue = 0;
            int[] cols = {c(0.62f, 0.5f, 0.42f), c(0.55f, 0.58f, 0.6f), c(0.68f, 0.6f, 0.48f)};
            while (x < width) {
                float bw = 220f;
                int col = cols[hue % 3];
                g.rect(x, 40, bw, 190, col);
                g.rect(x, 36, bw, 8, PartyGfx.darkened(col, 0.3f));
                g.line(x + 40, 36, x + 40, 14, c(0.3f, 0.3f, 0.3f), 2f);
                g.line(x + 30, 20, x + 50, 20, c(0.3f, 0.3f, 0.3f), 2f);
                g.rect(x, 140, bw, 4, PartyGfx.darkened(col, 0.15f));
                for (int wy = 0; wy < 3; wy++) {
                    for (int wx = 0; wx < 4; wx++) {
                        boolean lit = ((x * 0.13f + wx * 1.7f + wy * 2.3f) % 3f) < 1f;
                        w.window(r(x + 20 + wx * 50, 60 + wy * 50, 30, 30), lit);
                        if ((wx + wy + hue) % 5 == 0) {
                            g.rect(x + 16 + wx * 50, 88 + wy * 50, 38, 6, PartyGfx.darkened(col, 0.25f));
                            for (int k = 0; k < 4; k++) g.rect(x + 18 + wx * 50 + k * 10, 82 + wy * 50, 2, 8, c(0.3f, 0.3f, 0.3f));
                        }
                    }
                }
                w.acUnit(v(x + 180, 104 + (hue % 2) * 50));
                x += bw;
                hue++;
            }
            // a cat in a window
            g.rect(570, 160, 30, 30, c(1f, 0.85f, 0.5f));
            g.circle(585, 182, 8, c(0.2f, 0.2f, 0.2f));
            g.poly(c(0.2f, 0.2f, 0.2f), 578, 176, 580, 168, 583, 175);
            g.poly(c(0.2f, 0.2f, 0.2f), 587, 175, 590, 168, 592, 176);
            w.text("MARGY", 860, 210, 18, PartyGfx.rgba(0.44f, 0.87f, 0.72f, 0.8f));
            w.door(r(120, 150, 60, 80), c(0.4f, 0.3f, 0.25f));
            g.rect(110, 226, 80, 6, c(0.6f, 0.6f, 0.6f));
            // balloons tied to the door of the birthday boy's house
            PartyDeco.balloon(g, 112, 168, 0, t, 40, 2);
            PartyDeco.balloon(g, 188, 172, 1, t, 36, 2);
            g.rect(1230, 120, 160, 110, c(0.3f, 0.55f, 0.48f));
            g.rect(1222, 116, 176, 30, MINT);
            for (int k = 0; k < 8; k++) g.rect(1222 + k * 22, 146, 11, 8, k % 2 == 0 ? c(0.95f, 0.95f, 0.95f) : MINT);
            w.text("МЯТНЫЙ ЛАТТЕ", 1310, 137, 14, c(0.1f, 0.25f, 0.2f));
            w.window(r(1240, 162, 30, 40), true);
            w.window(r(1350, 162, 30, 40), true);
            w.door(r(1280, 160, 60, 70), c(0.2f, 0.35f, 0.3f));
        } else {
            g.rect(0, 130, 700, 100, c(0.5f, 0.48f, 0.46f));
            for (int k = 0; k < 6; k++) w.window(r(30 + k * 110, 150, 40, 40), k % 3 == 1);
            g.rect(200, 70, 220, 110, c(0.95f, 0.95f, 0.95f));
            g.frame(200, 70, 220, 110, c(0.3f, 0.3f, 0.3f), 4f);
            w.text("ПУСТЬ ИИ", 310, 110, 20, c(0.2f, 0.3f, 0.8f));
            w.text("СДЕЛАЕТ ВСЁ", 310, 136, 20, c(0.2f, 0.3f, 0.8f));
            w.text("ЗА ТЕБЯ!", 310, 162, 16, c(0.8f, 0.2f, 0.3f));
            g.rect(250, 180, 8, 50, c(0.3f, 0.3f, 0.3f));
            g.rect(362, 180, 8, 50, c(0.3f, 0.3f, 0.3f));
            g.rect(700, 20, 580, 210, c(0.3f, 0.42f, 0.55f));
            for (int gx = 0; gx < 12; gx++) {
                for (int gy = 0; gy < 4; gy++) {
                    g.rect(710 + gx * 48, 30 + gy * 46, 42, 40, PartyGfx.lerp(c(0.45f, 0.62f, 0.78f), c(0.8f, 0.9f, 1f), 0.2f * ((gx + gy) % 3)));
                }
            }
            g.rect(700, 14, 580, 8, c(0.2f, 0.28f, 0.36f));
            g.rect(900, 150, 160, 80, c(0.2f, 0.25f, 0.3f));
            g.rect(890, 140, 180, 14, c(0.15f, 0.18f, 0.22f));
            w.text("ПОДДЕРЖКА", 980, 151, 13, PartyGfx.WHITE);
            g.rect(940, 210, 80, 20, game.flag("captcha_done") ? MINT : c(0.8f, 0.25f, 0.25f));
        }
        // pavement and road
        w.tiles(r(0, 230, width, 170), c(0.55f, 0.55f, 0.56f), c(0.5f, 0.5f, 0.52f), 34f);
        g.rect(0, 230, width, 6, c(0.42f, 0.42f, 0.44f));
        g.rect(0, 400, width, 80, c(0.18f, 0.18f, 0.2f));
        g.rect(0, 396, width, 8, c(0.7f, 0.7f, 0.7f));
        for (float dx = 0f; dx < width; dx += 80f) g.rect(dx, 438, 40, 4, c(0.9f, 0.9f, 0.8f));
        float cx2 = (t * 160f) % (width + 400f) - 200f;
        g.rect(cx2, 412, 90, 30, c(0.8f, 0.3f, 0.25f));
        g.rect(cx2 + 18, 404, 50, 14, c(0.6f, 0.8f, 0.95f));
        g.circle(cx2 + 20, 444, 7, c(0.1f, 0.1f, 0.1f));
        g.circle(cx2 + 70, 444, 7, c(0.1f, 0.1f, 0.1f));
        float cx3 = width + 200f - (t * 110f + 600f) % (width + 400f);
        g.rect(cx3, 448, 80, 26, c(0.3f, 0.5f, 0.8f));
        g.rect(cx3 + 14, 440, 44, 12, c(0.6f, 0.8f, 0.95f));
        if (yard) {
            for (PartyVec p : new PartyVec[]{v(300, 240), v(980, 240), v(1500, 240)}) w.tree(p);
            w.bush(v(230, 246), 60);
            w.bush(v(1440, 246), 50);
            w.flowerbed(r(660, 236, 70, 20));
            w.bin(v(205, 262));
            w.bin(v(1210, 262));
            w.hydrant(v(900, 392));
            // the bench grandma sits on
            g.rect(520, 250, 100, 10, c(0.5f, 0.35f, 0.2f));
            g.rect(520, 262, 100, 10, c(0.6f, 0.42f, 0.25f));
            g.rect(526, 272, 6, 12, c(0.2f, 0.2f, 0.2f));
            g.rect(608, 272, 6, 12, c(0.2f, 0.2f, 0.2f));
            w.plate(r(1040, 236, 90, 24), "← ДОМ  →", 12);
            g.rect(1082, 260, 6, 30, c(0.35f, 0.3f, 0.25f));
            // a puddle
            g.rect(400, 360, 60, 14, c(0.45f, 0.55f, 0.7f));
            g.rect(410, 362, 20, 3, c(0.8f, 0.9f, 1f));
        } else {
            for (PartyVec p : new PartyVec[]{v(80, 240), v(520, 240)}) w.tree(p);
            w.bush(v(650, 246), 60);
            w.bin(v(860, 262));
            w.flowerbed(r(1100, 236, 120, 20));
            g.rect(900, 222, 30, 32, c(0.6f, 0.6f, 0.65f));
            g.rect(1030, 222, 30, 32, c(0.6f, 0.6f, 0.65f));
            // a bus stop
            g.rect(380, 236, 120, 8, c(0.3f, 0.32f, 0.36f));
            g.rect(384, 244, 6, 40, c(0.3f, 0.32f, 0.36f));
            g.rect(490, 244, 6, 40, c(0.3f, 0.32f, 0.36f));
            g.rect(396, 262, 88, 8, c(0.55f, 0.4f, 0.25f));
            w.plate(r(386, 210, 108, 24), "ДАТА-ЦЕНТР 5 км", 10);
        }
        // street lamps and their light
        for (float lx = 160f; lx < width; lx += 560f) {
            w.lightPool(v(lx + 3, 300), 60, c(1f, 0.9f, 0.6f));
            g.rect(lx, 160, 6, 80, c(0.25f, 0.25f, 0.28f));
            g.rect(lx - 8, 156, 22, 8, c(1f, 0.9f, 0.6f));
        }
    }

    private static void cafe(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        w.wall(r(40, 50, 560, 150), c(0.32f, 0.55f, 0.48f), c(0.36f, 0.6f, 0.52f));
        w.planks(r(40, 200, 560, 240), c(0.42f, 0.3f, 0.2f), c(0.36f, 0.25f, 0.16f));
        g.rect(290, 440, 60, 20, c(0.42f, 0.3f, 0.2f));
        // the menu on the wall
        g.rect(250, 70, 140, 80, c(0.15f, 0.15f, 0.15f));
        w.text("МЕНЮ", 320, 92, 14, MINT);
        w.text("латте 8", 320, 110, 12, PartyGfx.WHITE);
        w.text("круассан 14", 320, 124, 12, PartyGfx.WHITE);
        w.text("энергетик 20", 320, 138, 12, PartyGfx.WHITE);
        w.text("торт 25 ★", 320, 150, 11, PartyGame.GOLD);
        // the counter and the bunny barista
        Bitmap bt = w.tex("npc_barista");
        g.tex(bt, 296, 150 + (float) Math.sin(w.t * 2f), 48, 64);
        PartyDeco.hatOn(g, bt, 296, 150 + (float) Math.sin(w.t * 2f), 2, 1);
        g.rect(200, 196, 240, 40, c(0.55f, 0.38f, 0.26f));
        g.rect(200, 196, 240, 6, c(0.7f, 0.5f, 0.35f));
        g.rect(410, 176, 20, 20, c(0.6f, 0.6f, 0.62f));
        // tables
        for (PartyVec p : new PartyVec[]{v(90, 300), v(470, 330)}) {
            g.rect(p.x, p.y, 70, 36, c(0.75f, 0.62f, 0.45f));
            g.rect(p.x + 6, p.y + 36, 6, 10, c(0.3f, 0.25f, 0.2f));
            g.rect(p.x + 58, p.y + 36, 6, 10, c(0.3f, 0.25f, 0.2f));
        }
        g.rect(490, 318, 30, 16, c(0.2f, 0.2f, 0.22f));
    }

    private static void lobby(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 960, 480, PartyGfx.BLACK);
        w.wall(r(0, 60, 960, 140), c(0.78f, 0.8f, 0.82f), c(0.72f, 0.74f, 0.76f));
        w.tiles(r(0, 200, 960, 240), c(0.62f, 0.64f, 0.66f), c(0.58f, 0.6f, 0.62f), 48f);
        g.rect(450, 440, 60, 20, c(0.62f, 0.64f, 0.66f));
        w.door(r(440, 100, 80, 100), c(0.5f, 0.55f, 0.6f));
        w.text("ОПЕНСПЕЙС", 480, 94, 12, c(0.3f, 0.3f, 0.35f));
        g.rect(620, 90, 200, 50, c(0.1f, 0.1f, 0.12f));
        w.text("ОБСЛУЖИВАЕТСЯ: 7", 720, 112, 14, c(1f, 0.4f, 0.3f));
        w.text("ВАШ НОМЕР: 4815162342", 720, 132, 11, c(0.8f, 0.8f, 0.8f));
        w.bot(v(220, 222), "ЖДИТЕ");
        g.rect(140, 226, 160, 40, c(0.85f, 0.85f, 0.88f));
        g.rect(140, 226, 160, 6, c(0.4f, 0.42f, 0.48f));
        for (int k = 0; k < 6; k++) {
            g.rect(560 + k * 50, 316, 36, 14, c(0.3f, 0.35f, 0.55f));
            g.rect(560 + k * 50, 300, 36, 16, c(0.25f, 0.3f, 0.48f));
        }
        g.rect(864, 150, 32, 50, c(0.85f, 0.87f, 0.9f));
        g.rect(868, 120, 24, 30, c(0.55f, 0.75f, 0.95f));
    }

    private static void support(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 1280, 480, PartyGfx.BLACK);
        w.wall(r(0, 60, 1280, 140), c(0.7f, 0.72f, 0.68f), c(0.65f, 0.67f, 0.63f));
        w.tiles(r(0, 200, 1280, 220), c(0.4f, 0.44f, 0.5f), c(0.37f, 0.4f, 0.46f), 44f);
        w.door(r(1200, 100, 60, 100), c(0.35f, 0.25f, 0.2f));
        w.text("МОДЕРАТОР", 1230, 94, 12, c(0.3f, 0.2f, 0.15f));
        String[] plates = {"SLA: 999 дней", "МЫ ЦЕНИМ ВАС", "НЕ ШУМЕТЬ", "ПЯТНИЦА: ДЕПЛОЙ ЗАПРЕЩЁН", "УЛЫБАЙТЕСЬ"};
        for (int k = 0; k < 5; k++) w.plate(r(80 + k * 220, 100, 140, 26), plates[k], 11);
        for (float y : new float[]{250f, 340f}) {
            for (float x : new float[]{200f, 420f, 640f, 860f}) w.desk(r(x, y, 90, 30), true, ((x + y) % 3f) < 2f);
        }
        w.desk(r(1040, 250, 100, 30), true, true);
        w.text("ДЕЛА", 1090, 214, 11, c(0.2f, 0.2f, 0.2f));
    }

    private static void office(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        w.wall(r(60, 50, 520, 150), c(0.35f, 0.22f, 0.2f), c(0.4f, 0.26f, 0.23f));
        w.planks(r(60, 200, 520, 240), c(0.3f, 0.2f, 0.14f), c(0.25f, 0.16f, 0.11f));
        g.rect(290, 440, 60, 20, c(0.3f, 0.2f, 0.14f));
        w.door(r(580, 290, 20, 70), c(0.5f, 0.5f, 0.55f));
        g.rect(60, 70, 140, 116, c(0.4f, 0.28f, 0.18f));
        int[] cols = {c(0.7f, 0.2f, 0.2f), c(0.2f, 0.3f, 0.6f), c(0.85f, 0.75f, 0.3f)};
        for (int k = 0; k < 12; k++) g.rect(66 + (k % 6) * 22, 78 + (k / 6) * 54, 18, 46, cols[k % 3]);
        w.plate(r(400, 90, 160, 50), "ПРАВИЛА ЕСТЬ ПРАВИЛА", 11);
        g.rect(220, 230, 200, 40, c(0.45f, 0.3f, 0.2f));
        g.rect(220, 230, 200, 6, c(0.55f, 0.38f, 0.26f));
        g.rect(380, 214, 26, 18, c(0.85f, 0.2f, 0.2f));
    }

    private static void gate(PartyWorld w) {
        PartyGfx g = w.g;
        float t = w.t;
        g.rect(0, 0, 1280, 230, c(0.04f, 0.05f, 0.12f));
        for (int k = 0; k < 40; k++) {
            g.rect((k * 131f) % 1280f, (k * 37f) % 120f + 4, 2, 2, PartyGfx.rgba(1, 1, 1, 0.5f + 0.5f * (float) Math.sin(t * 2f + k)));
        }
        g.circle(180, 50, 22, c(0.95f, 0.95f, 0.85f));
        for (float fx = 0f; fx < 900f; fx += 18f) g.rect(fx, 150, 4, 80, c(0.4f, 0.42f, 0.45f));
        g.rect(0, 160, 900, 4, c(0.4f, 0.42f, 0.45f));
        g.rect(900, 40, 380, 190, c(0.16f, 0.18f, 0.2f));
        for (int k = 0; k < 8; k++) g.rect(920 + k * 44, 70, 30, 6, ((t + k * 0.3f) % 1f) < 0.5f ? GREEN : PartyGfx.darkened(GREEN, 0.7f));
        w.text("ДАТА-ЦЕНТР", 1090, 60, 16, c(0.8f, 0.8f, 0.8f));
        w.door(r(1160, 150, 60, 80), c(0.3f, 0.32f, 0.35f));
        w.tiles(r(0, 230, 1280, 170), c(0.2f, 0.21f, 0.24f), c(0.18f, 0.19f, 0.22f), 40f);
        g.rect(0, 400, 1280, 80, c(0.05f, 0.05f, 0.06f));
        g.rect(560, 200, 70, 92, c(0.3f, 0.35f, 0.5f));
        g.rect(570, 214, 50, 30, c(1f, 0.9f, 0.6f));
        if (w.game.flag("gate_open")) {
            g.rect(706, 140, 10, 92, c(0.9f, 0.2f, 0.2f));
        } else {
            g.rect(700, 230, 16, 170, c(0.9f, 0.85f, 0.2f));
            for (int k = 0; k < 6; k++) g.rect(700, 240 + k * 28, 16, 12, c(0.9f, 0.2f, 0.2f));
        }
    }

    private static void corridor(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 1280, 480, PartyGfx.BLACK);
        w.wall(r(0, 90, 1280, 130), c(0.13f, 0.14f, 0.18f), c(0.17f, 0.18f, 0.23f));
        w.tiles(r(0, 220, 1280, 140), c(0.18f, 0.19f, 0.24f), c(0.15f, 0.16f, 0.2f));
        g.rect(0, 360, 1280, 12, c(0.1f, 0.1f, 0.13f));
        for (int k = 0; k < 9; k++) w.rack(v(620f + k * 46f, 100));
        for (float x : new float[]{40f, 380f}) {
            w.rack(v(x, 100));
            w.rack(v(x + 46, 100));
        }
        w.plate(r(140, 150, 70, 40), "SSH", 16);
        w.bot(v(522, 250), "LGTM");
    }

    private static void cooling(PartyWorld w) {
        PartyGfx g = w.g;
        float t = w.t;
        PartyGame game = w.game;
        g.rect(0, 0, 960, 480, PartyGfx.BLACK);
        w.wall(r(0, 80, 960, 140), c(0.16f, 0.24f, 0.32f), c(0.2f, 0.3f, 0.4f));
        w.tiles(r(0, 220, 960, 160), c(0.7f, 0.8f, 0.88f), c(0.62f, 0.72f, 0.82f), 40f);
        for (int k = 0; k < 2; k++) {
            PartyVec cc = v(720 + k * 130, 150);
            g.circle(cc, 46, c(0.25f, 0.3f, 0.36f));
            float spin = t * (game.flag("cool") ? 6f : 14f);
            for (int b = 0; b < 4; b++) {
                float a = spin + b * (float) Math.PI * 0.5f;
                PartyVec p1 = cc.add(PartyVec.angle(a - 0.25f).mul(40f)), p2 = cc.add(PartyVec.angle(a + 0.25f).mul(40f));
                g.poly(c(0.6f, 0.7f, 0.8f), cc.x, cc.y, p1.x, p1.y, p2.x, p2.y);
            }
        }
        int temp = PartyStory.temperature(game);
        String[] names = {"A", "B", "C"};
        for (int k = 0; k < 3; k++) {
            boolean on = game.flag("valve_" + names[k].toLowerCase());
            float vx = 290f + k * 80f;
            g.rect(vx + 13, 150, 4, 60, c(0.4f, 0.45f, 0.5f));
            g.circle(vx + 15, 150, 14, on ? c(0.3f, 0.8f, 0.5f) : c(0.8f, 0.3f, 0.3f));
            w.text(names[k], vx + 15, 156, 14, PartyGfx.WHITE);
        }
        g.rect(560, 120, 100, 60, c(0.05f, 0.05f, 0.06f));
        w.text(temp + " °C", 610, 160, 22, temp == 18 ? MINT : c(1f, 0.35f, 0.3f));
        w.plate(r(110, 150, 70, 40), "ЗАПИСКА", 11);
        w.door(r(930, 260, 30, 90), c(0.5f, 0.55f, 0.6f), game.flag("cool"));
        for (int k = 0; k < 10; k++) {
            float px = (k * 97f + t * 20f) % 960f;
            g.circle(px, 230 + (k * 13f) % 40f, 10, PartyGfx.rgba(1, 1, 1, 0.05f));
        }
    }

    private static void legacy(PartyWorld w) {
        PartyGfx g = w.g;
        float t = w.t;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        w.wall(r(40, 40, 560, 110), c(0.2f, 0.19f, 0.14f), c(0.25f, 0.23f, 0.17f));
        w.tiles(r(40, 150, 560, 290), c(0.24f, 0.22f, 0.17f), c(0.21f, 0.19f, 0.15f));
        g.rect(0, 290, 40, 90, c(0.24f, 0.22f, 0.17f));
        for (int k = 0; k < 6; k++) {
            float x = 62f + k * 19f;
            g.rect(x, 70 + (k % 2) * 10, 16, 22, c(0.9f, 0.88f, 0.8f));
            for (int l = 0; l < 4; l++) g.line(x + 2, 74 + (k % 2) * 10 + l * 4, x + 12, 74 + (k % 2) * 10 + l * 4, c(0.4f, 0.4f, 0.4f), 1f);
        }
        boolean open = w.game.flag("door_open");
        g.rect(280, 60, 80, 92, open ? c(0.05f, 0.05f, 0.05f) : c(0.35f, 0.25f, 0.15f));
        if (!open) g.rect(342, 106, 6, 6, c(0.9f, 0.8f, 0.3f));
        else g.rect(290, 130, 60, 22, c(0.24f, 0.22f, 0.17f));
        w.plate(r(480, 70, 100, 40), "ЛЕГАСИ", 14);
        g.rect(410, 150, 70, 40, c(0.3f, 0.3f, 0.33f));
        g.rect(416, 154, 58, 28, c(0.02f, 0.1f, 0.04f));
        w.text((t % 1f) < 0.5f ? "?_" : "?", 445, 174, 16, GREEN);
        g.rect(100, 180, 40, 40, c(0.35f, 0.38f, 0.4f));
        g.rect(96, 176, 48, 8, c(0.45f, 0.48f, 0.5f));
    }

    private static void secret(PartyWorld w) {
        PartyGfx g = w.g;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        w.wall(r(200, 90, 240, 140), c(0.16f, 0.08f, 0.1f), c(0.2f, 0.1f, 0.13f));
        w.tiles(r(200, 230, 240, 170), c(0.2f, 0.12f, 0.14f), c(0.17f, 0.1f, 0.12f), 30f);
        g.rect(440, 310, 40, 50, c(0.2f, 0.12f, 0.14f));
        Bitmap ct = w.tex("charlie");
        if (ct != null) {
            float sw = ct.getWidth() * 0.72f, sh = ct.getHeight() * 0.72f;
            float px = 320 - sw * 0.5f, py = 110;
            g.rect(px - 8, py - 8, sw + 16, sh + 16, c(0.85f, 0.66f, 0.25f));
            g.frame(px - 8, py - 8, sw + 16, sh + 16, c(0.55f, 0.4f, 0.12f), 3f);
            g.tex(ct, px, py, sw, sh);
        }
        g.rect(290, 200, 60, 14, c(0.75f, 0.7f, 0.55f));
        w.text("№1", 320, 212, 12, c(0.3f, 0.15f, 0.1f));
        g.poly(PartyGfx.rgba(1, 0.95f, 0.7f, 0.05f), 310, 90, 330, 90, 420, 230, 220, 230);
    }

    private static void hall(PartyWorld w) {
        PartyGfx g = w.g;
        float t = w.t;
        g.rect(0, 0, 1600, 480, c(0.01f, 0.02f, 0.02f));
        w.wall(r(0, 90, 1600, 130), c(0.04f, 0.06f, 0.05f), c(0.05f, 0.09f, 0.07f));
        w.tiles(r(0, 220, 1600, 140), c(0.06f, 0.08f, 0.07f), c(0.04f, 0.06f, 0.05f), 60f);
        for (int k = 0; k < 7; k++) {
            float x = 180f + k * 200f;
            g.rect(x + 22, 130, 16, 90, c(0.15f, 0.16f, 0.18f));
            g.rect(x, 96, 60, 44, c(0.2f, 0.21f, 0.24f));
            g.rect(x + 4, 100, 52, 34, c(0.02f, 0.08f, 0.04f));
            for (int l = 0; l < 3; l++) {
                float ww = 10f + (k * 13f + l * 7f) % 30f;
                g.rect(x + 8, 106 + l * 9, ww, 3, PartyGfx.darkened(GREEN, 0.3f));
            }
        }
        for (int k = 0; k < 40; k++) {
            float x = (k * 97f) % 1600f;
            float y = 90f + (t * (20f + (k % 5) * 6f) + k * 31f) % 130f;
            w.text(k % 2 == 0 ? "0" : "1", x, y, 12, PartyGfx.alpha(GREEN, 0.25f));
        }
        if (!w.game.flag("boss_done")) {
            PartyVec b = v(1475, 236);
            float bob = (float) Math.sin(t * 2f) * 2f;
            g.tex(w.tex("boss_body"), b.x - 60, b.y - 100 + bob, 120, 110);
            g.tex(w.tex("boss_arm_l"), b.x - 52, b.y - 70 + bob, 40, 60);
            g.tex(w.tex("boss_arm_r"), b.x + 12, b.y - 70 + bob, 40, 60);
            g.tex(w.tex("boss_keyboard"), b.x - 40, b.y - 14 + bob, 80, 22);
            g.tex(w.tex("boss_head"), b.x - 28, b.y - 118 + bob * 1.5f, 56, 46);
            w.text((t % 4f) > 0.2f ? "-_-" : "o_o", b.x, b.y - 92 + bob * 1.5f, 14, GREEN);
            // the stolen cake beside him, untouched
            PartyDeco.cake(g, 1395, 268, 1, false, t);
        }
    }

    /** Small things on top of the rooms: plants, rugs, posters, light, and the birthday. */
    private static void deco(PartyWorld w, String room) {
        PartyGfx g = w.g;
        float t = w.t;
        switch (room) {
            case "bedroom":
                w.shelf(r(190, 60, 40, 84));
                w.poster(r(350, 64, 50, 64), c(0.2f, 0.2f, 0.35f), "SPACE");
                w.plant(v(585, 250), false);
                w.chair(v(505, 236), c(0.25f, 0.25f, 0.3f));
                g.rect(460, 380, 50, 18, PartyGfx.darkened(MINT, 0.3f));
                g.rect(470, 374, 30, 10, c(0.3f, 0.3f, 0.4f));
                w.lightPool(v(505, 200), 70, c(1f, 0.3f, 0.3f));
                PartyDeco.garland(g, 44, 596, 44, 24);
                for (int k = 0; k < 4; k++) PartyDeco.balloon(g, 530 + k * 16, 360 - (k % 2) * 14, k, t, 36, 2);
                break;
            case "flat":
                for (int k = 0; k < 14; k++) g.rect(270 + k * 50, 60, 4, 140, c(0.34f, 0.31f, 0.25f));
                w.rug(r(480, 260, 220, 90), c(0.45f, 0.25f, 0.3f), c(0.8f, 0.7f, 0.5f));
                w.plant(v(250, 214), true);
                w.plant(v(940, 214), false);
                w.window(r(30, 90, 22, 70), true);
                g.rect(680, 90, 50, 8, c(0.4f, 0.3f, 0.2f));
                g.rect(688, 98, 22, 50, PartyGfx.darkened(MINT, 0.15f));
                g.rect(712, 98, 14, 40, c(0.25f, 0.25f, 0.3f));
                g.circle(355, 304, 6, c(0.95f, 0.95f, 0.95f));
                w.poster(r(420, 80, 70, 54), c(0.95f, 0.85f, 0.55f), "СДЕЛАЙ САМ");
                w.chair(v(320, 286));
                w.chair(v(390, 286));
                PartyDeco.garland(g, 270, 840, 66, 30);
                PartyDeco.banner(g, 490, 172, "С ДНЁМ РОЖДЕНИЯ", 12);
                break;
            case "party":
                for (int k = 0; k < 14; k++) g.rect(270 + k * 50, 60, 4, 140, c(0.38f, 0.3f, 0.34f));
                w.plant(v(250, 214), true);
                w.plant(v(940, 214), false);
                w.window(r(30, 90, 22, 70), false);
                PartyDeco.garland(g, 0, 480, 66, 34);
                PartyDeco.garland(g, 480, 960, 66, 34);
                PartyDeco.banner(g, 490, 120, "С ДНЁМ РОЖДЕНИЯ, НАРЕЗАННЫЙ!", 15);
                for (int k = 0; k < 10; k++) {
                    float bx = 230 + k * 64 + (k % 2) * 10;
                    PartyDeco.balloon(g, bx, 166 - (k % 3) * 10, k, t, 30, 2);
                }
                // a disco light swinging over the table
                for (int k = 0; k < 3; k++) {
                    float sx = 480 + (float) Math.sin(t * 1.3f + k * 2.1f) * 220;
                    w.lightPool(v(sx, 330), 60, PartyDeco.COLOURS[(k * 2 + (int) (t * 0.5f)) % 6]);
                }
                break;
            case "cafe":
                for (float x : new float[]{120f, 320f, 520f}) {
                    g.line(x, 0, x, 40, c(0.2f, 0.2f, 0.2f), 2f);
                    g.circle(x, 46, 10, c(1f, 0.85f, 0.5f));
                    w.lightPool(v(x, 280), 70, c(1f, 0.85f, 0.5f));
                }
                w.plant(v(60, 214), true);
                w.plant(v(580, 214), true);
                g.rect(216, 170, 34, 28, c(0.3f, 0.3f, 0.32f));
                g.rect(222, 176, 10, 8, (t % 1f) < 0.5f ? c(1f, 0.4f, 0.3f) : c(0.4f, 0.1f, 0.1f));
                for (int k = 0; k < 4; k++) g.rect(380 + k * 12, 184, 8, 12, c(0.95f, 0.95f, 0.95f));
                for (PartyVec p : new PartyVec[]{v(80, 350), v(170, 350), v(460, 380), v(550, 380)}) w.chair(p, c(0.35f, 0.55f, 0.48f));
                w.poster(r(70, 80, 60, 50), c(0.95f, 0.9f, 0.8f), "КОФЕ");
                w.poster(r(480, 80, 80, 50), c(0.3f, 0.25f, 0.2f), "WI-FI: margy");
                PartyDeco.garland(g, 44, 596, 54, 20);
                break;
            case "lobby":
                w.plant(v(40, 214), true);
                w.plant(v(920, 330), true);
                w.poster(r(40, 90, 70, 60), c(0.3f, 0.5f, 0.8f), "МЫ РЯДОМ");
                w.poster(r(320, 90, 90, 60), c(0.9f, 0.85f, 0.5f), "ВАШ ЗВОНОК ВАЖЕН");
                for (int k = 0; k < 4; k++) {
                    float px = 560f + k * 90f;
                    g.rect(px, 280, 6, 22, c(0.75f, 0.7f, 0.4f));
                    if (k < 3) g.line(px + 3, 284, px + 93, 284, c(0.6f, 0.15f, 0.2f), 3f);
                }
                w.rug(r(400, 380, 160, 50), c(0.55f, 0.15f, 0.2f), c(0.85f, 0.7f, 0.4f));
                break;
            case "support":
                for (float x : new float[]{300f, 520f, 740f, 960f}) {
                    g.rect(x, 236, 6, 44, c(0.55f, 0.6f, 0.65f));
                    g.rect(x, 326, 6, 44, c(0.55f, 0.6f, 0.65f));
                }
                w.plant(v(160, 214), true);
                w.plant(v(1180, 400), true);
                g.rect(1100, 330, 50, 34, c(0.85f, 0.85f, 0.88f));
                g.rect(1106, 322, 38, 10, c(0.7f, 0.7f, 0.72f));
                g.rect(1110, 350, 30, 4, c(0.95f, 0.95f, 0.95f));
                g.rect(40, 80, 30, 90, c(0.92f, 0.92f, 0.92f));
                w.cables(408, 0, 1280);
                break;
            case "office":
                w.rug(r(180, 300, 280, 120), c(0.4f, 0.12f, 0.15f), c(0.75f, 0.6f, 0.3f));
                w.plant(v(560, 230), true);
                g.circle(320, 80, 18, c(0.95f, 0.95f, 0.9f));
                g.line(320, 80, 320, 68, PartyGfx.BLACK, 2f);
                g.line(320, 80, 320 + (float) Math.sin(t) * 10, 80 - (float) Math.cos(t) * 10, PartyGfx.BLACK, 2f);
                g.rect(250, 110, 4, 80, c(0.5f, 0.45f, 0.4f));
                g.rect(254, 110, 40, 26, c(0.85f, 0.2f, 0.25f));
                w.text("BAN", 274, 128, 11, PartyGfx.WHITE);
                break;
            case "dc_gate":
                for (PartyVec p : new PartyVec[]{v(120, 236), v(420, 236)}) w.tree(p, 1.1f);
                w.lightPool(v(800, 300), 90, c(1f, 0.9f, 0.6f));
                g.rect(796, 150, 6, 90, c(0.25f, 0.25f, 0.28f));
                g.rect(786, 146, 26, 8, c(1f, 0.9f, 0.6f));
                g.rect(880, 120, 14, 10, c(0.3f, 0.3f, 0.32f));
                g.rect(888, 124, 4, 3, (t % 1f) < 0.5f ? c(1f, 0.2f, 0.2f) : c(0.3f, 0.05f, 0.05f));
                w.bin(v(470, 262));
                break;
            case "corridor":
                w.cables(372, 0, 1280);
                w.pipeH(92, 0, 1280, c(0.35f, 0.38f, 0.42f));
                break;
            case "cooling":
                w.pipeH(86, 0, 960, c(0.5f, 0.6f, 0.7f));
                w.pipeH(104, 0, 960, c(0.4f, 0.5f, 0.62f));
                for (int k = 0; k < 6; k++) g.rect(40f + k * 150f, 374, 60, 6, PartyGfx.rgba(0.85f, 0.95f, 1f, 0.5f));
                break;
            case "legacy":
                w.cables(430, 40, 600);
                w.shelf(r(200, 60, 60, 84));
                g.rect(540, 380, 40, 30, c(0.85f, 0.8f, 0.7f));
                g.rect(546, 372, 30, 10, c(0.9f, 0.86f, 0.75f));
                break;
            case "hall":
                for (int k = 0; k < 6; k++) w.lightPool(v(210 + k * 200, 250), 50, GREEN);
                break;
        }
    }
}

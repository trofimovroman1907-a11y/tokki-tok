package cat.narezany.margyt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The simple screens: title, the story before, losing, the endings, the birthday finale and the credits. */
final class PartyScreens implements PartyGame.Scene {
    private static final int MINT = PartyGame.MINT, GREEN = PartyGame.GREEN, GOLD = PartyGame.GOLD;

    private static final String[][] INTRO = {
            {"24 ноября. У Нарезанного день рождения.", "cake"},
            {"Вечером придут гости. Шарики надуты, торт заказан.", "balloons"},
            {"Осталось попросить Claude доделать праздничную игру для подписчиков. И можно отдыхать.", "narez"},
            {"Но утром пришло письмо счастья.", "error"},
            {"Нарезанного забанили. Прямо в день рождения. А где-то на окраине, в дата-центре, кто-то печатал. Сам. Руками.", "boss"},
    };
    private static final String[][] SPARE = {
            {"Селфкодинг садится рядом. Вы долго молчите.", "both"},
            {"Потом он открывает редактор. Ты открываешь чат с Claude.", "both"},
            {"Он пишет функцию. Claude предлагает тест. Ты делаешь ревью.", "both"},
            {"Код собирается с первого раза. Почти.", "both"},
            {"Модератор снимает бан. За окном уже вечер.", "both"},
            {"«Слушай, — говоришь ты. — У меня сегодня день рождения. Приходи»", "cake"},
            {"Селфкодинг долго молчит. Потом кивает. Его сорок лет никуда не звали.", "both"},
    };
    private static final String[][] KILL = {
            {"Селфкодинг рассыпается на строчки кода.", "dead"},
            {"Строчки гаснут одна за другой.", "dead"},
            {"Аккаунт разблокировали. Claude снова отвечает. Быстро, вежливо, правильно.", "error_ok"},
            {"За окном вечер. Дома ждут гости и торт.", "cake"},
            {"Праздник будет. Только руками больше никто не пишет. Даже ты.", "narez"},
    };

    final PartyGame game;
    final String mode0;
    String mode;
    final Map<String, Object> data;
    private float t;
    private int sel;
    private int slide;
    private float shown;
    private String[][] slides = new String[0][];
    private final List<float[]> shards = new ArrayList<>();
    private final PartyDeco.Confetti confetti = new PartyDeco.Confetti(640, 480, 6f);
    /** The finale: what the server said about the hat, or null while it is asked. */
    private String badge;
    private boolean asked;

    PartyScreens(PartyGame game, String mode, Map<String, Object> data) {
        this.game = game;
        this.mode = mode;
        this.mode0 = mode;
        this.data = data;
        switch (mode) {
            case "title":
                game.playMusic("home");
                break;
            case "intro":
                slides = INTRO;
                if (Party.expired()) {
                    // after the invitation ran out the party is still there; the hat is not
                    String[][] late = new String[INTRO.length + 1][];
                    late[0] = new String[]{"Говорят, раньше за это выдавали значок. Теперь нет. Но торт всё ещё есть.", "cake"};
                    System.arraycopy(INTRO, 0, late, 1, INTRO.length);
                    slides = late;
                }
                game.playMusic("hall");
                break;
            case "gameover":
                game.playMusic("");
                game.sfx.play("hurt");
                break;
            case "ending":
                slides = "spare".equals(data.get("kind")) ? SPARE : KILL;
                game.playMusic("spare".equals(data.get("kind")) ? "end" : "hall");
                break;
            case "finale":
                game.playMusic("party");
                confetti.rate = 30f;
                confetti.burst(320, 200, 120);
                game.sfx.play("tada");
                break;
        }
    }

    private List<String> titleOpts() {
        List<String> o = new ArrayList<>();
        if (game.hasSave()) o.add("Продолжить");
        o.add("Новая игра");
        o.add("Выйти");
        return o;
    }

    @Override
    public void update(float dt) {
        t += dt;
        PartyInput in = game.input;
        confetti.update(dt);
        switch (mode) {
            case "title": {
                List<String> opts = titleOpts();
                if (in.justPressed(PartyInput.UP)) {
                    sel = (sel - 1 + opts.size()) % opts.size();
                    game.sfx.play("move");
                }
                if (in.justPressed(PartyInput.DOWN)) {
                    sel = (sel + 1) % opts.size();
                    game.sfx.play("move");
                }
                if (in.justPressed(PartyInput.OK) && t > 0.5f) {
                    game.sfx.play("ok");
                    String o = opts.get(sel);
                    if (o.equals("Продолжить")) game.loadGame();
                    else if (o.equals("Новая игра")) game.newGame();
                    else game.host.close();
                }
                break;
            }
            case "intro":
            case "ending":
                slides(dt);
                break;
            case "finale":
                if (!asked && t > 1.2f) {
                    asked = true;
                    if (Party.expired()) badge = "Значок за праздник больше не выдают. Но торт всё равно вкусный.";
                    else game.host.claimBadge(said -> badge = said);
                }
                if (in.justPressed(PartyInput.OK) && t > 3f && badge != null) {
                    game.sfx.play("ok");
                    mode = "credits";
                    t = 0f;
                }
                break;
            case "credits":
                if (in.justPressed(PartyInput.OK) && t > 2f) {
                    game.showScreen("title", null);
                }
                break;
            case "gameover":
                if (t > 0.8f && t - dt <= 0.8f) game.sfx.play("crack");
                if (t > 1.6f && shards.isEmpty()) {
                    game.sfx.play("shatter");
                    for (int k = 0; k < 8; k++) shards.add(new float[]{320, 220, game.randf(-160, 160), game.randf(-260, -60)});
                }
                for (float[] s : shards) {
                    s[3] += 500f * dt;
                    s[0] += s[2] * dt;
                    s[1] += s[3] * dt;
                }
                if (t > 3f) shown += 30f * dt;
                if (in.justPressed(PartyInput.OK) && t > 3.5f) {
                    game.sfx.play("ok");
                    if (game.hasSave()) game.loadGame();
                    else game.newGame();
                }
                break;
        }
    }

    private void slides(float dt) {
        PartyInput in = game.input;
        String txt = slides[slide][0];
        int before = (int) shown;
        shown = Math.min(shown + 26f * dt, txt.length());
        if ((int) shown != before && (int) shown % 2 == 0) game.sfx.play("blip_sys");
        if (in.justPressed(PartyInput.OK) || in.justPressed(PartyInput.CANCEL)) {
            if (shown < txt.length()) {
                shown = txt.length();
            } else {
                slide++;
                shown = 0f;
                t = 0f;
                if (slide >= slides.length) {
                    if (mode.equals("intro")) game.startWorld();
                    else PartyStory.toParty(game);
                }
            }
        }
    }

    @Override
    public void draw(PartyGfx g) {
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        switch (mode) {
            case "title": {
                for (int k = 0; k < 6; k++) {
                    PartyDeco.balloon(g, 30 + k * 18 + (k % 2) * 6, 70 + (k % 3) * 14 + (float) Math.sin(t + k) * 4, k, t, 60, 2);
                    PartyDeco.balloon(g, 610 - k * 18 - (k % 2) * 6, 70 + (k % 3) * 14 + (float) Math.cos(t + k) * 4, k + 3, t, 60, 2);
                }
                confetti.draw(g);
                boolean glitch = Math.sin(t * 23f) > 0.93f;
                g.text("НАРЕЗАННЫЙ", 320, 110, 52, MINT, true);
                g.text("против", 320, 152, 22, PartyGfx.rgb(0.7f, 0.7f, 0.7f), true);
                float off = glitch ? game.randf(-4, 4) : 0f;
                if (glitch) g.text("СЕЛФКОДИНГА", 320 + off + 3, 208, 52, PartyGfx.rgba(1f, 0.2f, 0.3f, 0.7f), true);
                g.text("СЕЛФКОДИНГА", 320 + off, 208, 52, GREEN, true);
                g.text("★ ДЕНЬ РОЖДЕНИЯ ★", 320, 246, 22, GOLD, true);
                g.tex(game.tex("narez_right_0"), 60, 300, 72, 96);
                PartyDeco.hatOn(g, game.tex("narez_right_0"), 60, 300, 3, 0);
                g.tex(game.tex("boss_head"), 470, 300, 112, 92);
                g.text((t % 1f) < 0.5f ? "_" : "", 526, 352, 28, GREEN, true);
                List<String> opts = titleOpts();
                for (int k = 0; k < opts.size(); k++) {
                    float y = 312f + k * 36f;
                    g.text(opts.get(k), 320, y, 26, k == sel ? MINT : PartyGfx.WHITE, true);
                    if (k == sel) g.tex(game.tex("soul"), 320 - g.width(opts.get(k), 26) * 0.5f - 30, y - 18, 16, 16);
                }
                g.text("Z выбрать   X назад   стрелки ходить", 320, 462, 14, PartyGfx.rgb(0.5f, 0.5f, 0.5f), true);
                break;
            }
            case "intro":
            case "ending":
                if (slide < slides.length) {
                    picture(g, slides[slide][1]);
                    String txt = slides[slide][0];
                    float y = 336f;
                    for (String ln : g.wrap(txt.substring(0, Math.min((int) shown, txt.length())), 540, 24)) {
                        g.text(ln, 50, y, 24, PartyGfx.WHITE, false);
                        y += 34f;
                    }
                }
                break;
            case "finale": {
                confetti.draw(g);
                PartyDeco.cake(g, 320, 250, 4, false, t);
                g.text("С ДНЁМ РОЖДЕНИЯ,", 320, 300, 34, GOLD, true);
                g.text("НАРЕЗАННЫЙ!", 320, 342, 44, MINT, true);
                if (badge == null) {
                    g.text(t > 1.2f ? "Вручаем праздничный колпак..." : "", 320, 400, 18, PartyGfx.rgb(0.75f, 0.75f, 0.75f), true);
                } else {
                    float y = 392;
                    for (String ln : g.wrap(badge, 560, 18)) {
                        g.text(ln, 320, y, 18, PartyGfx.WHITE, true);
                        y += 24;
                    }
                }
                if (t > 3f && badge != null) g.text("Z", 320, 466, 18, PartyGfx.rgb(0.5f, 0.5f, 0.5f), true);
                break;
            }
            case "credits": {
                boolean spare = "spare".equals(game.flags.get("ending"));
                g.text("КОНЕЦ", 320, 130, 52, spare ? MINT : PartyGfx.rgb(0.6f, 0.6f, 0.6f), true);
                g.text(spare ? "праздничный" : "тихий", 320, 170, 22, PartyGfx.rgb(0.7f, 0.7f, 0.7f), true);
                g.text("НАРЕЗАННЫЙ ПРОТИВ СЕЛФКОДИНГА", 320, 240, 22, PartyGfx.WHITE, true);
                g.text("издание ко дню рождения, 24.11", 320, 270, 16, GOLD, true);
                g.text("сделал Нарезанный, спасибо, что пришёл", 320, 310, 18, PartyGfx.rgb(0.7f, 0.7f, 0.7f), true);
                g.text("MargyT 2.3.1 Birthday Bash", 320, 340, 16, PartyGfx.rgb(0.55f, 0.55f, 0.55f), true);
                if (t > 2f) g.text("Z", 320, 440, 18, PartyGfx.rgb(0.5f, 0.5f, 0.5f), true);
                break;
            }
            case "gameover": {
                android.graphics.Bitmap soul = game.tex("soul");
                if (t < 0.8f) {
                    g.tex(soul, 304, 204, 32, 32);
                } else if (t < 1.6f) {
                    g.texRegion(soul, 298, 204, 16, 32, 0, 0, 8, 16);
                    g.texRegion(soul, 322, 204, 16, 32, 8, 0, 8, 16);
                    g.line(320, 204, 316, 236, PartyGfx.BLACK, 3f);
                }
                for (float[] s : shards) g.rect(s[0], s[1], 6, 6, MINT);
                if (t > 3f) {
                    String msg = "Сборка упала.\nНарезанный, не сдавайся. Гости ждут.";
                    float y = 330f;
                    for (String ln : g.wrap(msg.substring(0, Math.min((int) shown, msg.length())), 540, 24)) {
                        g.text(ln, 320, y, 24, PartyGfx.WHITE, true);
                        y += 34f;
                    }
                }
                break;
            }
        }
    }

    private void picture(PartyGfx g, String id) {
        PartyRect r = new PartyRect(170, 40, 300, 230);
        g.rect(r, PartyGfx.rgb(0.06f, 0.06f, 0.08f));
        g.frame(r, PartyGfx.WHITE, 3f);
        PartyVec c = r.center();
        switch (id) {
            case "cake":
                PartyDeco.cake(g, c.x, c.y + 70, 4, true, t);
                break;
            case "balloons":
                for (int k = 0; k < 7; k++) {
                    PartyDeco.balloon(g, c.x - 90 + k * 30, c.y + 10 - (k % 2) * 24, k, t, 80, 2);
                }
                PartyDeco.garland(g, r.x + 10, r.ex() - 10, r.y + 16, 26);
                break;
            case "keyboard":
                g.tex(game.tex("boss_keyboard"), c.x - 120, c.y - 33, 240, 66);
                break;
            case "narez":
                g.tex(game.tex("narez_down_0"), c.x - 48, c.y - 64, 96, 128);
                PartyDeco.hatOn(g, game.tex("narez_down_0"), c.x - 48, c.y - 64, 4, 0);
                break;
            case "error":
                g.text("✉", c.x, c.y - 40, 40, PartyGfx.rgb(0.9f, 0.9f, 0.9f), true);
                g.text("Your account has", c.x, c.y + 10, 22, PartyGfx.rgb(1f, 0.45f, 0.45f), true);
                g.text("been suspended.", c.x, c.y + 38, 22, PartyGfx.rgb(1f, 0.45f, 0.45f), true);
                break;
            case "error_ok":
                g.text("Account restored", c.x, c.y + 10, 26, MINT, true);
                break;
            case "boss":
                g.tex(game.tex("boss_head"), c.x - 84, c.y - 69, 168, 138);
                g.text((t % 0.8f) < 0.4f ? "_" : "", c.x, c.y, 30, GREEN, true);
                break;
            case "both":
                g.tex(game.tex("narez_right_0"), c.x - 120, c.y - 50, 72, 96);
                g.tex(game.tex("boss_head"), c.x + 10, c.y - 46, 112, 92);
                g.text("^_^", c.x + 66, c.y - 2, 22, GREEN, true);
                break;
            case "dead": {
                float a = Math.max(0f, Math.min(1f, 1f - t / 3f));
                g.tex(game.tex("boss_head"), c.x - 84, c.y - 69, 168, 138, PartyGfx.alpha(PartyGfx.WHITE, a));
                for (int k = 0; k < 20; k++) {
                    float x = c.x - 120 + (k * 37f) % 240f;
                    float y = c.y - 80 + (t * 60f + k * 23f) % 180f;
                    g.text(k % 2 == 0 ? "0" : "1", x, y, 16, PartyGfx.alpha(GREEN, a * 0.8f), true);
                }
                break;
            }
        }
    }
}

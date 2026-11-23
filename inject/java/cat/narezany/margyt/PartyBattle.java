package cat.narezany.margyt;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A fight: the player's turn (КОД, ПРОМПТ, ВЕЩИ, ПОЩАДА), then the enemy talks and attacks.
 * An attack happens in a box: the paw soul dodges symbols of code.
 */
final class PartyBattle implements PartyGame.Scene {
    private static final int MINT = PartyGame.MINT, GREEN = PartyGame.GREEN;
    private static final int ORANGE = PartyGfx.rgb(1f, 0.67f, 0.24f);
    private static final PartyRect MENU_BOX = new PartyRect(32, 250, 576, 140);
    private static final String[] BUTTONS = {"КОД", "ПРОМПТ", "ВЕЩИ", "ПОЩАДА"};
    private static final String[] ICONS = {"</>", ">_", "[ ]", "~"};
    private static final float TAU = (float) (Math.PI * 2);

    private static final String[][] BOSS_TALK = {
            {"Ну здравствуй, Нарезанный.", "Опять с нейросеткой пришёл?", "Тут она не поможет."},
            {"Я писал код, когда твой Claude был ещё if-ом в чьём-то дипломе."},
            {"Ты хоть знаешь, что такое указатель?", "Не гугли."},
            {"Каждая точка с запятой выстрадана.", "Каждая!"},
            {"Знаешь, сколько ночей я дебажил один?"},
            {"Никто не читает мой код. Никто."},
            {"Документацию я тоже писал сам.", "Её тоже никто не читал."},
            {"Баги? У меня нет багов.", "Только незадокументированные фичи."},
    };
    private static final String[] BOSS_IDLE = {"Ctrl+C, Ctrl+V. Вот и вся ваша магия.", "Компилируется. Значит, работает.",
            "Я не устал. Я в потоке.", "Ещё одна строчка. Ещё одна.", "Табы, а не пробелы. Это не обсуждается."};

    private static final class Bullet {
        PartyVec p, v, h;
        String g = "";
        float fs;
        int c = PartyGfx.WHITE;
        int d;
        boolean heal;
        float life = 12f;
        boolean ring, bug, bounce;
        float a, rot, r, sp;
        String lab;
        boolean hasGrav, hasWave;
        float grav, wave, y0, ph;
    }

    private static final class Hazard {
        PartyRect r;
        float t, w, e;
        String kind;
        boolean h;
    }

    private static final class Foe {
        String name;
        int hp, max, dmg;
        List<String> acts = new ArrayList<>();
    }

    final PartyGame game;
    final String enemy;
    private final Foe e = new Foe();
    private PartyEnemies.E data;
    private String state = "intro";
    private float t, tt;
    private int btn, sub;
    private String text = "";
    private float shown;
    private String after = "";
    private PartyRect box = MENU_BOX.copy(), boxFrom = MENU_BOX.copy(), boxTo = MENU_BOX.copy();
    private PartyVec soul = new PartyVec(320, 320);
    private float soulVy;
    private boolean gravity;
    private float invT;
    private List<Bullet> bullets = new ArrayList<>();
    private List<Hazard> lasers = new ArrayList<>();
    private String atk = "";
    private float atkLen = 6f;
    private final Map<String, Float> acc = new HashMap<>();
    private String[] bubble = new String[0];
    private int bubbleI;
    private float bubbleShown;
    private int dmgVal = -1;
    private float dmgT, hurtShake;
    private float aimX;
    private int turn;
    private final Map<String, Boolean> flags = new HashMap<>();
    private int claude;
    private boolean spareable, finalDone;
    private float fade;
    private String[] react = new String[0];
    private String flavor = "";
    private int sparePts;
    private String atk2 = "";
    private String curPat = "";
    private float hard = 1f;
    private float shakeX, shakeY;
    private PartyGfx g;
    /** The enemy's party hat: where it was last drawn, and once the enemy is gone, flying off and bouncing on the floor. */
    private float hatX, hatY, hatVx, hatVy, hatSpin, hatAngle, hatT = -1f;
    private int hatScale = 3, hatColour;
    private boolean hatSeen;

    PartyBattle(PartyGame game, String enemy) {
        this.game = game;
        this.enemy = enemy;
        if (enemy.equals("boss")) {
            e.name = "СЕЛФКОДИНГ";
            e.hp = e.max = 260;
            // a little gentler than the original (2.3.1): combos were too much
            e.dmg = 5;
            e.acts.addAll(Arrays.asList("Проверить", "Похвалить код", "Спросить про тесты", "Позвать Claude", "Предложить вместе"));
            if (game.num("diary") >= 3) e.acts.add("Вспомнить дневник");
            e.acts.add("Пригласить");
            game.playMusic("boss");
            text("Селфкодинг преграждает путь. Клавиатура в его руках уже стучит. Рядом стоит твой торт.", "menu");
        } else {
            data = PartyEnemies.ALL.get(enemy);
            e.name = data.name;
            e.hp = e.max = data.hp;
            e.dmg = data.dmg;
            e.acts.addAll(data.acts.keySet());
            game.playMusic(data.music);
            text(data.intro, "menu");
        }
        flavor = text;
    }

    private void text(String s, String next) {
        text = s;
        shown = 0f;
        after = next;
        state = "text";
        t = 0f;
    }

    // ------------------------------------------------------------ input and turns

    @Override
    public void update(float dt) {
        t += dt;
        tt += dt;
        hatFly(dt);
        invT = Math.max(invT - dt, 0f);
        hurtShake = Math.max(hurtShake - dt, 0f);
        dmgT = Math.max(dmgT - dt, 0f);
        PartyInput in = game.input;
        boolean ok = in.justPressed(PartyInput.OK) && t > 0.05f;
        boolean back = in.justPressed(PartyInput.CANCEL);
        switch (state) {
            case "text":
                type(dt);
                if (ok || back) {
                    if (shown < text.length()) shown = text.length();
                    else if (ok) after();
                }
                break;
            case "menu":
                type(dt);
                if (in.justPressed(PartyInput.LEFT)) {
                    btn = (btn + 3) % 4;
                    game.sfx.play("move");
                } else if (in.justPressed(PartyInput.RIGHT)) {
                    btn = (btn + 1) % 4;
                    game.sfx.play("move");
                } else if (ok) {
                    game.sfx.play("ok");
                    sub = 0;
                    switch (btn) {
                        case 0:
                        case 1:
                            state = "target";
                            break;
                        case 2:
                            if (game.items.isEmpty()) game.sfx.play("miss");
                            else state = "items";
                            break;
                        case 3:
                            state = "mercy";
                            break;
                    }
                    t = 0f;
                }
                break;
            case "target":
                if (ok) {
                    game.sfx.play("ok");
                    if (btn == 0) {
                        state = "aim";
                        aimX = MENU_BOX.x + 10f;
                    } else {
                        state = "acts";
                        sub = 0;
                    }
                    t = 0f;
                } else if (back) {
                    state = "menu";
                }
                break;
            case "acts":
            case "items":
            case "mercy": {
                int n = list().size();
                if (in.justPressed(PartyInput.UP) && sub >= 2) {
                    sub -= 2;
                    game.sfx.play("move");
                } else if (in.justPressed(PartyInput.DOWN) && sub + 2 < n) {
                    sub += 2;
                    game.sfx.play("move");
                } else if (in.justPressed(PartyInput.LEFT) && sub % 2 == 1) {
                    sub -= 1;
                    game.sfx.play("move");
                } else if (in.justPressed(PartyInput.RIGHT) && sub % 2 == 0 && sub + 1 < n) {
                    sub += 1;
                    game.sfx.play("move");
                } else if (ok) {
                    game.sfx.play("ok");
                    choose();
                } else if (back) {
                    state = state.equals("acts") ? "target" : "menu";
                    t = 0f;
                }
                break;
            }
            case "aim":
                aimX += 520f * dt;
                if (ok) {
                    strike();
                } else if (aimX > MENU_BOX.ex() - 10f) {
                    dmgVal = 0;
                    dmgT = 1.2f;
                    game.sfx.play("miss");
                    state = "slash";
                    t = 0.6f;
                }
                break;
            case "slash":
                if (t > 1.3f) {
                    if (e.hp <= 0) die();
                    else enemyTurn();
                }
                break;
            case "talk": {
                String line = bubble[bubbleI];
                int before = (int) bubbleShown;
                bubbleShown = Math.min(bubbleShown + 30f * dt, line.length());
                if ((int) bubbleShown != before && (int) bubbleShown % 2 == 0) game.sfx.play(enemy.equals("boss") ? "blip_boss" : "blip_cat");
                if (ok || back) {
                    if (bubbleShown < line.length()) {
                        bubbleShown = line.length();
                    } else if (ok) {
                        bubbleI++;
                        bubbleShown = 0f;
                        if (bubbleI >= bubble.length) {
                            if (after.equals("end_kill")) dust();
                            else startAttack();
                        }
                    }
                }
                break;
            }
            case "to_attack":
                box = PartyRect.lerp(boxFrom, boxTo, Math.min(1f, t / 0.25f));
                if (t >= 0.25f) {
                    state = "attack";
                    t = 0f;
                    soul = box.center();
                    soulVy = 0f;
                }
                break;
            case "attack":
                soul(dt);
                runAttack(dt);
                bullets(dt);
                if (t >= atkLen && state.equals("attack")) {
                    bullets.clear();
                    lasers.clear();
                    gravity = false;
                    boxFrom = box;
                    boxTo = MENU_BOX.copy();
                    state = "to_menu";
                    t = 0f;
                }
                break;
            case "to_menu":
                box = PartyRect.lerp(boxFrom, boxTo, Math.min(1f, t / 0.25f));
                if (t >= 0.25f) {
                    box = MENU_BOX.copy();
                    turn++;
                    newFlavor();
                    text = flavor;
                    shown = 0f;
                    state = "menu";
                }
                break;
            case "dust":
                fade = Math.min(1f, t / 1.6f);
                if (t > 1.8f) text(enemy.equals("boss") ? "Ты победил." : data.killText + " Ты получаешь " + reward(true) + " ₽.", "end_kill");
                break;
            case "spare_fade":
                fade = Math.min(1f, t / 1f);
                if (t > 1.2f) text(enemy.equals("boss") ? "Ты пощадил Селфкодинга." : data.spareText + " Ты получаешь " + reward(false) + " ₽.", "end_spare");
                break;
            case "lose":
                if (t > 0.6f) game.gameOver();
                break;
        }
    }

    private void type(float dt) {
        int before = (int) shown;
        shown = Math.min(shown + 40f * dt, text.length());
        if ((int) shown != before && (int) shown % 2 == 0 && state.equals("text")) game.sfx.play("blip_sys");
    }

    private void after() {
        switch (after) {
            case "menu":
                state = "menu";
                if (!flavor.isEmpty()) text = flavor;
                shown = text.length();
                break;
            case "enemy":
                enemyTurn();
                break;
            case "end_kill":
                game.battleDone(enemy, "kill");
                break;
            case "end_spare":
                game.battleDone(enemy, "spare");
                break;
            case "end_flee":
                game.battleDone(enemy, "flee");
                break;
        }
    }

    private List<String> list() {
        switch (state) {
            case "acts": return e.acts;
            case "items": return game.items;
            case "mercy": return Arrays.asList("Пощадить", "Сбежать");
        }
        return new ArrayList<>();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase();
    }

    private void choose() {
        switch (state) {
            case "acts":
                act(e.acts.get(sub));
                break;
            case "items": {
                String it = game.items.remove(sub);
                Object[] d = PartyGame.ITEM_DATA.get(it);
                if (d == null) d = new Object[]{5, "Ты ешь это. +5 HP."};
                game.hp = Math.min(game.hp + (Integer) d[0], game.maxHp);
                game.sfx.play("heal");
                text(game.hp < game.maxHp ? (String) d[1] : d[1] + " Здоровье полное.", "enemy");
                break;
            }
            case "mercy":
                if (sub == 0) {
                    if (spareable) {
                        game.sfx.play("spare");
                        state = "spare_fade";
                        t = 0f;
                    } else {
                        text("Ты пытаешься пощадить. Но " + (enemy.equals("boss") ? "Селфкодинг" : cap(e.name)) + " ещё не готов.", "enemy");
                    }
                } else {
                    if (enemy.equals("boss")) {
                        text("Нельзя сбежать от собственного кода. И от своего торта.", "enemy");
                    } else if (data.mini) {
                        text(cap(e.name) + " не даёт уйти. Придётся разобраться.", "enemy");
                    } else {
                        game.sfx.play("flee");
                        text("Ты убегаешь. " + cap(e.name) + " остаётся позади. Как всегда.", "end_flee");
                    }
                }
                break;
        }
    }

    private void act(String a) {
        react = new String[0];
        if (!enemy.equals("boss")) {
            actData(a);
            return;
        }
        switch (a) {
            case "Проверить":
                text("СЕЛФКОДИНГ. АТК 9, ЗАЩ 9. Пишет всё сам. Не доверяет никому. Даже себе вчерашнему.", "enemy");
                break;
            case "Похвалить код":
                if (!flags.containsKey("praise")) {
                    flags.put("praise", true);
                    react = new String[]{"...Красивый?", "Ты... правда его прочитал?"};
                    text("Ты говоришь, что его код красивый. Селфкодинг замирает.", "enemy");
                } else {
                    react = new String[]{"Хватит. Я не таю.", "...ну, может, чуть-чуть."};
                    text("Ты хвалишь снова. Монитор Селфкодинга слегка розовеет.", "enemy");
                }
                break;
            case "Спросить про тесты":
                if (flags.containsKey("praise") && !flags.containsKey("tests")) {
                    flags.put("tests", true);
                    react = new String[]{"Тесты...", "Я тестирую в проде.", "Мне не на кого было положиться."};
                    text("Ты спрашиваешь, есть ли у него тесты. Селфкодинг долго молчит.", "enemy");
                } else if (flags.containsKey("tests")) {
                    react = new String[]{"Я же сказал. В проде."};
                    text("Ты снова спрашиваешь про тесты. Он отводит экран.", "enemy");
                } else {
                    react = new String[]{"Не твоё дело!"};
                    text("Ты спрашиваешь про тесты. Селфкодинг злится. Может, сначала сказать что-то приятное?", "enemy");
                }
                break;
            case "Позвать Claude":
                claude++;
                switch (claude) {
                    case 1:
                        text("Ты пишешь промпт. Ответ: «Your account has been suspended.»", "enemy");
                        react = new String[]{"Ха! Видишь? Тебя забанили."};
                        break;
                    case 2:
                        text("Ты пишешь апелляцию. Капсом. Ответ: «Your account has been suspended.»", "enemy");
                        break;
                    case 3:
                        game.hp = game.maxHp;
                        game.sfx.play("heal");
                        react = new String[]{"...Что?", "Поговорить? Со мной?"};
                        text("Ты пишешь в поддержку «пожалуйста». Аккаунт оживает на одно сообщение: «С днём рождения! Похоже, вам двоим просто нужно поговорить». Здоровье восстановлено.", "enemy");
                        break;
                    default:
                        text("Аккаунт снова заблокирован. Но ты уже знаешь, что делать.", "enemy");
                }
                break;
            case "Вспомнить дневник":
                spareable = true;
                react = new String[]{"Ты... читал?", "Мой дневник...", "Никто никогда не читал."};
                text("Ты рассказываешь, что читал его дневник. Про первый «Hello, world». Про ночи без сна. Про жалобу, которую он написал от одиночества. Селфкодинг роняет клавиатуру.", "enemy");
                break;
            case "Предложить вместе":
                if (flags.containsKey("praise") && flags.containsKey("tests")) {
                    spareable = true;
                    react = new String[]{"Вместе?..", "Ты, я и... он?", "Я так долго писал один."};
                    text("Ты предлагаешь писать код вместе: он, ты и Claude. Селфкодинг опускает клавиатуру.", "enemy");
                } else {
                    react = new String[]{"Вместе? С нейросеткой? Ха!"};
                    text("Ты предлагаешь писать вместе. Селфкодинг смеётся. Он тебе пока не доверяет.", "enemy");
                }
                break;
            case "Пригласить":
                if (spareable) {
                    react = new String[]{"На день... рождения?", "Меня?", "...Я приду."};
                    text("Ты зовёшь Селфкодинга вечером на день рождения. Его монитор мигает. Кажется, он улыбается.", "enemy");
                } else if (!flags.containsKey("invite")) {
                    flags.put("invite", true);
                    react = new String[]{"Праздник? У тебя?", "Не отвлекай. Я пишу."};
                    text("Ты зовёшь его на свой день рождения. Селфкодинг на секунду перестаёт печатать.", "enemy");
                } else {
                    react = new String[]{"Я подумаю.", "...Там будет торт?"};
                    text("Ты снова зовёшь. Он делает вид, что не слышит. Но печатает медленнее.", "enemy");
                }
                break;
        }
    }

    /** An enemy's action from its data: conditions, flags, mercy points. */
    private void actData(String a) {
        PartyEnemies.Act d = data.acts.get(a);
        if (d.needs != null && !flags.containsKey(d.needs)) {
            text(d.tNo != null ? d.tNo : d.t, "enemy");
            return;
        }
        boolean first = !flags.containsKey("did_" + a);
        String txt = first || d.onceT == null ? d.t : d.onceT;
        flags.put("did_" + a, true);
        if (d.flag != null) flags.put(d.flag, true);
        if (d.spare > 0 && (first || d.repeat)) sparePts += d.spare;
        if (d.rage > 0) e.dmg += d.rage;
        react = d.react;
        if (sparePts >= data.need && !spareable) {
            spareable = true;
            txt += " Его можно пощадить.";
        }
        text(txt, "enemy");
    }

    /** Money for a fight; sparing gives a little less. */
    private int reward(boolean kill) {
        int m = data.money;
        if (!kill) m = (int) Math.ceil(m * 0.7);
        game.money += m;
        return m;
    }

    private void strike() {
        float c = MENU_BOX.cx();
        float k = 1f - Math.max(0f, Math.min(1f, Math.abs(aimX - c) / (MENU_BOX.w * 0.5f)));
        int top = enemy.equals("boss") ? 34 : data.top;
        dmgVal = Math.round(top * (0.25f + 0.75f * k * k));
        if (Math.abs(aimX - c) < 10f) dmgVal = top + 6;
        e.hp = Math.max(e.hp - dmgVal, 0);
        state = "slash";
        t = 0f;
        game.sfx.play("hit");
    }

    private void enemyTurn() {
        if (enemy.equals("boss")) {
            if (react.length == 0) {
                if (spareable) bubble = new String[]{"...", "Я больше не хочу драться.", "Но рукам надо дописать."};
                else if (e.hp < e.max * 0.35f && !finalDone) bubble = new String[]{"Нет... ещё один баг...", "Я починю. Я всегда чиню. САМ!"};
                else if (turn < BOSS_TALK.length) bubble = BOSS_TALK[turn];
                else bubble = new String[]{BOSS_IDLE[game.randi(BOSS_IDLE.length)]};
            } else {
                bubble = react;
            }
        } else if (react.length > 0) {
            bubble = react;
        } else if (spareable) {
            bubble = new String[]{"..."};
        } else if (data.low != null && e.hp < e.max * 0.3f) {
            bubble = data.low;
        } else if (data.talkSeq != null && turn < data.talkSeq.length) {
            bubble = data.talkSeq[turn];
        } else {
            bubble = new String[]{data.talk[game.randi(data.talk.length)]};
        }
        react = new String[0];
        bubbleI = 0;
        bubbleShown = 0f;
        after = "";
        state = "talk";
        t = 0f;
    }

    private void die() {
        if (enemy.equals("boss")) {
            bubble = new String[]{"Значит... руками больше никто не пишет...", "Торт... отдай гостям... Сохрани хотя бы... мои комменты..."};
            bubbleI = 0;
            bubbleShown = 0f;
            after = "end_kill";
            state = "talk";
            t = 0f;
            game.flags.put("boss_done", true);
        } else {
            dust();
        }
    }

    private void dust() {
        game.playMusic("");
        game.sfx.play("shatter");
        state = "dust";
        t = 0f;
        // the hat does not vanish with its owner: it pops off, falls and bounces
        if (hatSeen) {
            hatT = 0f;
            hatVx = (game.rng.nextBoolean() ? 1 : -1) * game.randf(60, 110);
            hatVy = -game.randf(260, 320);
            hatSpin = hatVx > 0 ? 7f : -7f;
        }
    }

    private static final float HAT_FLOOR = 236f;

    private void hatFly(float dt) {
        if (hatT < 0f) return;
        hatT += dt;
        hatVy += 900f * dt;
        hatX += hatVx * dt;
        hatY += hatVy * dt;
        hatAngle += hatSpin * dt;
        if (hatY >= HAT_FLOOR && hatVy > 0) {
            hatY = HAT_FLOOR;
            hatVy = -hatVy * 0.42f;
            hatVx *= 0.6f;
            hatSpin *= 0.5f;
            if (Math.abs(hatVy) < 40f) {
                hatVy = 0f;
                hatSpin = 0f;
                hatAngle = Math.round(hatAngle / (float) Math.PI) * (float) Math.PI;
            } else {
                game.sfx.play("move", 0.7f);
            }
        }
    }

    private void drawFallingHat() {
        if (hatT < 0f) return;
        float a = hatT < 2.2f ? 1f : Math.max(0f, 1f - (hatT - 2.2f) / 0.8f);
        if (a <= 0f) return;
        g.transform(hatX + shakeX, hatY + shakeY, hatAngle);
        PartyDeco.hat(g, 0, 0, hatScale, hatColour, a);
        g.transform(shakeX, shakeY);
    }

    private void newFlavor() {
        if (spareable) {
            flavor = enemy.equals("boss") ? "Селфкодинг больше не хочет драться." : cap(e.name) + " больше не хочет драться.";
        } else if (enemy.equals("boss")) {
            String[] opts = {"Селфкодинг стучит по клавишам так, что дрожит пол.", "Пахнет кофе, паяльником и кремом.",
                    "На экране Селфкодинга бегут строчки. Ты узнаёшь свой старый код.", "Селфкодинг поправляет капюшон.",
                    "Свечи на торте тихо горят. Селфкодинг на них иногда косится."};
            if (e.hp < e.max * 0.35f) opts = new String[]{"Селфкодинг тяжело дышит. Вентилятор воет.", "По экрану Селфкодинга ползут артефакты."};
            flavor = opts[game.randi(opts.length)];
        } else {
            flavor = data.flavor[game.randi(data.flavor.length)];
        }
    }

    // ------------------------------------------------------------ attacks

    private void startAttack() {
        acc.clear();
        bullets.clear();
        lasers.clear();
        gravity = false;
        atk2 = "";
        if (enemy.equals("boss")) hard = 1f + Math.min(turn * 0.04f, 0.25f);
        else if (data.mini) hard = 1f + Math.min(turn * 0.07f, 0.4f);
        else hard = 1f + Math.min(turn * 0.05f, 0.2f);
        if (!enemy.equals("boss")) {
            atk = data.attacks[turn % data.attacks.length];
            atkLen = data.mini ? 7f : 5.5f;
            if (spareable) atkLen = 3.5f;
        } else if (spareable) {
            atk = "semis_soft";
            atkLen = 4f;
        } else if (e.hp < e.max * 0.35f && !finalDone) {
            atk = "final";
            atkLen = 9f;
            finalDone = true;
        } else {
            String[] order = {"semis", "braces", "loop", "merge", "stack", "review", "docs", "deploy", "bugs", "candles"};
            atk = turn < order.length ? order[turn] : order[game.randi(order.length)];
            switch (atk) {
                case "semis": atkLen = 6f; break;
                case "braces": atkLen = 6.5f; break;
                case "merge":
                case "stack": atkLen = 8f; break;
                case "review": atkLen = 7.5f; break;
                default: atkLen = 7f;
            }
            // second phase: under 60% health a light attack flies on top of the main one
            if (e.hp < e.max * 0.45f && !atk.equals("stack")) {
                atk2 = atk.equals("semis") || atk.equals("review") ? "bugs_light" : "semis_light";
            }
        }
        PartyRect r = new PartyRect(220, 230, 200, 160);
        switch (atk) {
            case "merge":
            case "docs":
                r = new PartyRect(170, 230, 300, 160);
                break;
            case "stack":
                r = new PartyRect(150, 250, 340, 130);
                gravity = true;
                break;
            case "deploy":
            case "final":
            case "review":
                r = new PartyRect(200, 220, 240, 180);
                break;
            case "grid":
                r = new PartyRect(230, 220, 180, 180);
                break;
            case "letters":
            case "numbers":
            case "rules":
            case "hammer":
            case "mail":
            case "coins":
            case "candles":
                r = new PartyRect(170, 230, 300, 160);
                break;
            case "lanes":
                r = new PartyRect(200, 230, 240, 160);
                break;
        }
        boxFrom = box;
        boxTo = r;
        state = "to_attack";
        t = 0f;
    }

    private int every(String key0, float period, float dt) {
        String key = curPat + ":" + key0;
        period /= hard;
        Float known = acc.get(key);
        float v = (known == null ? period : known) + dt;
        int n = 0;
        while (v >= period) {
            v -= period;
            n++;
        }
        acc.put(key, v);
        return n;
    }

    private Bullet bullet(float px, float py, float vx, float vy, String glyph, float size, float hx, float hy) {
        Bullet b = new Bullet();
        b.p = new PartyVec(px, py);
        b.v = new PartyVec(vx, vy);
        b.g = glyph;
        b.fs = size;
        b.h = new PartyVec(hx, hy);
        b.d = e.dmg;
        bullets.add(b);
        return b;
    }

    private void todo(float px, float py, float vy) {
        Bullet b = bullet(px, py, 0, vy, "// TODO", 14, 28, 6);
        b.c = GREEN;
        b.heal = true;
    }

    private void runAttack(float dt) {
        curPat = atk;
        pattern(atk, dt);
        if (!atk2.isEmpty()) {
            curPat = atk2;
            pattern(atk2, dt);
        }
        updateHazards(dt);
    }

    private float rf(float a, float b) { return game.randf(a, b); }

    private void pattern(String pat, float dt) {
        PartyRect b = box;
        switch (pat) {
            case "review":
                // code review: only the green zone is approved, everything else burns
                for (int i = every("rv", 1.9f, dt); i > 0; i--) {
                    float sz = 76;
                    hazard(new PartyRect(rf(b.x + 4, b.ex() - sz - 4), rf(b.y + 4, b.ey() - sz - 4), sz, sz), "safe", 1f, 1.45f, false);
                }
                for (int i = every("rs", 0.5f, dt); i > 0; i--) bullet(rf(b.x + 8, b.ex() - 8), b.y - 8, 0, 130, ";", 20, 4, 7);
                break;
            case "semis":
            case "semis_light":
            case "semis_soft": {
                float per = pat.equals("semis") ? 0.11f : pat.equals("semis_light") ? 0.22f : 0.3f;
                for (int i = every("s", per, dt); i > 0; i--) bullet(rf(b.x + 8, b.ex() - 8), b.y - 8, 0, rf(150, 210), ";", 22, 4, 8);
                for (int i = every("todo", 2.2f, dt); i > 0; i--) todo(rf(b.x + 30, b.ex() - 30), b.y - 8, 80);
                break;
            }
            case "braces":
                for (int i = every("b", 0.42f, dt); i > 0; i--) {
                    float side = acc.containsKey("side") ? acc.get("side") : 0f;
                    boolean left = ((int) side) % 2 == 0;
                    acc.put("side", side + 1);
                    float y = rf(b.y + 10, b.ey() - 10);
                    float sp = rf(140, 175) + t * 4f;
                    bullet(left ? b.x - 10 : b.ex() + 10, y, left ? sp : -sp, 0, left ? "{" : "}", 26, 4, 7);
                }
                break;
            case "loop":
            case "final":
                if (pat.equals("loop") || t > 2f) {
                    for (int i = every("ring", pat.equals("loop") ? 2.3f : 3.6f, dt); i > 0; i--) {
                        int gap = game.randi(14);
                        float rot = game.rng.nextFloat() < 0.5f ? 1.3f : -1.3f;
                        for (int k = 0; k < 14; k++) {
                            if ((k - gap + 14) % 14 < 3) continue;
                            Bullet bl = bullet(b.cx(), b.cy(), 0, 0, k % 2 == 0 ? "0" : "1", 20, 5, 8);
                            bl.ring = true;
                            bl.a = TAU * k / 14f;
                            bl.rot = rot;
                            bl.r = 150f;
                            bl.life = 3f;
                        }
                    }
                }
                if (pat.equals("final")) {
                    for (int i = every("s", 0.21f, dt); i > 0; i--) bullet(rf(b.x + 8, b.ex() - 8), b.y - 8, 0, rf(140, 185), ";", 22, 4, 8);
                    for (int i = every("laser", 1.9f, dt); i > 0; i--) laser();
                    for (int i = every("todo", 3f, dt); i > 0; i--) todo(rf(b.x + 30, b.ex() - 30), b.y - 8, 70);
                }
                break;
            case "merge": {
                // lines come from above first, then a pause, then from below: never both at once.
                // The next gap is near the last one, so it can be reached in time
                boolean down = t < 2.9f;
                if (t < 2.9f || t >= 4.4f) {
                    for (int i = every("m", 1.25f, dt); i > 0; i--) {
                        float last = acc.containsKey("gx") ? acc.get("gx") : b.cx();
                        float gx = Math.max(b.x + 36, Math.min(b.ex() - 36, last + rf(-95f, 95f)));
                        acc.put("gx", gx);
                        float gw = 64f;
                        float y = down ? b.y - 8 : b.ey() + 8;
                        float vy = down ? 100 : -100;
                        String lab = down ? "<<<<<<<<<<<<<<<<<<<<" : ">>>>>>>>>>>>>>>>>>>>";
                        float lw = gx - gw * 0.5f - b.x;
                        float rw = b.ex() - (gx + gw * 0.5f);
                        Bullet l = bullet(b.x + lw * 0.5f, y, 0, vy, "", 0, lw * 0.5f, 7);
                        l.lab = lab;
                        l.c = PartyGfx.rgb(1f, 0.45f, 0.45f);
                        Bullet r = bullet(b.ex() - rw * 0.5f, y, 0, vy, "", 0, rw * 0.5f, 7);
                        r.lab = lab;
                        r.c = PartyGfx.rgb(1f, 0.45f, 0.45f);
                    }
                }
                break;
            }
            case "stack": {
                float per = rf(0.9f, 1.25f);
                for (int i = every("st", per, dt); i > 0; i--) {
                    boolean fromLeft = t > 4f && game.rng.nextFloat() < 0.35f;
                    int hgt = 1 + game.randi(3);
                    for (int k = 0; k < hgt; k++) {
                        float y = b.ey() - 9 - k * 18;
                        Bullet bl = bullet(fromLeft ? b.x - 12 : b.ex() + 12, y, fromLeft ? 190 : -190, 0, "", 0, 11, 8);
                        bl.lab = "[]";
                        bl.c = ORANGE;
                    }
                }
                break;
            }
            case "docs":
                walls(b, "d", 0.17f, 1.7f, 42f, 34f, -200f, PartyGfx.rgb(0.8f, 0.8f, 0.8f), dt);
                break;
            case "rules":
                walls(b, "d", 0.2f, 1.4f, 38f, 36f, -180f, PartyGfx.rgb(0.85f, 0.8f, 0.6f), dt);
                break;
            case "deploy":
                for (int i = every("laser", Math.max(1.05f - t * 0.06f, 0.6f), dt); i > 0; i--) laser();
                break;
            case "bugs":
            case "bugs_light":
                for (int i = every("bug", pat.equals("bugs") ? 0.75f : 1.2f, dt); i > 0; i--) {
                    int side = game.randi(4);
                    PartyVec p;
                    switch (side) {
                        case 0: p = new PartyVec(rf(b.x, b.ex()), b.y - 6); break;
                        case 1: p = new PartyVec(rf(b.x, b.ex()), b.ey() + 6); break;
                        case 2: p = new PartyVec(b.x - 6, rf(b.y, b.ey())); break;
                        default: p = new PartyVec(b.ex() + 6, rf(b.y, b.ey()));
                    }
                    PartyVec v = soul.sub(p).normalized().mul(60f);
                    Bullet bl = bullet(p.x, p.y, v.x, v.y, "", 0, 9, 7);
                    bl.bug = true;
                    bl.life = 4.5f;
                    bl.sp = pat.equals("bugs") ? 85f : 60f;
                }
                if (pat.equals("bugs")) for (int i = every("todo", 2.8f, dt); i > 0; i--) todo(rf(b.x + 30, b.ex() - 30), b.y - 8, 70);
                break;
            case "candles":
                // birthday candles rain down; a little flame on each, and a slice of cake heals
                for (int i = every("cd", 0.2f, dt); i > 0; i--) {
                    Bullet bl = bullet(rf(b.x + 10, b.ex() - 10), b.y - 8, rf(-20, 20), rf(140, 190), "i", 22, 3, 9);
                    bl.c = PartyDeco.COLOURS[game.randi(PartyDeco.COLOURS.length)];
                }
                for (int i = every("cake", 2.4f, dt); i > 0; i--) {
                    Bullet bl = bullet(rf(b.x + 30, b.ex() - 30), b.y - 8, 0, 80, "🍰", 20, 8, 8);
                    bl.heal = true;
                }
                break;
            case "mail":
                // envelopes fly in from the top corners, slanting down
                for (int i = every("ml", 0.32f, dt); i > 0; i--) {
                    boolean left = game.rng.nextFloat() < 0.5f;
                    PartyVec p = new PartyVec(left ? b.x - 6 : b.ex() + 6, b.y + rf(-10, 40));
                    PartyVec to = new PartyVec(rf(b.x + 20, b.ex() - 20), b.ey() + 10);
                    PartyVec v = to.sub(p).normalized().mul(rf(130, 165));
                    bullet(p.x, p.y, v.x, v.y, "✉", 20, 7, 6);
                }
                break;
            case "coins":
                // coins jump up from below and fall back
                for (int i = every("cn", 0.38f, dt); i > 0; i--) {
                    Bullet bl = bullet(rf(b.x + 20, b.ex() - 20), b.ey() + 6, rf(-70, 70), rf(-330, -270), "$", 22, 5, 8);
                    bl.hasGrav = true;
                    bl.grav = 430f;
                    bl.c = PartyGfx.rgb(1f, 0.85f, 0.3f);
                }
                break;
            case "windows":
                for (int i = every("win", 0.75f, dt); i > 0; i--) {
                    float w = rf(56, 80), h = rf(40, 54);
                    hazard(new PartyRect(rf(b.x, b.ex() - w), rf(b.y, b.ey() - h), w, h), "window", 0.7f, 1.6f, false);
                }
                break;
            case "cross":
                for (int i = every("cr", 0.7f, dt); i > 0; i--) {
                    PartyVec v = PartyVec.angle(rf(0.4f, 2.7f)).mul(125f);
                    Bullet bl = bullet(rf(b.x + 10, b.ex() - 10), b.y + 4, v.x, v.y, "✕", 20, 5, 6);
                    bl.bounce = true;
                    bl.life = 4f;
                    bl.c = PartyGfx.rgb(1f, 0.4f, 0.4f);
                }
                break;
            case "lanes":
                // columns of tickets fall along lanes; a lane lights up first
                for (int i = every("ln", 0.55f, dt); i > 0; i--) {
                    int n = 4;
                    float lw = b.w / n;
                    int k = game.randi(n);
                    hazard(new PartyRect(b.x + k * lw + 2, b.y - 4, lw - 4, b.h + 8), "lane", 0.6f, 1f, false);
                }
                break;
            case "numbers":
                for (int i = every("nm", 0.2f, dt); i > 0; i--) {
                    float y0 = rf(b.y + 20, b.ey() - 20);
                    String digits = "4815162342";
                    Bullet bl = bullet(b.ex() + 8, y0, -150, 0, String.valueOf(digits.charAt(game.randi(10))), 20, 5, 8);
                    bl.hasWave = true;
                    bl.wave = 26f;
                    bl.y0 = y0;
                    bl.ph = game.rng.nextFloat() * TAU;
                }
                break;
            case "grid":
                // a 3x3 grid: some cells blink, then strike
                for (int i = every("gr", 1.1f, dt); i > 0; i--) {
                    float cw = b.w / 3f;
                    List<Integer> cells = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8));
                    java.util.Collections.shuffle(cells, game.rng);
                    int many = 4 + Math.min((int) (t / 2.5f), 2);
                    for (int k = 0; k < many; k++) {
                        int c = cells.get(k);
                        hazard(new PartyRect(b.x + (c % 3) * cw + 2, b.y + (c / 3) * cw + 2, cw - 4, cw - 4), "cell", 0.7f, 1.05f, false);
                    }
                }
                break;
            case "lights":
                for (int i = every("lt", 0.24f, dt); i > 0; i--) {
                    boolean gr = game.rng.nextFloat() < 0.18f;
                    int col = gr ? GREEN : (game.randi(2) == 0 ? PartyGfx.rgb(1f, 0.3f, 0.3f) : PartyGfx.rgb(1f, 0.85f, 0.3f));
                    Bullet bl = bullet(rf(b.x + 8, b.ex() - 8), b.y - 8, 0, rf(140, 190), "●", 20, 6, 6);
                    bl.c = col;
                    bl.heal = gr;
                }
                break;
            case "letters":
                // the captcha's twisted letters crawl in a wave
                for (int i = every("lr", 0.17f, dt); i > 0; i--) {
                    float y0 = b.cy() + (float) Math.sin(t * 2f) * 40f;
                    String letters = "x7Gq2kR";
                    float yy = y0 + rf(-30, 30);
                    Bullet bl = bullet(b.ex() + 8, yy, -165, 0, String.valueOf(letters.charAt(game.randi(7))), 24, 6, 9);
                    bl.hasWave = true;
                    bl.wave = 18f;
                    bl.y0 = y0 + rf(-30, 30);
                    bl.ph = game.rng.nextFloat() * TAU;
                    bl.c = PartyGfx.rgb(0.75f, 0.8f, 1f);
                }
                break;
            case "hammer":
                // the ban hammer strikes where you stand
                for (int i = every("hm", 1.05f, dt); i > 0; i--) {
                    float x = Math.max(b.x + 30, Math.min(b.ex() - 30, soul.x + rf(-30, 30)));
                    hazard(new PartyRect(x - 30, b.y - 4, 60, b.h + 8), "hammer", 0.65f, 0.95f, false);
                }
                for (int i = every("bn", 0.6f, dt); i > 0; i--) {
                    Bullet bl = bullet(rf(b.x, b.ex()), b.ey() + 8, 0, -120, "BAN", 14, 14, 6);
                    bl.c = PartyGfx.rgb(1f, 0.4f, 0.4f);
                }
                break;
            case "ban_ring":
                for (int i = every("ring", 2.3f, dt); i > 0; i--) {
                    int gap = game.randi(10);
                    float rot = game.rng.nextFloat() < 0.5f ? 1.1f : -1.1f;
                    for (int k = 0; k < 10; k++) {
                        if ((k - gap + 10) % 10 < 3) continue;
                        Bullet bl = bullet(b.cx(), b.cy(), 0, 0, "BAN", 14, 13, 6);
                        bl.ring = true;
                        bl.a = TAU * k / 10f;
                        bl.rot = rot;
                        bl.r = 150f;
                        bl.life = 3f;
                        bl.c = PartyGfx.rgb(1f, 0.45f, 0.45f);
                    }
                }
                break;
        }
    }

    /** Walls of paper sliding in with a gap that drifts up and down. */
    private void walls(PartyRect b, String key, float period, float speed, float swing, float half, float vx, int col, float dt) {
        for (int i = every(key, period, dt); i > 0; i--) {
            float gc = b.cy() + (float) Math.sin(t * speed) * swing;
            float topH = gc - half - b.y;
            float botH = b.ey() - (gc + half);
            float x = b.ex() + 8;
            if (topH > 2f) {
                Bullet bl = bullet(x, b.y + topH * 0.5f, vx, 0, "", 0, 7, topH * 0.5f);
                bl.lab = "≡";
                bl.c = col;
            }
            if (botH > 2f) {
                Bullet bl = bullet(x, b.ey() - botH * 0.5f, vx, 0, "", 0, 7, botH * 0.5f);
                bl.lab = "≡";
                bl.c = col;
            }
        }
    }

    private void updateHazards(float dt) {
        for (Hazard l : lasers) {
            float before = l.t;
            l.t += dt;
            if (before < l.w && l.t >= l.w) {
                game.sfx.play(l.kind.equals("laser") || l.kind.equals("hammer") ? "laser" : "hit");
            }
        }
        lasers.removeIf(l -> l.t >= l.e + 0.05f);
    }

    private void laser() {
        PartyRect b = box;
        boolean horiz = game.rng.nextFloat() < 0.5f;
        PartyRect r;
        if (horiz) {
            float y = rf(b.y + 14, b.ey() - 14);
            r = new PartyRect(b.x - 4, y - 13, b.w + 8, 26);
        } else {
            float x = rf(b.x + 14, b.ex() - 14);
            r = new PartyRect(x - 13, b.y - 4, 26, b.h + 8);
        }
        hazard(r, "laser", 0.6f, 0.95f, horiz);
    }

    /** A zone: first it blinks a warning for w seconds, then it strikes until e. */
    private void hazard(PartyRect r, String kind, float w, float e, boolean h) {
        Hazard l = new Hazard();
        l.r = r;
        l.kind = kind;
        l.w = w;
        l.e = e;
        l.h = h;
        lasers.add(l);
        game.sfx.play("warn");
    }

    private void soul(float dt) {
        PartyInput in = game.input;
        PartyVec v = new PartyVec(in.axisX(), in.axisY());
        PartyRect lim = box.grow(-9f);
        if (gravity) {
            soul.x += v.x * 150f * dt;
            soulVy += 760f * dt;
            boolean onFloor = soul.y >= lim.ey() - 0.5f;
            if (onFloor && in.pressed(PartyInput.UP)) soulVy = -340f;
            if (!in.pressed(PartyInput.UP) && soulVy < -120f) soulVy = -120f;
            soul.y += soulVy * dt;
            if (soul.y >= lim.ey()) {
                soul.y = lim.ey();
                soulVy = 0f;
            }
        } else {
            if (v.length() > 1f) v = v.normalized();
            soul = soul.add(v.mul(150f * dt));
        }
        soul = soul.clamp(lim.pos(), lim.end());
        if (soul.y <= lim.y && soulVy < 0f) soulVy = 0f;
    }

    private void bullets(float dt) {
        PartyRect hit = new PartyRect(soul.x - 4, soul.y - 4, 8, 8);
        List<Bullet> keep = new ArrayList<>();
        PartyRect wide = box.grow(60f);
        for (Bullet b : bullets) {
            b.life -= dt;
            if (b.ring) {
                b.r -= 68f * dt;
                b.a += b.rot * dt;
                b.p = box.center().add(PartyVec.angle(b.a).mul(b.r));
                if (b.r < 10f) continue;
            } else if (b.bug) {
                PartyVec want = soul.sub(b.p).normalized().mul(b.sp);
                b.v = b.v.lerp(want, Math.min(1.6f * dt, 1f));
                b.p = b.p.add(b.v.mul(dt));
            } else {
                if (b.hasGrav) b.v = b.v.add(0, b.grav * dt);
                b.p = b.p.add(b.v.mul(dt));
                if (b.hasWave) b.p = new PartyVec(b.p.x, b.y0 + (float) Math.sin(tt * 4f + b.ph) * b.wave);
                if (b.bounce) {
                    PartyRect lim = box.grow(-6f);
                    if (b.p.x < lim.x || b.p.x > lim.ex()) b.v = new PartyVec(-b.v.x, b.v.y);
                    if (b.p.y < lim.y || b.p.y > lim.ey()) b.v = new PartyVec(b.v.x, -b.v.y);
                    b.p = b.p.clamp(lim.pos(), lim.end());
                }
            }
            if (b.life <= 0f || !wide.has(b.p)) continue;
            PartyRect r = new PartyRect(b.p.x - b.h.x, b.p.y - b.h.y, b.h.x * 2f, b.h.y * 2f);
            if (box.grow(2f).has(b.p) && r.intersects(hit)) {
                if (b.heal) {
                    game.hp = Math.min(game.hp + 2, game.maxHp);
                    game.sfx.play("heal");
                    continue;
                }
                hurt(b.d);
            }
            keep.add(b);
        }
        bullets = keep;
        for (Hazard l : lasers) {
            if (l.kind.equals("safe")) {
                if (l.t >= l.w && l.t < l.e && !l.r.grow(2f).encloses(hit)) hurt(e.dmg);
                continue;
            }
            if (l.t >= l.w && l.t < l.e && l.r.grow(-4f).intersects(hit)) hurt(e.dmg + 1);
        }
    }

    private void hurt(int d) {
        if (invT > 0f || !state.equals("attack")) return;
        game.hp -= d;
        invT = enemy.equals("boss") ? 0.85f : 0.65f;
        hurtShake = 0.25f;
        game.sfx.play("hurt");
        if (game.hp <= 0) {
            game.hp = 0;
            game.playMusic("");
            state = "lose";
            t = 0f;
        }
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void draw(PartyGfx gfx) {
        g = gfx;
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        shakeX = hurtShake > 0f ? game.randf(-3, 3) : 0f;
        shakeY = hurtShake > 0f ? game.randf(-2, 2) : 0f;
        g.transform(shakeX, shakeY);
        drawEnemy();
        drawFallingHat();
        drawBox();
        drawHud();
        if (state.equals("talk") && bubbleI < bubble.length) {
            String s = bubble[bubbleI];
            drawBubble(s.substring(0, Math.min((int) bubbleShown, s.length())));
        }
        g.transform(0, 0);
    }

    private void t(String s, float x, float y, float size, int col, boolean center) { g.text(s, x, y, size, col, center); }

    private float enemyAlpha() {
        boolean ending = state.equals("dust") || state.equals("spare_fade") || (state.equals("text") && (after.equals("end_kill") || after.equals("end_spare")));
        return ending ? 1f - fade : 1f;
    }

    private void drawEnemy() {
        float a = enemyAlpha();
        if (state.equals("text") && (after.equals("end_kill") || after.equals("end_spare"))) a = 0f;
        float sx = state.equals("slash") && t > 0.25f && t < 0.7f && dmgVal > 0 ? (float) Math.sin(t * 60f) * 6f : 0f;
        int col = PartyGfx.alpha(PartyGfx.WHITE, a);
        if (state.equals("spare_fade")) col = PartyGfx.rgba(0.6f, 0.6f, 0.6f, a);
        if (enemy.equals("moderator")) {
            Bitmap mt = game.tex("moderator");
            float sc = data.scale;
            float w = mt.getWidth() * sc, h = mt.getHeight() * sc;
            float mx = 320 - w * 0.5f + sx, my = 14 + (float) Math.sin(tt * 2f) * 3f;
            float swing = state.equals("attack") && (tt % 1.05f) < 0.3f ? -0.9f : -0.25f;
            g.transform(mx + w * 0.86f + shakeX, my + h * 0.62f + shakeY, swing);
            g.tex(game.tex("banhammer"), -30, -120, 60, 100, col);
            g.transform(shakeX, shakeY);
            g.tex(mt, mx, my, w, h, col);
            if (hatT < 0f) {
                int[] hd = PartyDeco.head(mt);
                remember(mx + hd[0] * sc, my + hd[1] * sc, 2);
                PartyDeco.hat(g, hatX, hatY, hatScale, hatColour, a);
            }
        } else if (!enemy.equals("boss")) {
            Bitmap et = game.tex(data.sprite + "_" + ((int) (tt * 4f) % 2));
            if (et != null) {
                float w = et.getWidth() * data.scale, h = et.getHeight() * data.scale;
                float ey = 140 - h * 0.5f + (float) Math.sin(tt * 3f) * 4f;
                g.tex(et, 320 - w * 0.5f + sx, ey, w, h, col);
                if (hatT < 0f) {
                    int[] hd = PartyDeco.head(et);
                    remember(320 - w * 0.5f + sx + hd[0] * data.scale, ey + hd[1] * data.scale, enemy.length());
                    PartyDeco.hat(g, hatX, hatY, hatScale, hatColour, a);
                }
            }
        } else {
            float bob = (float) Math.sin(tt * 2.2f) * 3f;
            float ox = 200 + sx, oy = 34;
            // a tail of falling code under the hoodie
            String code = "01{};";
            for (int k = 0; k < 26; k++) {
                float x = ox + 40 + (k * 53f) % 160f;
                float y = oy + 170 + (tt * 40f + k * 17f) % 50f;
                t(String.valueOf(code.charAt(k % 5)), x, y, 14, PartyGfx.alpha(GREEN, 0.5f * a * (1f - (y - oy - 170) / 50f)), false);
            }
            g.tex(game.tex("boss_body"), ox, oy + bob, 240, 220, col);
            float arm = state.equals("attack") ? (float) Math.sin(tt * 9f) * 2f : 0f;
            g.tex(game.tex("boss_arm_l"), ox + 16, oy + 60 + bob + arm, 80, 120, col);
            g.tex(game.tex("boss_arm_r"), ox + 144, oy + 60 + bob - arm, 80, 120, col);
            g.tex(game.tex("boss_keyboard"), ox + 40, oy + 170 + bob, 160, 44, col);
            float hx = ox + 64, hy = oy - 18 + bob * 1.6f;
            g.tex(game.tex("boss_head"), hx, hy, 112, 92, col);
            t(face(), hx + 56, hy + 50, 26, PartyGfx.alpha(GREEN, a), true);
            if (e.hp < e.max * 0.35f && a > 0f) {
                for (int k = 0; k < 3; k++) {
                    float gy = hy + 16 + (tt * 90f + k * 21f) % 60f;
                    g.rect(hx + 16, gy, 80, 2, PartyGfx.alpha(GREEN, 0.35f * a));
                }
            }
            // the stolen cake on a stool beside him
            if (a > 0f) {
                g.rect(470, 214, 34, 6, PartyGfx.rgba(0.4f, 0.3f, 0.22f, a));
                g.rect(484, 220, 6, 22, PartyGfx.rgba(0.4f, 0.3f, 0.22f, a));
                PartyDeco.cake(g, 487, 214, 2, true, tt);
            }
        }
        // the enemy's health bar on a hit
        if (state.equals("slash") || dmgT > 0f) {
            float hx = 220f, hy = 18f;
            float shownHp = e.hp;
            if (state.equals("slash") && t < 0.5f) shownHp = e.hp + dmgVal;
            g.rect(hx, hy, 200, 12, PartyGfx.rgb(0.5f, 0.05f, 0.08f));
            g.rect(hx, hy, 200 * Math.max(0f, Math.min(1f, shownHp / e.max)), 12, MINT);
            if (state.equals("slash") && t > 0.4f) {
                t(dmgVal == 0 ? "МИМО" : String.valueOf(dmgVal), 320, 60 - Math.min(t - 0.4f, 0.3f) * 40f, 30,
                        dmgVal > 0 ? PartyGfx.rgb(1f, 0.3f, 0.3f) : PartyGfx.rgb(0.7f, 0.7f, 0.7f), true);
            }
        }
        // the strike: symbols of code fly into the enemy
        if (state.equals("slash") && t < 0.5f && dmgVal > 0) {
            String[] s = {"</>", "{}", "();", "=>", "[]", "&&"};
            for (int k = 0; k < 6; k++) t(s[k], 320 + (k - 2.5f) * 24f, 250 - t * 380f + k * 6f, 20, MINT, true);
        }
    }

    private void remember(float x, float y, int colour) {
        hatX = x;
        hatY = y;
        hatColour = colour;
        hatSeen = true;
    }

    private String face() {
        if (state.equals("slash") && t > 0.25f && dmgVal > 0) return "x_x";
        if (spareable) return (tt % 3f) > 0.2f ? "^_^" : "-_-";
        if (e.hp < e.max * 0.35f) return (tt % 0.5f) < 0.25f ? ">_<" : "x_<";
        if (state.equals("talk")) return (int) bubbleShown % 4 < 2 ? "o_o" : "O_O";
        if (state.equals("attack")) return (tt % 1f) < 0.5f ? ">:)" : ">:D";
        return (tt % 4f) > 0.15f ? "-_-" : "=_=";
    }

    private void drawBubble(String s) {
        PartyRect r = new PartyRect(452, 36, 176, 110);
        g.rect(r, PartyGfx.WHITE);
        g.poly(PartyGfx.WHITE, r.x, 76, r.x - 18, 88, r.x, 96);
        float y = r.y + 24f;
        for (String ln : g.wrap(s, r.w - 20f, 16)) {
            t(ln, r.x + 10, y, 16, PartyGfx.BLACK, false);
            y += 20f;
        }
    }

    private void drawBox() {
        PartyRect b = box;
        if (atk.equals("final") && state.equals("attack")) b = b.moved(game.randf(-2, 2), game.randf(-2, 2));
        g.rect(b, PartyGfx.BLACK);
        g.frame(b.grow(2.5f), PartyGfx.WHITE, 5f);
        switch (state) {
            case "menu":
            case "text": {
                float y = MENU_BOX.y + 38f, fs = 22, step = 32f;
                if (g.wrap("> " + text, 520, 22).size() > 3) {
                    fs = 17;
                    y = MENU_BOX.y + 32f;
                    step = 25f;
                }
                String s = text.substring(0, Math.min((int) shown, text.length()));
                if (!s.isEmpty()) {
                    for (String ln : g.wrap("> " + s, 530, fs)) {
                        t(ln, MENU_BOX.x + 26, y, fs, PartyGfx.WHITE, false);
                        y += step;
                    }
                }
                break;
            }
            case "target":
                soulIcon(MENU_BOX.x + 40, MENU_BOX.y + 32);
                t(e.name, MENU_BOX.x + 64, MENU_BOX.y + 46, 22, spareable ? MINT : PartyGfx.WHITE, false);
                g.rect(MENU_BOX.x + 260, MENU_BOX.y + 30, 120, 16, PartyGfx.rgb(0.5f, 0.05f, 0.08f));
                g.rect(MENU_BOX.x + 260, MENU_BOX.y + 30, 120f * e.hp / e.max, 16, MINT);
                break;
            case "acts":
            case "items":
            case "mercy": {
                List<String> l = list();
                boolean tight = l.size() > 6;
                for (int k = 0; k < l.size(); k++) {
                    float px = MENU_BOX.x + 64 + (k % 2) * 270, py = MENU_BOX.y + (tight ? 34 : 44) + (k / 2) * (tight ? 29 : 34);
                    int col = state.equals("mercy") && k == 0 && spareable ? MINT : PartyGfx.WHITE;
                    t(l.get(k), px, py, l.size() > 6 ? 19 : 22, col, false);
                    if (k == sub) soulIcon(px - 24, py - 14);
                }
                break;
            }
            case "aim": {
                PartyRect r = MENU_BOX.grow(-14);
                float c = r.cx();
                for (int k = 0; k < 12; k++) {
                    float w = r.w * 0.5f * (1f - k / 12f);
                    int col = PartyGfx.lerp(PartyGfx.rgb(1f, 0.35f, 0.3f), MINT, k / 11f);
                    col = PartyGfx.alpha(col, 0.18f + k * 0.03f);
                    g.rect(c - w, r.y + 10 + k * 2, w * 2, r.h - 20 - k * 4, col);
                }
                g.rect(c - 3, r.y + 4, 6, r.h - 8, PartyGfx.WHITE);
                g.rect(aimX - 4, MENU_BOX.y + 6, 8, MENU_BOX.h - 12, PartyGfx.WHITE);
                g.rect(aimX - 2, MENU_BOX.y + 8, 4, MENU_BOX.h - 16, PartyGfx.BLACK);
                break;
            }
        }
        if (state.equals("attack") || state.equals("lose")) {
            for (Hazard l : lasers) drawHazard(l);
            PartyRect inner = box.grow(4f);
            for (Bullet bl : bullets) {
                if (!inner.has(bl.p)) continue;
                PartyVec p = bl.p;
                if (bl.bug) {
                    g.tex(game.tex("bug_" + ((int) (tt * 8f) % 2)), p.x - 12, p.y - 9, 24, 18);
                } else if (bl.lab != null) {
                    PartyRect r = new PartyRect(p.x - bl.h.x, p.y - bl.h.y, bl.h.x * 2f, bl.h.y * 2f).intersection(box);
                    g.rect(r, PartyGfx.darkened(bl.c, 0.6f));
                    g.frame(r, bl.c, 2f);
                    if (bl.lab.equals("≡")) {
                        for (float yy = r.y + 4f; yy < r.ey() - 2f; yy += 5f) g.line(r.x + 3, yy, r.ex() - 3, yy, PartyGfx.rgb(0.6f, 0.6f, 0.6f), 1f);
                    } else {
                        String lab = bl.lab;
                        float fw = g.width(lab, 13);
                        float lx = r.x + 2f;
                        if (fw > r.w - 4f) lab = lab.substring(0, Math.max((int) ((r.w - 4f) / 8f), 0));
                        else lx = r.cx() - fw * 0.5f;
                        t(lab, lx, r.cy() + 5, 13, bl.c, false);
                    }
                } else if (bl.g.equals("i")) {
                    // a birthday candle: a stick and a flame
                    g.rect(p.x - 2.5f, p.y - 6, 5, 14, bl.c);
                    g.circle(p.x, p.y - 9, 3.2f, PartyGfx.rgb(1f, 0.7f, 0.2f));
                    g.circle(p.x, p.y - 10, 1.6f, PartyGfx.rgb(1f, 0.95f, 0.6f));
                } else {
                    t(bl.g, p.x, p.y + bl.fs * 0.35f, bl.fs, bl.c, true);
                }
            }
            if (state.equals("attack") && (invT <= 0f || (int) (invT * 16f) % 2 == 0)) {
                g.tex(game.tex(gravity ? "soul_build" : "soul"), soul.x - 8, soul.y - 8, 16, 16);
            }
            if (state.equals("attack") && gravity && t < 1.2f) t("ВВЕРХ = прыжок", box.cx(), box.y - 12, 14, ORANGE, true);
        }
    }

    /** A zone's warning and strike: a laser, a popup window, a ticket lane, a captcha cell, the ban hammer. */
    private void drawHazard(Hazard l) {
        PartyRect r = l.r;
        float w = l.w, en = l.e;
        boolean blink = (int) (l.t * 14f) % 2 == 0;
        switch (l.kind) {
            case "laser":
                if (l.t < w) {
                    if (blink) g.frame(r, PartyGfx.alpha(GREEN, 0.7f), 2f);
                    PartyVec src = l.h ? new PartyVec(r.x - 30, r.cy()) : new PartyVec(r.cx(), r.y - 26);
                    g.rect(src.x - 16, src.y - 12, 32, 24, PartyGfx.rgb(0.2f, 0.2f, 0.22f));
                    t(">_", src.x, src.y + 6, 14, GREEN, true);
                } else if (l.t < en) {
                    float k = 1f - (l.t - w) / (en - w);
                    float s = 13f * (1f - k);
                    PartyRect rr = l.h ? r.grow(0, -s, 0, -s) : r.grow(-s, 0, -s, 0);
                    g.rect(rr, PartyGfx.rgba(0.85f, 1f, 0.9f, 0.9f));
                    g.rect(rr.grow(-5), GREEN);
                }
                break;
            case "window":
                if (l.t < w) {
                    if (blink) g.frame(r, PartyGfx.rgba(1, 1, 1, 0.6f), 2f);
                } else if (l.t < en) {
                    g.rect(r, PartyGfx.rgb(0.9f, 0.91f, 0.94f));
                    g.rect(r.x, r.y, r.w, 10, PartyGfx.rgb(0.24f, 0.43f, 0.86f));
                    g.rect(r.ex() - 10, r.y + 1, 8, 8, PartyGfx.rgb(0.86f, 0.24f, 0.24f));
                    t("Принять?", r.cx(), r.cy() + 8, 11, PartyGfx.rgb(0.2f, 0.2f, 0.25f), true);
                }
                break;
            case "lane":
                if (l.t < w) {
                    g.rect(r, PartyGfx.rgba(1f, 0.95f, 0.7f, blink ? 0.18f : 0.08f));
                } else if (l.t < en) {
                    g.rect(r, PartyGfx.rgb(0.96f, 0.93f, 0.78f));
                    for (float yy = r.y + 8f + (l.t * 300f) % 24f; yy < r.ey(); yy += 24f) {
                        t("#4815", r.cx(), yy, 11, PartyGfx.rgb(0.6f, 0.3f, 0.25f), true);
                    }
                }
                break;
            case "cell":
                if (l.t < w) {
                    if (blink) g.frame(r, PartyGfx.rgba(1f, 0.85f, 0.3f, 0.8f), 3f);
                    g.rect(r.grow(-r.w * 0.35f), PartyGfx.rgba(1f, 0.85f, 0.3f, 0.25f));
                } else if (l.t < en) {
                    g.rect(r, PartyGfx.rgba(1f, 0.35f, 0.35f, 0.9f));
                    t("✕", r.cx(), r.cy() + 10, 30, PartyGfx.WHITE, true);
                }
                break;
            case "safe": {
                PartyRect[] outside = {new PartyRect(box.x, box.y, box.w, r.y - box.y), new PartyRect(box.x, r.ey(), box.w, box.ey() - r.ey()),
                        new PartyRect(box.x, r.y, r.x - box.x, r.h), new PartyRect(r.ex(), r.y, box.ex() - r.ex(), r.h)};
                if (l.t < w) {
                    for (PartyRect o : outside) g.rect(o, PartyGfx.rgba(1f, 0.3f, 0.3f, blink ? 0.12f : 0.05f));
                    g.rect(r, PartyGfx.alpha(GREEN, 0.25f));
                    g.frame(r, GREEN, 2f);
                    t("LGTM", r.cx(), r.cy() + 6, 14, GREEN, true);
                } else if (l.t < en) {
                    for (PartyRect o : outside) g.rect(o, PartyGfx.rgba(1f, 0.35f, 0.35f, 0.85f));
                    g.frame(r, GREEN, 3f);
                }
                break;
            }
            case "hammer": {
                Bitmap ht = game.tex("banhammer");
                if (l.t < w) {
                    g.rect(r, PartyGfx.rgba(1f, 0.3f, 0.3f, blink ? 0.16f : 0.06f));
                    g.tex(ht, r.cx() - 30, r.y - 70, 60, 100, PartyGfx.rgba(1, 1, 1, 0.8f));
                } else if (l.t < en) {
                    g.rect(r, PartyGfx.rgba(1f, 0.4f, 0.4f, 0.8f));
                    g.tex(ht, r.cx() - 30, r.ey() - 100, 60, 100);
                }
                break;
            }
        }
    }

    private void soulIcon(float x, float y) { g.tex(game.tex("soul"), x, y, 16, 16); }

    private void drawHud() {
        float y = 412f;
        t("НАРЕЗАННЫЙ", 34, y, 18, PartyGfx.WHITE, false);
        t("LV 1", 170, y, 18, PartyGfx.WHITE, false);
        t("HP", 240, y, 16, PartyGfx.WHITE, false);
        float scale = Math.min(2.6f, 200f / game.maxHp);
        g.rect(270, y - 15, game.maxHp * scale, 18, PartyGfx.rgb(0.55f, 0.05f, 0.1f));
        g.rect(270, y - 15, game.hp * scale, 18, MINT);
        t(game.hp + " / " + game.maxHp, 270 + game.maxHp * scale + 12, y, 18, PartyGfx.WHITE, false);
        boolean menuish = Arrays.asList("menu", "target", "acts", "items", "mercy", "aim").contains(state);
        for (int k = 0; k < 4; k++) {
            PartyRect r = new PartyRect(32 + k * 150, 428, 128, 40);
            boolean on = k == btn && menuish;
            int col = on ? MINT : PartyGfx.rgb(0.75f, 0.75f, 0.78f);
            if (k == 3 && spareable && !on) col = PartyGfx.rgb(0.55f, 0.8f, 0.7f);
            g.frame(r, col, 3f);
            if (on && state.equals("menu")) soulIcon(r.x + 10, r.y + 12);
            else t(ICONS[k], r.x + (ICONS[k].length() < 3 ? 18 : 12), r.y + 27, 16, col, false);
            t(BUTTONS[k], r.x + 48, r.y + 27, 18, col, false);
        }
    }

    @SuppressWarnings("unused")
    private static <T> void drop(Iterator<T> it) { it.remove(); }
}

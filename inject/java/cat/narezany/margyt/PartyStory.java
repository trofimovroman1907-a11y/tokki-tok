package cat.narezany.margyt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The plot: talking to everything, scenes on entering rooms, puzzles, the pages of Селфкодинг's diary, the party. */
final class PartyStory {
    private PartyStory() {}

    private static final Object[][] QUIZ = {
            {"Чему равно 0.1 + 0.2 в JavaScript?", new String[]{"0.3", "0.30000000000000004"}, 1},
            {"Где тестируют код?", new String[]{"В проде", "В тестах"}, 1},
            {"Сколько программистов нужно, чтобы поменять лампочку?", new String[]{"Ни одного", "Двое"}, 0},
    };

    private static final Map<Integer, String[]> DIARY = new HashMap<>();
    private static final Map<String, String> FACES = new HashMap<>();

    static {
        DIARY.put(1, new String[]{"Вырванная страница. Почерк аккуратный, как отступы в коде.",
                "«Сегодня написал первый Hello, world. Сам. Руками.",
                "Мама сказала: молодец. Буду всю жизнь писать всё сам»."});
        DIARY.put(2, new String[]{"Ещё одна страница. Чернила местами расплылись.",
                "«Мой код в проде двадцать лет. Никто не знает, как он работает. Даже я.",
                "Пришли нейросети. Теперь за минуту пишут то, что я писал год. И никто больше не читает мой код»."});
        DIARY.put(3, new String[]{"Последняя страница. Буквы прыгают.",
                "«Написал жалобу на Нарезанного. Пусть хоть день попишет сам. Даже если у него день рождения.",
                "Мне на день рождения никто никогда не приходил. Может, тогда хоть кто-то... посидит со мной рядом»."});
        FACES.put("Бабушка Зина", "npc_grandma");
        FACES.put("Вася", "npc_kid");
        FACES.put("Бариста", "npc_barista");
        FACES.put("Медведь", "npc_banned1");
        FACES.put("Лиса", "npc_banned2");
        FACES.put("Оператор", "npc_operator");
        FACES.put("Курьер", "npc_courier");
        FACES.put("Охранник Гена", "npc_guard");
        FACES.put("Модератор", "npc_owl");
        FACES.put("Мизуня", "npc_mizunya");
        FACES.put("Горилла", "npc_gorilla");
    }

    static PartyText.Line N(String text, String face) {
        return new PartyText.Line(text).face(face).voice("narez").name("Нарезанный");
    }

    static PartyText.Line N(String text) { return N(text, "normal"); }

    /** Селфкодинг: a monitor for a face, moods normal, angry, sad, happy. */
    static PartyText.Line B(String text, String mood) {
        return new PartyText.Line(text).face("boss_" + mood).voice("boss").name("Селфкодинг");
    }

    static PartyText.Line B(String text) { return B(text, "normal"); }

    /** A neighbour: face on the left, name on the plate. */
    static PartyText.Line P(String who, String text) {
        String face = FACES.get(who);
        return new PartyText.Line(text).face(face == null ? "npc_operator" : face).voice("cat").name(who);
    }

    private static PartyText.Line sys(String who, String text) {
        return new PartyText.Line(text).voice("sys").name(who).plain();
    }

    private static PartyText.Line ask(String text, String... choices) {
        return new PartyText.Line(text).choices(choices);
    }

    /** The cooling room: A and B cool, C warms. */
    static int temperature(PartyGame g) {
        return 30 - (g.flag("valve_a") ? 5 : 0) - (g.flag("valve_b") ? 7 : 0) + (g.flag("valve_c") ? 6 : 0);
    }

    private static boolean give(PartyWorld w, String item) {
        if (w.game.items.size() >= PartyGame.MAX_ITEMS) return false;
        w.game.items.add(item);
        w.game.sfx.play("item");
        return true;
    }

    private static List<PartyText.Line> L(Object... lines) { return PartyText.of(lines); }

    private static String pick(PartyWorld w, String... s) { return s[w.game.randi(s.length)]; }

    // ------------------------------------------------------------ entering a room

    static void onEnter(PartyWorld w, String room) {
        PartyGame g = w.game;
        Map<String, Object> f = g.flags;
        switch (room) {
            case "bedroom":
                if (!g.flag("woke")) {
                    f.put("woke", true);
                    w.say(N("Утро. Сегодня мой день рождения!", "happy"), N("Попрошу Claude доделать праздничную игру для гостей и спать до вечера.", "happy"),
                            "Компьютер мигает красным.");
                }
                break;
            case "yard":
                if (!g.flag("yard_seen")) {
                    f.put("yard_seen", true);
                    w.say(N("Свежий воздух. Почти забыл, как он пахнет."), N("Так. Поддержка где-то за кофейней, у бизнес-центра."),
                            N("Успеть бы до вечера. Гости в семь.", "smug"));
                }
                break;
            case "lobby":
                if (!g.flag("lobby_seen")) {
                    f.put("lobby_seen", true);
                    w.say("В холле поддержки пахнет кофе из автомата и отчаянием.", N("Очередь... Ну конечно. В мой день рождения.", "angry"));
                }
                break;
            case "office":
                if (!g.flag("moderator_done")) {
                    w.locked = true;
                    w.say(L(P("Модератор", "Нарезанный. Пришли обжаловать бан?"), N("Да! Я ничего не нарушал! У меня сегодня день рождения!", "angry"),
                            P("Модератор", "Поздравляю. Пункт 7.3: «вайбкодинг в особо крупных размерах»."),
                            P("Модератор", "Жалоба от уважаемого пользователя. Обжалованию не подлежит. Даже в день рождения."),
                            N("Это мы ещё посмотрим.", "smug")), () -> {
                        w.locked = false;
                        w.encounter("moderator");
                    });
                } else if (!g.flag("pass")) {
                    afterModerator(w);
                }
                break;
            case "dc_gate":
                if (!g.flag("gate_seen")) {
                    f.put("gate_seen", true);
                    w.say("Вечереет. Дата-центр на окраине гудит, как улей.", N("Он где-то там. SELF_CODING. И, кажется, мой торт тоже."));
                }
                break;
            case "corridor":
                if (!g.flag("dc_seen")) {
                    f.put("dc_seen", true);
                    w.say("Внутри тепло и шумно. Сотни серверов думают о своём.", N("Тут бы и Claude жил, если бы его не забанили... меня."));
                }
                break;
            case "cooling":
                if (!g.flag("cool_seen")) {
                    f.put("cool_seen", true);
                    w.say("Холодильная. Вентиляторы воют. Табло на стене горит красным: перегрев.");
                }
                break;
            case "party":
                if (!g.flag("party_seen")) {
                    f.put("party_seen", true);
                    f.put("hat_on", true);
                    w.confetti.burst(320, 200, 90);
                    g.sfx.play("pop");
                    boolean spare = "spare".equals(f.get("ending"));
                    if (spare) {
                        w.say(L("Вечер. Дома полно гостей. Кто-то уже надел на тебя колпак.", P("Бабушка Зина", "С днём рождения, внучок!"),
                                P("Вася", "С днюхой, Нарезанный!!"), B("С днём... рождения.", "happy"),
                                N("Ты пришёл!", "happy"), B("Я испёк торт. Сам. Руками. Первый раз в жизни.", "happy"),
                                B("Рецепт не гуглил. Ну, почти.", "normal"),
                                "Торт стоит на столе. Свечи горят. Подойди и задуй их."), null);
                    } else {
                        w.say(L("Вечер. Дома полно гостей. Кто-то уже надел на тебя колпак.", P("Бабушка Зина", "С днём рождения, внучок!"),
                                P("Вася", "С днюхой, Нарезанный!!"), "Торт стоит на столе. Свечи горят.",
                                N("Чего-то не хватает. Ладно.", "normal"), "Подойди к торту и задуй свечи."), null);
                    }
                }
                break;
        }
    }

    private static void afterModerator(PartyWorld w) {
        PartyGame g = w.game;
        g.flags.put("pass", true);
        g.sfx.play("item");
        if (g.flag("moderator_kill")) {
            w.say("Модератора нет. На столе лежит пропуск и дело с жалобой.", "Заявитель: SELF_CODING. Адрес: дата-центр на окраине.",
                    "Ты берёшь пропуск. Служебный выход справа.");
        } else {
            w.say(P("Модератор", "Апелляцию я одобрил. Но снять бан может только заявитель."),
                    P("Модератор", "SELF_CODING сидит в дата-центре на окраине. Третью неделю не выходит."),
                    P("Модератор", "Вот служебный пропуск. Выход справа. И... с днём рождения. Пункт 1.1."), "Ты получаешь служебный пропуск.");
        }
    }

    // ------------------------------------------------------------ triggers

    static void trigger(PartyWorld w, String what) {
        PartyGame g = w.game;
        switch (what) {
            case "bug":
                if (g.flag("bug_done")) return;
                w.locked = true;
                g.sfx.play("warn");
                w.say(L("Из-под серверной стойки выползает БАГ!"), () -> {
                    w.locked = false;
                    w.encounter("bug");
                });
                break;
            case "captcha":
                if (g.flag("captcha_done") || w.locked) return;
                w.locked = true;
                g.sfx.play("warn");
                w.say(L("Турникет пищит. На экране загружается что-то с глазом.", N("Только не это.", "shock")), () -> {
                    w.locked = false;
                    w.encounter("captcha");
                });
                break;
            case "boss": {
                if (w.locked) return;
                w.locked = true;
                g.playMusic("");
                List<PartyText.Line> lines = L(B("Ну наконец-то."), B("Я Селфкодинг. Тот, кто пишет код сам."),
                        B("Пока вы жали Enter, я печатал. Строчку за строчкой."),
                        B("Это я написал на тебя жалобу. Чтобы ты вспомнил, как это делается.", "angry"),
                        N("Так это ТЫ меня забанил?! В мой день рождения?!", "shock"), N("И это мой торт рядом с тобой!", "angry"),
                        B("Курьер ошибся адресом. Я не просил.", "sad"), B("Дни рождения... Ха. Я пережил их сорок. Один.", "happy"));
                if (g.num("diary") >= 3) {
                    lines.add(N("Я читал твой дневник."));
                    lines.add(B("...Что? Это... неважно.", "sad"));
                }
                lines.add(B("Покажи, чего ты стоишь без подсказок.", "angry"));
                w.say(lines, () -> w.encounter("boss"));
                break;
            }
        }
    }

    /** After the boss: the evening, at home, with everybody. */
    static void toParty(PartyGame game) {
        game.room = "party";
        game.spawn = new PartyVec(60, 300);
        game.facing = "right";
        game.saveGame();
        game.startWorld();
    }

    // ------------------------------------------------------------ talking

    static void talk(PartyWorld w, String what) {
        PartyGame g = w.game;
        Map<String, Object> f = g.flags;
        switch (what) {
            // --- home ---
            case "pc":
                f.put("pc_seen", true);
                w.say("На мониторе письмо: «Your account has been suspended.»", N("Меня... забанили?! В день рождения?!", "shock"),
                        N("Без нейросетки я даже открытку гостям не сверстаю.", "angry"),
                        N("Ладно. До вечера ещё куча времени. Пойду в поддержку и разберусь, кто это устроил."));
                break;
            case "bed":
                w.say("Твоя кровать. Одеяло мятного цвета.", "Ты бы поспал, но гости сами себя не встретят.");
                break;
            case "cat":
                w.say(L("Толстый тилкайо в праздничном колпаке смотрит на тебя с осуждением.", ask("Погладить?", "Да", "Нет")), () -> {
                    if (g.box.lastChoice == 0) {
                        g.hp = g.maxHp;
                        g.sfx.play("heal");
                        w.say("Тилкайо мурчит. Здоровье восстановлено.", N("Ты лучший, пушистый. Колпак тебе идёт.", "happy"));
                    } else {
                        w.say("Тилкайо обиделся. Немного. Колпак съехал набок.");
                    }
                });
                break;
            case "poster":
                w.say("Плакат: «MARGY. Сделано с любовью и нейросеткой».");
                break;
            case "window":
                w.say("За окном рассвет. Где-то далеко гудят дата-центры.");
                break;
            case "balloons":
                w.say("Шарики. Вчера надувал сам. Руками. Ну, ртом.", N("Чуть не умер на десятом.", "smug"));
                break;
            case "garland":
                w.say("Растяжка «С ДНЁМ РОЖДЕНИЯ». Буква «Я» висит вверх ногами.", N("Сойдёт. Гости не заметят.", "smug"));
                break;
            case "fridge":
                if (!g.flag("fridge")) {
                    f.put("fridge", true);
                    if (give(w, "Тако")) w.say("В холодильнике тако. И кетчуп с 2019 года. Места для торта нет.", "Ты берёшь тако. Кетчуп оставляешь истории.");
                    else w.say("В холодильнике тако. Но карманы полны.");
                } else {
                    w.say("В холодильнике только кетчуп. Он смотрит на тебя. Ты на него.");
                }
                break;
            case "stove":
                w.say("Плита. Последний раз её включали, когда Claude сказал «приготовь сам».");
                break;
            case "note":
                w.say("Записка: «Купить корм тилкайо. Забрать торт у курьера в 18:00».", "Это твой почерк. Писал сам. Руками.", N("...Ну, бывает.", "smug"));
                break;
            case "mirror":
                w.say("В зеркале тилкайо в мятном худи.", N("Красавчик. Даже забаненный. Даже на год старше.", "smug"));
                break;
            case "shoes":
                w.say("Кроссовки, тапки и мятные кеды. Ты выбираешь кеды. Ты всегда выбираешь кеды.");
                break;
            // --- the yard ---
            case "grandma":
                if (!g.flag("grandma")) {
                    f.put("grandma", true);
                    boolean got = give(w, "Пирожок");
                    w.say(P("Бабушка Зина", "Внучок, это ты тот, который в интернете котом ходит?"),
                            P("Бабушка Зина", "С днём рождения тебя! Я и колпак надела, видишь?"),
                            P("Бабушка Зина", "В наше время код на перфокартах писали. Дырочки руками колупали!"),
                            P("Бабушка Зина", "Говорят, тебя там забанили. Вот, возьми пирожок, с капустой."),
                            got ? "Ты получаешь пирожок." : "Пирожок не влезает в карманы. Бабушка расстроена.");
                } else {
                    w.say(P("Бабушка Зина", pick(w, "Кушай, кушай. Худенький какой.", "Вечером приду, торт попробую.", "Ты заходи потом, расскажешь, как там твой Клод.")));
                }
                break;
            case "kid":
                f.put("met_kid", true);
                w.refresh();
                w.say(P("Вася", "Здарова, Нарезанный! С днюхой! Я твой подписчик!"), P("Вася", "У меня Claude тоже не отвечает. Пишу код на телефоне сам. Медленно."),
                        P("Вася", "Осторожно, по району летает спам и попапы. Если кто нападёт:"),
                        P("Вася", "КОД бьёт, ПРОМПТ даёт поговорить. Если имя врага стало мятным, жми ПОЩАДА."),
                        P("Вася", "Зелёные «// TODO» лечат! А на C меню с вещами."),
                        P("Вася", "Говорят, в поддержке знают, кто на тебя жалобу написал."));
                break;
            case "mizunya":
                if (!g.flag("mizunya")) {
                    g.flags.put("mizunya", true);
                    boolean got = give(w, "Конфета");
                    w.say(P("Мизуня", "Мфф! Мф-мфмф мф мфф-мфмф!"), "Мизуня говорит сквозь капюшон. Кажется, это было «С днём рождения!».",
                            P("Мизуня", "Мфмф мф мфф мфмф мф-мф. Мфф!"), N("Ты очень хотел попасть ко мне в игру?", "happy"),
                            P("Мизуня", "МФФ!"), "Мизуня радостно кивает всем капюшоном.",
                            got ? "Мизуня даёт тебе конфету." : "Мизуня хочет дать тебе конфету, но карманы полны. Он грустно мфкает.");
                } else {
                    w.say(P("Мизуня", pick(w, "Мфф.", "Мф-мф мфмф?", "Мфмфмф! Мф!")), pick(w, "Мизуня поправляет капюшон.", "Мизуня машет варежкой.", "Кажется, Мизуня одобряет."));
                }
                break;
            case "courier":
                w.say(P("Курьер", "Заказ на имя SELF_CODING. Сорок банок энергетика и новая клавиатура."),
                        P("Курьер", "А ещё торт. Большой, со свечками. На имя Нарезанного. Но адрес почему-то тоже дата-центр."),
                        N("Что?! Мой торт?!", "shock"), P("Курьер", "Меня туда не пускают. Говорят, там всё сам принимает какой-то SELF_CODING."));
                break;
            case "sign_yard":
                w.say("Указатель: «← Дом. Кофейня. Бизнес-центр с поддержкой →».");
                break;
            case "bench":
                w.say("Скамейка. На ней кто-то вырезал «вайбкодеры — не программисты».");
                break;
            // --- the café ---
            case "barista":
                w.say(L(P("Бариста", "Привет! С днём рождения! Мятный латте для мятного худи? Сегодня ещё и торт есть.")),
                        () -> w.openShop(java.util.Arrays.asList(new Object[]{"Латте", 8}, new Object[]{"Круассан", 14},
                                new Object[]{"Энергетик", 20}, new Object[]{"Торт", 25})));
                break;
            case "bear":
                w.say(P("Медведь", "Меня тоже забанили. Попросил нейросеть написать мне диплом."),
                        P("Медведь", "Сам? Не, я не умею. Я гуманитарий. Колпак мне бариста выдала."),
                        P("Медведь", "Тут один хмурый тип в капюшоне каждый день сидел. Писал в тетрадку. Потом вырвал страницу и ушёл."));
                break;
            case "cafe_table":
                w.say("Столик. Ноутбук медведя. На экране: «Как написать диплом самому (без регистрации)».");
                break;
            // --- the street by the business centre ---
            case "gorilla":
                w.say(P("Горилла", "У-у-а-а! У-а-а!"), N("Едрена мать"));
                break;
            case "smoker":
                w.say(P("Оператор", "Я из поддержки. Перекур. Седьмой за час."),
                        P("Оператор", "Твоя жалоба? Помню. Подписал какой-то SELF_CODING. Писал капсом и без единой опечатки."),
                        P("Оператор", "Только через турникет не пройдёшь, там капча. Её даже мы проходим с третьего раза."));
                break;
            case "billboard":
                w.say("Реклама: «ПУСТЬ ИИ СДЕЛАЕТ ВСЁ ЗА ТЕБЯ!» Мелким шрифтом: «кроме того, что не сделает».");
                break;
            case "turnstile":
                w.say(g.flag("captcha_done") ? "Турникет горит мятным. Проходи." : "Турникет. На экране: «Докажите, что вы не робот».");
                break;
            // --- support ---
            case "reception":
                w.say(sys("Робот-ресепшн", "Возьмите талон. Ваш номер: 4815162342. Сейчас обслуживается: 7."),
                        sys("Робот-ресепшн", "Среднее время ожидания: да."), N("Пойду сам. Через опенспейс.", "smug"));
                break;
            case "cooler":
                w.say("Кулер булькает. Единственный, кто здесь работает.");
                break;
            case "chairs":
                w.say("Стулья для очереди. На одном выцарапано: «я тут с 2024».");
                break;
            case "fox":
                w.say(P("Лиса", "Ты тоже в очереди? Я тут с прошлого года."), P("Лиса", "Меня забанили за слово «хакнуть» в стихах про любовь."),
                        P("Лиса", "У тебя день рождения? Колпак у меня свой, всегда ношу. На всякий случай."),
                        P("Лиса", "Если идёшь к Модератору, помни: он уважает только правила. Особенно те, что он забыл."));
                break;
            case "op1":
                w.say(P("Оператор", "Zzz... тикет... закрыт как дубликат... zzz..."));
                break;
            case "op2":
                w.say(P("Оператор", "Хочешь ответ? У нас их три: «Спасибо за обращение», «Мы уже работаем» и «Решено»."), P("Оператор", "Выбирай любой."));
                break;
            case "op3":
                w.say(P("Оператор", "Модератор у себя. Он сегодня злой."), P("Оператор", "Он всегда злой. Но у него есть пункт 1.1. Он про него забыл."));
                break;
            case "op_desk":
                w.say("На столе кружка «ЛУЧШИЙ ОПЕРАТОР 2017» и кактус. Кактус живее всех.");
                break;
            case "case":
                f.put("case_read", true);
                w.say("Терминал с делами. Ты находишь своё.", "«Дело №SUS-2026. Пользователь: Нарезанный.",
                        "Причина: вайбкодинг в особо крупных размерах. Дата: 24 ноября. Заявитель: SELF_CODING».",
                        N("SELF_CODING... Кто ты вообще такой?", "angry"));
                break;
            case "mod_desk":
                if (g.flag("pass")) w.say("Стол Модератора. Регламент открыт на пункте 1.1. Кто-то обвёл его ручкой.");
                else afterModerator(w);
                break;
            case "owl":
                w.say(P("Модератор", "Дата-центр на окраине. Выход справа. Я занят: перечитываю пункт 1.1."));
                break;
            case "binders":
                w.say("Регламенты. «Правила», «Правила к правилам», «Исключения из правил к правилам».");
                break;
            // --- the data centre ---
            case "guard":
                if (g.flag("gate_open")) {
                    w.say(P("Охранник Гена", "Проходи. Только тихо. Там кто-то третью неделю печатает."));
                } else if (g.flag("pass")) {
                    f.put("gate_open", true);
                    w.refresh();
                    g.sfx.play("door");
                    w.say(P("Охранник Гена", "Пропуск? ...Служебный. Ну проходи."), "Шлагбаум поднимается.",
                            P("Охранник Гена", "Он там в капюшоне сидит. Монитор вместо лица. Энергетики ему таскают."),
                            P("Охранник Гена", "Сегодня ещё торт привезли. Он его не ест. Смотрит на свечки и молчит."));
                } else {
                    w.say(P("Охранник Гена", "Без пропуска не пущу."));
                }
                break;
            case "courier2":
                w.say(P("Курьер", "Пустили! Энергетики доставлены."), P("Курьер", "Он даже спасибо сказал. Тихо так. Будто давно ни с кем не говорил."));
                break;
            case "gate":
            case "booth":
                w.say(g.flag("gate_open") ? "Шлагбаум поднят." : "Шлагбаум.");
                break;
            case "sign":
                w.say("Табличка: «Серверная. Вход только тем, кто знает, что такое SSH».");
                break;
            case "bot":
                w.say(sys("Робот-ревьюер", "Б-бип. Я робот-ревьюер. Был."), "Без связи с нейросетью я умею только писать «LGTM».",
                        "Если лапка станет оранжевой, она падает вниз. Прыгай стрелкой вверх.", "Хозяин этого места не делает ревью. Ему некому делать ревью.", "LGTM.");
                break;
            case "rack":
                w.say("Сервер мигает лампочками. Похоже, он тоже не понимает, что происходит.");
                break;
            case "valve_a":
            case "valve_b":
            case "valve_c": {
                f.put(what, !g.flag(what));
                g.sfx.play("door");
                int temp = temperature(g);
                if (temp == 18 && !g.flag("cool")) {
                    f.put("cool", true);
                    w.say("Ты крутишь вентиль. Табло: " + temp + " °C.", "Вентиляторы успокаиваются. Дверь справа щёлкает.", N("Восемнадцать. Как в записке.", "smug"));
                } else {
                    if (temp != 18) f.remove("cool");
                    w.say("Ты крутишь вентиль. Табло: " + temp + " °C.");
                }
                break;
            }
            case "thermo":
                w.say("Табло: " + temperature(g) + " °C. Нужно 18.");
                break;
            case "cool_note":
                w.say("Записка: «Серверам нужно ровно 18 °C. Вентиль C не трогать: он греет. Всё сам настраивал. С.»");
                break;
            case "trash":
                if (g.flag("trash")) {
                    w.say("Пустая мусорка. Только фантик от «Сникерса».");
                } else if (give(w, "Сосиска")) {
                    f.put("trash", true);
                    w.say("В мусорке лежит ещё тёплая сосиска.", "Ты берёшь её. Не спрашивай.");
                } else {
                    w.say("В мусорке лежит ещё тёплая сосиска.", "Но карманы полны.");
                }
                break;
            case "papers":
                w.say("Распечатки кода. Комментарии на трёх языках, и ни один не английский.");
                break;
            case "sign_legacy":
                w.say("Табличка: «ЛЕГАСИ. Работает? Не трогай».");
                break;
            case "door":
                w.say(g.flag("door_open") ? "Дверь открыта. Из-за неё слышно, как кто-то печатает." : "Дверь заперта. На ней написано: «Ответь терминалу».");
                break;
            case "terminal":
                if (g.flag("door_open")) {
                    w.say("Терминал: «Доступ уже открыт. Иди уже».");
                } else {
                    w.quiz = 0;
                    quiz(w);
                }
                break;
            case "mon1":
                w.say("На мониторе: git commit -m \"fix\"");
                break;
            case "mon2":
                w.say("На мониторе: git commit -m \"fix2\"");
                break;
            case "mon3":
                w.say("На мониторе: git commit -m \"ОКОНЧАТЕЛЬНО fix\"", "Тебе становится не по себе.");
                break;
            // --- anywhere ---
            case "disk":
                w.saveHere();
                w.say("Дискета «бэкап_финал_точно_финал.zip».", "Ты сохраняешься. Здоровье восстановлено.");
                break;
            case "diary": {
                int n = w.def.diaryN;
                f.put("diary_" + n, true);
                f.put("diary", g.num("diary") + 1);
                g.sfx.play("item");
                List<PartyText.Line> lines = L((Object[]) DIARY.get(n));
                lines.add(new PartyText.Line("Страниц дневника найдено: " + g.num("diary") + " из 3."));
                w.say(lines, null);
                break;
            }
            case "charlie":
                w.say("На стене висит скриншот в золотой рамочке.", "Табличка: «Музей хейта. Экспонат №1. Чарли, 12 ноября, 22:11».",
                        N("Я сделал хайп игру. А он сделал... совет.", "smug"));
                break;
            case "headwall":
                w.say(L(ask("Голая стена. Удариться об неё головой?", "Да", "Нет")), () -> {
                    if (g.box.lastChoice != 0) {
                        w.say("Ты решаешь не слушать Чарли. Мудро.");
                        return;
                    }
                    f.put("headbang", g.num("headbang") + 1);
                    g.hp = Math.max(g.hp - 1, 1);
                    g.sfx.play("hurt");
                    if (g.num("headbang") == 5 && give(w, "Сосиска")) {
                        w.say("Ты бьёшься головой об стену. Несколько раз.", "Из стены выпадает сосиска. Чарли был прав?");
                    } else {
                        w.say("Ты бьёшься головой об стену. -1 HP.", g.num("headbang") > 1 ? "Несколько раз." : "Чарли бы одобрил.");
                    }
                });
                break;
            default:
                party(w, what);
        }
    }

    // ------------------------------------------------------------ the party

    private static void party(PartyWorld w, String what) {
        PartyGame g = w.game;
        boolean spare = "spare".equals(g.flags.get("ending"));
        switch (what) {
            case "cake":
                if (g.flag("cake_done")) return;
                w.say(L(spare ? "Торт Селфкодинга. Кривой, зато сам. На нём кремом написано: «print(\"с др\")»."
                                : "Торт. Свечи горят. На нём кремом: «С ДР, НАРЕЗАННЫЙ».",
                        ask("Загадать желание и задуть свечи?", "Задуть", "Ещё нет")), () -> {
                    if (g.box.lastChoice != 0) return;
                    g.flags.put("cake_done", true);
                    g.sfx.play("pop");
                    w.confetti.burst(480 - w.cam, 250, 160);
                    w.confetti.rate = 20f;
                    w.say(L("Ты набираешь воздуха... Фух!", "Свечи гаснут. Гости хлопают.",
                            spare ? B("Ура. Это... правда приятно. Писать код вместе. И праздновать.", "happy")
                                    : P("Бабушка Зина", "Загадал? Не говори, а то не сбудется!"),
                            N("Спасибо, что пришли. Все. Это лучший день рождения.", "happy")), () -> g.showScreen("finale", null));
                });
                break;
            case "p_fridge":
                w.say("В холодильнике газировка и ещё один торт. Запасной. Бабушка Зина подстраховалась.");
                break;
            case "p_gifts":
                w.say("Гора подарков. Самый большой подписан: «От подписчиков MargyT». Внутри... ещё один колпак.", N("Ну, логично.", "smug"));
                break;
            case "p_banner":
                w.say("Растяжка «С ДНЁМ РОЖДЕНИЯ, НАРЕЗАННЫЙ!». Все буквы на месте. Её вешал Вася.");
                break;
            case "p_grandma":
                w.say(P("Бабушка Зина", "Пирожков на весь подъезд напекла. Кушай, именинник!"), P("Бабушка Зина", "И чтоб без нейросетей в этот год! Хотя бы по праздникам."));
                break;
            case "p_kid":
                w.say(P("Вася", "Я тебе открытку сам сделал! В фотошопе. Без нейросети. Пять часов."), P("Вася", "Ну, почти без."));
                break;
            case "p_barista":
                w.say(P("Бариста", "Принесла латте на всех. Мятный, конечно."), P("Бариста", "С тебя отзыв на карте. Пять звёзд."));
                break;
            case "p_bear":
                w.say(P("Медведь", "Я дописал диплом! Сам! Ну, бабушка Зина помогала."), P("Медведь", "Тема: «Перфокарты в эпоху нейросетей». Пятёрка."));
                break;
            case "p_fox":
                w.say(P("Лиса", "Я написала тебе стихи. Без слова «хакнуть»."), P("Лиса", "«Нарезанный, ты мятный и родной, пусть код твой будет вечно рабочий»."), P("Лиса", "Рифму доработаю."));
                break;
            case "p_guard":
                w.say(P("Охранник Гена", "Я с работы отпросился. Первый раз за двенадцать лет."), P("Охранник Гена", "Шлагбаум пока сам себя охраняет."));
                break;
            case "p_courier":
                w.say(P("Курьер", "Торт доставлен по правильному адресу! Пусть и с опозданием."), P("Курьер", "Ставь пять звёзд, именинник."));
                break;
            case "p_owl":
                w.say(P("Модератор", "Пункт 1.1: «Пользователь не должен страдать». Особенно в день рождения."), P("Модератор", "Это я дописал сегодня. Сам. Руками."));
                break;
            case "p_smoker":
                w.say(P("Оператор", "Твой тикет закрыт. Статус: «Решено. С днём рождения»."), P("Оператор", "Первый тикет, который я закрыл с радостью."));
                break;
            case "p_mizunya":
                w.say(P("Мизуня", "Мфф-мфф-мфф!"), "Мизуня дарит тебе брелок с толстым тилкайо.",
                        "Его невозможно держать нормально. Он не слушается.", N("Аккуратно возьми за ухо и не психуй...", "smug"));
                break;
            case "p_cat":
                w.say("Тилкайо ест крем с краешка торта. В колпаке. Он счастлив.", N("Эй! Ладно, тебе можно.", "happy"));
                break;
            case "p_boss":
                w.say(B("Сорок лет я не ходил на дни рождения. Даже на свои.", "sad"), B("Тут шумно. И тепло. И никто не спорит про табы и пробелы.", "happy"),
                        B("...Хотя табы, конечно, лучше.", "normal"));
                break;
        }
    }

    private static void quiz(PartyWorld w) {
        PartyGame g = w.game;
        Object[] q = QUIZ[w.quiz];
        w.say(L(ask("Терминал: «" + q[0] + "»", (String[]) q[1])), () -> {
            if (g.box.lastChoice == (Integer) q[2]) {
                w.quiz++;
                if (w.quiz >= QUIZ.length) {
                    g.flags.put("door_open", true);
                    w.refresh();
                    g.sfx.play("door");
                    w.say("Терминал: «Верно. Это была аппаратная проблема».", "Где-то наверху щёлкнул замок.", N("Ну вот. Без всякого Claude.", "smug"));
                } else {
                    g.sfx.play("ok");
                    quiz(w);
                }
            } else {
                g.hp = Math.max(g.hp - 3, 1);
                g.sfx.play("hurt");
                w.say("Терминал бьёт тебя током. -3 HP.", "Терминал: «Неверно. Хотя Селфкодинг бы с тобой согласился».");
            }
        });
    }
}

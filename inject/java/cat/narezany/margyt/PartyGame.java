package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.media.MediaPlayer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * «Нарезанный против Селфкодинга», birthday edition: the game's state, its screens, saving, sprites, music.
 * Ported from the Godot game in ~/Selfcoding; everything is drawn in a 640x480 frame.
 */
final class PartyGame {
    interface Scene {
        void update(float dt);
        void draw(PartyGfx g);
    }

    static final String ASSETS = "margyt/party/";
    static final int MAX_ITEMS = 8;
    static final int MINT = PartyGfx.rgb(0.44f, 0.87f, 0.72f);
    static final int GREEN = PartyGfx.rgb(0.35f, 1.0f, 0.55f);
    static final int GOLD = PartyGfx.rgb(1.0f, 0.82f, 0.36f);
    private static final String PREFS = "margyt_party";

    /** Food: how much it heals and what it says. */
    static final Map<String, Object[]> ITEM_DATA = new HashMap<>();

    static {
        ITEM_DATA.put("Тако", new Object[]{12, "Ты ешь тако. Хрустит. +12 HP."});
        ITEM_DATA.put("Арбуз", new Object[]{20, "Ты ешь арбуз. Целиком. +20 HP."});
        ITEM_DATA.put("Сосиска", new Object[]{8, "Ты ешь сосиску из мусорки. Не думай об этом. +8 HP."});
        ITEM_DATA.put("Пирожок", new Object[]{15, "Бабушкин пирожок с капустой. Тепло на душе. +15 HP."});
        ITEM_DATA.put("Латте", new Object[]{15, "Мятный латте. Бодрит. +15 HP."});
        ITEM_DATA.put("Круассан", new Object[]{24, "Круассан хрустит на весь мир. +24 HP."});
        ITEM_DATA.put("Энергетик", new Object[]{30, "Энергетик. Сердце стучит в такт клавиатуре. +30 HP."});
        ITEM_DATA.put("Торт", new Object[]{40, "Кусок праздничного торта. Со свечкой. Свечку не ешь. +40 HP."});
        ITEM_DATA.put("Конфета", new Object[]{10, "Конфета из праздничного пакетика. +10 HP."});
    }

    final Context context;
    final Typeface font;
    final PartySfx sfx = new PartySfx();
    final PartyInput input = new PartyInput();
    final PartyText box;
    final Random rng = new Random();
    final Map<String, Bitmap> tex = new HashMap<>();
    /** The activity: what closes the game, what fetches the badge. */
    final Host host;

    interface Host {
        void close();
        void claimBadge(java.util.function.Consumer<String> answer);
    }

    private MediaPlayer music;
    private String musicName = "";
    private boolean paused;
    Scene scene;
    long frame;

    // the run
    int hp = 30, maxHp = 30, money = 0;
    List<String> items = new ArrayList<>();
    Map<String, Object> flags = new HashMap<>();
    String room = "bedroom";
    PartyVec spawn = new PartyVec(250, 300);
    String facing = "down";

    PartyGame(Context context, Host host) {
        this.context = context;
        this.host = host;
        Typeface f;
        try {
            f = Typeface.createFromAsset(context.getAssets(), ASSETS + "DejaVuSansMono-Bold.ttf");
        } catch (Throwable error) {
            f = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);
        }
        font = f;
        box = new PartyText(this);
        sfx.load(context);
        showScreen("title", null);
    }

    // ------------------------------------------------------------ assets

    Bitmap tex(String name) {
        if (tex.containsKey(name)) return tex.get(name);
        Bitmap b = null;
        try (InputStream in = context.getAssets().open(ASSETS + name + ".png")) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inScaled = false;
            b = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignored) {
        }
        tex.put(name, b);
        return b;
    }

    void playMusic(String n) {
        if (n.equals(musicName)) return;
        musicName = n;
        stopMusic();
        if (n.isEmpty()) return;
        try {
            AssetFileDescriptor fd = context.getAssets().openFd(ASSETS + "music/" + n + ".ogg");
            MediaPlayer p = new MediaPlayer();
            p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            p.setLooping(true);
            p.setVolume(0.45f, 0.45f);
            p.prepare();
            if (!paused) p.start();
            music = p;
        } catch (Throwable error) {
            Diary.note("party music " + n + ": " + error);
        }
    }

    private void stopMusic() {
        if (music != null) {
            try {
                music.release();
            } catch (Throwable ignored) {
            }
        }
        music = null;
    }

    void pause() {
        paused = true;
        input.releaseAll();
        try {
            if (music != null && music.isPlaying()) music.pause();
        } catch (Throwable ignored) {
        }
    }

    void resume() {
        paused = false;
        try {
            if (music != null) music.start();
        } catch (Throwable ignored) {
        }
    }

    void release() {
        stopMusic();
        sfx.release();
    }

    // ------------------------------------------------------------ the loop

    void update(float dt) {
        frame++;
        input.frame();
        if (scene != null) scene.update(dt);
        box.update(dt);
    }

    void draw(PartyGfx g) {
        g.rect(0, 0, 640, 480, PartyGfx.BLACK);
        if (scene != null) scene.draw(g);
        box.draw(g);
    }

    /** The phone's back button: X, and on the title screen it leaves. */
    void back() {
        if (scene instanceof PartyScreens && ((PartyScreens) scene).mode.equals("title")) {
            host.close();
            return;
        }
        input.press(PartyInput.CANCEL);
        input.release(PartyInput.CANCEL);
    }

    // ------------------------------------------------------------ screens

    void showScreen(String mode, Map<String, Object> data) {
        scene = new PartyScreens(this, mode, data == null ? new HashMap<>() : data);
    }

    void newGame() {
        hp = 30;
        maxHp = 30;
        items = new ArrayList<>();
        flags = new HashMap<>();
        money = 10;
        room = "bedroom";
        spawn = new PartyVec(250, 300);
        facing = "down";
        showScreen("intro", null);
    }

    boolean hasSave() {
        return prefs().contains("save");
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void loadGame() {
        try {
            JSONObject o = new JSONObject(prefs().getString("save", "{}"));
            maxHp = o.optInt("max_hp", 30);
            hp = maxHp;
            items = new ArrayList<>();
            JSONArray it = o.optJSONArray("items");
            if (it != null) for (int i = 0; i < it.length(); i++) items.add(it.getString(i));
            money = o.optInt("money", 0);
            flags = new HashMap<>();
            JSONObject f = o.optJSONObject("flags");
            if (f != null) {
                for (Iterator<String> k = f.keys(); k.hasNext(); ) {
                    String key = k.next();
                    flags.put(key, f.get(key));
                }
            }
            room = o.optString("room", "bedroom");
            spawn = new PartyVec((float) o.optDouble("x", 250), (float) o.optDouble("y", 300));
            facing = "down";
            startWorld();
        } catch (Throwable error) {
            Diary.note("party load: " + error);
            newGame();
        }
    }

    void saveGame() {
        try {
            JSONObject o = new JSONObject();
            o.put("max_hp", maxHp);
            o.put("items", new JSONArray(items));
            o.put("money", money);
            JSONObject f = new JSONObject();
            for (Map.Entry<String, Object> e : flags.entrySet()) f.put(e.getKey(), e.getValue());
            o.put("flags", f);
            o.put("room", room);
            o.put("x", spawn.x);
            o.put("y", spawn.y);
            prefs().edit().putString("save", o.toString()).apply();
        } catch (Throwable error) {
            Diary.note("party save: " + error);
        }
    }

    void startWorld() {
        PartyWorld w = new PartyWorld(this);
        scene = w;
        w.enter(room, spawn, facing);
    }

    /** A fight; the world is remembered and comes back after a win. */
    void startBattle(String enemy) {
        scene = new PartyBattle(this, enemy);
    }

    void battleDone(String enemy, String result) {
        if (enemy.equals("boss")) {
            Map<String, Object> d = new HashMap<>();
            d.put("kind", result);
            flags.put("ending", result);
            saveGame();
            showScreen("ending", d);
            return;
        }
        flags.put(enemy + "_" + result, true);
        if (!result.equals("flee")) {
            flags.put(enemy + "_done", true);
            // mini-bosses toughen you up: more health
            if (enemy.equals("captcha") || enemy.equals("moderator")) {
                maxHp += 6;
                hp = maxHp;
            }
        }
        startWorld();
    }

    void gameOver() {
        showScreen("gameover", null);
    }

    // ------------------------------------------------------------ flags

    boolean flag(String k) {
        Object v = flags.get(k);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return v != null;
    }

    int num(String k) {
        Object v = flags.get(k);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    float randf(float a, float b) { return a + rng.nextFloat() * (b - a); }
    int randi(int n) { return n <= 0 ? 0 : rng.nextInt(n); }
}

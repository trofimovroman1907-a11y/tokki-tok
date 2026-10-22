package cat.narezany.margyt;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Themes people make themselves: a .mttheme file.
 *
 * A .mttheme is JSON. It holds what the mod's own appearance settings hold: the
 * colour of each role for the light theme and the dark one, the accent, the
 * likes, the theme engine's background and text, AMOLED and the interface
 * style. Everything in it is optional; what a theme leaves out is left as
 * TikTok has it. The format is written up in docs/themes.md, and the easiest
 * way to start one is "Save as a theme file", which writes out what is set now.
 *
 * Applying a theme first clears the colours of the last one, so two themes in
 * a row do not mix. Nothing in a theme runs: it is colours and a few switches.
 */
final class ThemeFile {

    private ThemeFile() {}

    static final int FORMAT = 1;
    static final String EXTENSION = "mttheme";
    /** The name of the theme put on last, shown in the settings. */
    static final String KEY_NAME = "theme_file_name";
    private static final int MOST_BYTES = 64 * 1024;

    /** A theme read and checked, not yet put on. */
    static final class Theme {
        final JSONObject json;
        final String name, author, description;
        final int colours;

        Theme(JSONObject json, String name, String author, String description, int colours) {
            this.json = json;
            this.name = name;
            this.author = author;
            this.description = description;
            this.colours = colours;
        }
    }

    /** A file that is not a theme, said so a person can tell what is wrong with it. */
    static final class Refused extends Exception {
        Refused(String why) { super(why); }
    }

    static Theme read(byte[] raw) throws Refused {
        if (raw == null || raw.length == 0) throw new Refused(Text.THEME_FILE_EMPTY);
        if (raw.length > MOST_BYTES) throw new Refused(Text.THEME_FILE_BIG);
        JSONObject json;
        try {
            json = new JSONObject(new String(raw, "UTF-8"));
        } catch (Throwable error) {
            throw new Refused(Text.THEME_FILE_NOT_JSON);
        }
        int format = json.optInt("mttheme", -1);
        if (format < 1) throw new Refused(Text.THEME_FILE_NOT_THEME);
        if (format > FORMAT) throw new Refused(Text.THEME_FILE_NEWER);
        String name = json.optString("name", "").trim();
        if (name.isEmpty()) throw new Refused(Text.THEME_FILE_NO_NAME);
        int colours = 0;
        // every colour is checked before anything is put on: half a theme is worse than none
        colours += check(json, "accent");
        for (String mode : new String[]{"light", "dark"}) {
            JSONObject roles = json.optJSONObject(mode);
            if (roles == null) continue;
            for (Iterator<String> keys = roles.keys(); keys.hasNext(); ) {
                String role = keys.next();
                if (role(role) < 0) continue;
                colours += check(roles, role);
            }
        }
        JSONObject likes = json.optJSONObject("likes");
        if (likes != null) colours += check(likes, "feed") + check(likes, "comment");
        JSONObject engine = json.optJSONObject("engine");
        if (engine != null) for (String mode : new String[]{"light", "dark"}) {
            JSONObject pair = engine.optJSONObject(mode);
            if (pair != null) colours += check(pair, "background") + check(pair, "text");
        }
        String design = json.optString("design", "");
        if (!design.isEmpty() && !Design.valid(design)) throw new Refused(Text.THEME_FILE_BAD_DESIGN + " " + design);
        return new Theme(json, cut(name, 60), cut(json.optString("author", "").trim(), 60),
                cut(json.optString("description", "").trim(), 400), colours);
    }

    private static int check(JSONObject parent, String key) throws Refused {
        if (!parent.has(key)) return 0;
        if (colour(parent.optString(key, "")) == null) throw new Refused(Text.THEME_FILE_BAD_COLOUR + " " + key + ": " + parent.optString(key, ""));
        return 1;
    }

    /** "#RRGGBB" or "#AARRGGBB"; the colours of a theme are always solid. */
    static Integer colour(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (!value.matches("#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})")) return null;
        try {
            return 0xFF000000 | (Color.parseColor(value) & 0xFFFFFF);
        } catch (Throwable error) {
            return null;
        }
    }

    private static String hex(int colour) {
        return String.format(Locale.ROOT, "#%06X", colour & 0xFFFFFF);
    }

    private static int role(String name) {
        for (int i = 0; i < AppearanceColors.ROLES.length; i++) if (AppearanceColors.ROLES[i].equals(name)) return i;
        return -1;
    }

    private static String cut(String text, int most) {
        return text.length() <= most ? text : text.substring(0, most);
    }

    // ------------------------------------------------------------ putting on

    static void apply(Context context, Theme theme) {
        JSONObject json = theme.json;
        SharedPreferences prefs = AccountAppearance.prefs(context);
        SharedPreferences.Editor edit = prefs.edit();
        // the last theme's colours go first, so two themes in a row do not mix
        for (String role : AppearanceColors.ROLES) {
            edit.remove(AppearanceColors.key(role, true));
            edit.remove(AppearanceColors.key(role, false));
        }
        for (String mode : new String[]{"light", "dark"}) {
            JSONObject roles = json.optJSONObject(mode);
            if (roles == null) continue;
            for (String role : AppearanceColors.ROLES) {
                Integer value = colour(roles.optString(role, null));
                if (value != null && !AppearanceColors.ACCENT.equals(role)) edit.putInt(AppearanceColors.key(role, "dark".equals(mode)), value);
            }
        }
        JSONObject engine = json.optJSONObject("engine");
        if (engine != null) {
            edit.putBoolean(Themes.KEY_ON, engine.optBoolean("on", true));
            edit.putBoolean(Themes.KEY_MATERIAL, engine.optBoolean("wallpaper", false));
            if (engine.has("strength")) edit.putInt(Themes.KEY_STRENGTH, Math.max(0, Math.min(100, engine.optInt("strength", 100))));
            for (String mode : new String[]{"light", "dark"}) {
                JSONObject pair = engine.optJSONObject(mode);
                if (pair == null) continue;
                boolean dark = "dark".equals(mode);
                Integer background = colour(pair.optString("background", null)), text = colour(pair.optString("text", null));
                if (background != null) edit.putInt(Themes.modeKey(Themes.KEY_BACKGROUND, dark), background);
                if (text != null) edit.putInt(Themes.modeKey(Themes.KEY_TEXT, dark), text);
            }
        }
        if (json.has("amoled")) edit.putBoolean(Themes.KEY_AMOLED, json.optBoolean("amoled", false));
        edit.putString(KEY_NAME, theme.name);
        edit.apply();

        Integer accent = colour(json.optString("accent", null));
        if (accent != null) Accent.setCustom(accent);
        JSONObject likes = json.optJSONObject("likes");
        if (likes != null) {
            Integer feed = colour(likes.optString("feed", null)), comment = colour(likes.optString("comment", null));
            if (feed != null) LikeColors.set(feed);
            if (comment != null) LikeColors.setComment(comment);
        }
        String design = json.optString("design", "");
        if (Design.valid(design)) Design.choose(context, design);

        AppearanceColors.reload();
        Themes.forget();
        Accent.forget();
        Themes.repaintVisible();
        Diary.note("theme: " + theme.name + " put on, " + theme.colours + " colours");
    }

    static String current(Context context) {
        return AccountAppearance.prefs(context).getString(KEY_NAME, "");
    }

    // ------------------------------------------------------------ writing out

    /** What is set now, as a theme file anybody can open and change. */
    static String export(Context context, String name) {
        try {
            SharedPreferences prefs = AccountAppearance.prefs(context);
            JSONObject json = new JSONObject();
            json.put("mttheme", FORMAT);
            json.put("name", name);
            json.put("author", "");
            json.put("description", "");
            json.put("accent", hex(Accent.colour()));
            json.put("design", Design.mode());
            json.put("amoled", Themes.amoledChosen());
            JSONObject engine = new JSONObject();
            engine.put("on", Themes.isEnabled());
            engine.put("wallpaper", Themes.isMaterial());
            engine.put("strength", Themes.strength());
            for (String mode : new String[]{"light", "dark"}) {
                boolean dark = "dark".equals(mode);
                JSONObject pair = new JSONObject();
                String background = Themes.modeKey(Themes.KEY_BACKGROUND, dark), text = Themes.modeKey(Themes.KEY_TEXT, dark);
                if (prefs.contains(background)) pair.put("background", hex(prefs.getInt(background, 0)));
                if (prefs.contains(text)) pair.put("text", hex(prefs.getInt(text, 0)));
                if (pair.length() > 0) engine.put(mode, pair);
            }
            json.put("engine", engine);
            for (String mode : new String[]{"light", "dark"}) {
                JSONObject roles = new JSONObject();
                for (String role : AppearanceColors.ROLES) {
                    if (AppearanceColors.ACCENT.equals(role)) continue;
                    String key = AppearanceColors.key(role, "dark".equals(mode));
                    if (prefs.contains(key)) roles.put(role, hex(prefs.getInt(key, 0)));
                }
                if (roles.length() > 0) json.put(mode, roles);
            }
            JSONObject likes = new JSONObject();
            likes.put("feed", hex(LikeColors.colour()));
            likes.put("comment", hex(LikeColors.commentColour()));
            json.put("likes", likes);
            return json.toString(2);
        } catch (Throwable error) {
            Diary.note("theme export: " + error);
            return null;
        }
    }

    /** A name a file system takes. */
    static String fileName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", " ").trim();
        if (safe.isEmpty()) safe = "theme";
        return safe + "." + EXTENSION;
    }
}

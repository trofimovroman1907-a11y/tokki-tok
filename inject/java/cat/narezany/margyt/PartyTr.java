package cat.narezany.margyt;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The game in Ukrainian and English (2.4): every Russian line the game shows, looked up in assets/margyt/party/tr.json
 * where it is shown -- the dialogue box before it starts typing, the text drawn on the screen, the buttons.
 *
 * A line the code glues together from pieces (a name and a number, two halves of a sentence) is not in the table as a
 * whole; its pieces are, and they are put in its place, the longest first. In Russian nothing is looked up at all.
 */
final class PartyTr {
    private PartyTr() {}

    private static Map<String, String> table;
    private static List<String> pieces;
    private static final Map<String, String> done = Collections.synchronizedMap(new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 600; }
    });

    static String t(String s) {
        if (s == null || s.isEmpty() || Text.pick("ru", "uk", "en").equals("ru")) return s;
        Map<String, String> words = table();
        if (words.isEmpty()) return s;
        String exact = words.get(s);
        if (exact != null) return exact;
        String known = done.get(s);
        if (known != null) return known;
        String out = s;
        if (hasCyrillic(s)) {
            for (String piece : pieces) {
                if (piece.length() >= 3 && out.contains(piece)) out = out.replace(piece, words.get(piece));
            }
        }
        done.put(s, out);
        return out;
    }

    static String[] t(String[] all) {
        if (all == null) return null;
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) out[i] = t(all[i]);
        return out;
    }

    private static boolean hasCyrillic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'Ѐ' && c <= 'ӿ') return true;
        }
        return false;
    }

    private static synchronized Map<String, String> table() {
        if (table != null) return table;
        Map<String, String> out = new HashMap<>();
        String lang = Text.pick("ru", "uk", "en");
        try (InputStream in = Margy.context().getAssets().open("margyt/party/tr.json")) {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) raw.write(buf, 0, n);
            JSONObject all = new JSONObject(raw.toString("UTF-8"));
            java.util.Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                String ru = keys.next();
                JSONObject one = all.optJSONObject(ru);
                String said = one == null ? null : one.optString(lang, null);
                if (said != null && !said.isEmpty()) out.put(ru, said);
            }
        } catch (Throwable error) {
            Diary.note("party translation: " + error);
        }
        List<String> order = new ArrayList<>(out.keySet());
        Collections.sort(order, (a, b) -> b.length() - a.length());
        pieces = order;
        table = out;
        return table;
    }
}

package cat.narezany.margyt;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The game's little sounds, made from square waves and noise in code, as the original made them. */
final class PartySfx {
    private static final int RATE = 22050;
    private static final String[] NAMES = {"blip_narez", "blip_boss", "blip_sys", "blip_cat", "move", "ok", "hurt", "hit", "heal",
            "save", "warn", "laser", "crack", "shatter", "spare", "door", "flee", "item", "glitch", "miss", "pop", "tada"};

    private SoundPool pool;
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<String, Float> volume = new HashMap<>();

    void load(Context context) {
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        File dir = new File(context.getCacheDir(), "margyt-party-sfx-2");
        dir.mkdirs();
        for (String k : NAMES) {
            try {
                File f = new File(dir, k + ".wav");
                if (!f.exists() || f.length() < 50) {
                    File tmp = new File(dir, k + ".tmp");
                    try (FileOutputStream out = new FileOutputStream(tmp)) {
                        out.write(wav(make(k)));
                    }
                    tmp.renameTo(f);
                }
                ids.put(k, pool.load(f.getPath(), 1));
                volume.put(k, k.startsWith("blip") ? 0.4f : 0.6f);
            } catch (Throwable error) {
                Diary.note("party sfx " + k + ": " + error);
            }
        }
    }

    void play(String k) { play(k, 1f); }

    void play(String k, float pitch) {
        Integer id = ids.get(k);
        if (pool == null || id == null) return;
        float v = volume.get(k);
        pool.play(id, v, v, 1, 0, Math.max(0.5f, Math.min(2f, pitch)));
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }

    // ------------------------------------------------------------ making them

    private static float[] make(String k) {
        Random rng = new Random(k.hashCode());
        switch (k) {
            case "blip_narez": return sq(0.035f, 620, 620, 0.35f, 0.5f);
            case "blip_boss": return sq(0.04f, 180, 170, 0.4f, 0.25f);
            case "blip_sys": return sq(0.03f, 900, 900, 0.25f, 0.5f);
            case "blip_cat": return sq(0.05f, 1100, 1500, 0.3f, 0.5f);
            case "move": return sq(0.04f, 700, 900, 0.3f, 0.5f);
            case "ok": return mix(sq(0.06f, 800, 800, 0.3f, 0.5f), sq(0.09f, 1200, 1200, 0.2f, 0.5f));
            case "hurt": return mix(sq(0.25f, 300, 80, 0.45f, 0.5f), noise(rng, 0.2f, 0.3f));
            case "hit": return mix(noise(rng, 0.25f, 0.6f), sq(0.3f, 220, 50, 0.4f, 0.5f));
            case "heal": return seq(new float[]{523, 659, 784, 1046}, 0.06f, 0.3f);
            case "save": return seq(new float[]{784, 988, 1175, 1568, 1175, 1568}, 0.07f, 0.25f);
            case "warn": return sq(0.12f, 1400, 1400, 0.2f, 0.5f);
            case "laser": return mix(noise(rng, 0.45f, 0.35f), sq(0.45f, 140, 120, 0.35f, 0.5f));
            case "crack": return mix(noise(rng, 0.15f, 0.6f), sq(0.15f, 400, 200, 0.3f, 0.5f));
            case "shatter": return mix(noise(rng, 0.7f, 0.5f), seq(new float[]{300, 200, 140}, 0.15f, 0.3f));
            case "spare": return seq(new float[]{1046, 1318, 1568, 2093}, 0.05f, 0.22f);
            case "door": return mix(noise(rng, 0.3f, 0.25f), sq(0.3f, 90, 60, 0.4f, 0.5f));
            case "flee": return sq(0.3f, 300, 1200, 0.3f, 0.5f);
            case "item": return seq(new float[]{659, 880}, 0.08f, 0.3f);
            case "glitch": return mix(noise(rng, 0.4f, 0.4f), sq(0.4f, 60, 900, 0.25f, 0.1f));
            case "miss": return sq(0.12f, 300, 250, 0.25f, 0.5f);
            // a party popper and a little fanfare, for the birthday
            case "pop": return mix(noise(rng, 0.18f, 0.7f), sq(0.12f, 1800, 600, 0.25f, 0.5f));
            case "tada": return seq(new float[]{784, 784, 784, 1046, 0, 1318, 1568}, 0.09f, 0.28f);
        }
        return new float[1];
    }

    private static float[] sq(float length, float f0, float f1, float amp, float duty) {
        int n = (int) (length * RATE);
        float[] s = new float[n];
        float ph = 0f;
        for (int i = 0; i < n; i++) {
            float k = (float) i / n;
            float f = f0 + (f1 - f0) * k;
            ph = (ph + f / RATE) % 1f;
            s[i] = f <= 0 ? 0f : (ph < duty ? 1f : -1f) * amp * Math.min(1f, (1f - k) * 6f);
        }
        return s;
    }

    private static float[] noise(Random rng, float length, float amp) {
        int n = (int) (length * RATE);
        float[] s = new float[n];
        float v = 0f;
        for (int i = 0; i < n; i++) {
            if (i % 3 == 0) v = rng.nextFloat() * 2f - 1f;
            float k = 1f - (float) i / n;
            s[i] = v * amp * k * k;
        }
        return s;
    }

    private static float[] seq(float[] freqs, float step, float amp) {
        int one = (int) (step * RATE);
        float[] out = new float[one * freqs.length];
        for (int i = 0; i < freqs.length; i++) System.arraycopy(sq(step, freqs[i], freqs[i], amp, 0.25f), 0, out, i * one, one);
        return out;
    }

    private static float[] mix(float[] a, float[] b) {
        float[] s = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < s.length; i++) s[i] = (i < a.length ? a[i] : 0f) + (i < b.length ? b[i] : 0f);
        return s;
    }

    private static byte[] wav(float[] s) {
        int data = s.length * 2;
        byte[] out = new byte[44 + data];
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + data).put("WAVE".getBytes()).put("fmt ".getBytes())
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes()).putInt(data);
        for (float v : s) b.putShort((short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767f));
        return out;
    }
}

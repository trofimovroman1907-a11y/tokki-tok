package cat.narezany.margyt;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Drawing for the birthday game: rectangles, circles, lines, pixel sprites and the one blocky font,
 * all in the game's own 640x480 coordinates. Colours are plain ARGB ints.
 */
final class PartyGfx {
    Canvas c;
    final Paint fill = new Paint();
    final Paint line = new Paint();
    final Paint text = new Paint();
    final Paint sprite = new Paint();
    private final RectF rf = new RectF();
    private final Rect src = new Rect();
    private final Path path = new Path();
    /** Saved state of the canvas with the screen's own scale on it: transforms go back to it, not on top. */
    private int base = -1;

    PartyGfx(Typeface font) {
        fill.setStyle(Paint.Style.FILL);
        fill.setAntiAlias(false);
        line.setStyle(Paint.Style.STROKE);
        line.setAntiAlias(false);
        line.setStrokeCap(Paint.Cap.BUTT);
        text.setTypeface(font);
        text.setAntiAlias(false);
        text.setSubpixelText(false);
        sprite.setFilterBitmap(false);
        sprite.setAntiAlias(false);
    }

    void begin(Canvas canvas) {
        c = canvas;
        base = canvas.save();
    }

    void end() {
        if (base >= 0) c.restoreToCount(base);
        base = -1;
    }

    /** Godot's draw_set_transform: an offset and a turn from the screen itself, not from the last one. */
    void transform(float dx, float dy, float rotation) {
        c.restoreToCount(base);
        base = c.save();
        c.translate(dx, dy);
        if (rotation != 0f) c.rotate((float) Math.toDegrees(rotation));
    }

    void transform(float dx, float dy) { transform(dx, dy, 0f); }

    // ------------------------------------------------------------ colours

    static int rgb(float r, float g, float b) { return rgba(r, g, b, 1f); }

    static int rgba(float r, float g, float b, float a) {
        return (cl(a) << 24) | (cl(r) << 16) | (cl(g) << 8) | cl(b);
    }

    private static int cl(float v) { return Math.max(0, Math.min(255, Math.round(v * 255f))); }

    static float r(int c) { return ((c >> 16) & 255) / 255f; }
    static float g(int c) { return ((c >> 8) & 255) / 255f; }
    static float b(int c) { return (c & 255) / 255f; }
    static float a(int c) { return ((c >>> 24) & 255) / 255f; }

    static int alpha(int c, float a) { return rgba(r(c), g(c), b(c), a); }
    static int darkened(int c, float k) { return rgba(r(c) * (1 - k), g(c) * (1 - k), b(c) * (1 - k), a(c)); }
    static int lightened(int c, float k) { return rgba(r(c) + (1 - r(c)) * k, g(c) + (1 - g(c)) * k, b(c) + (1 - b(c)) * k, a(c)); }

    static int lerp(int x, int y, float k) {
        return rgba(r(x) + (r(y) - r(x)) * k, g(x) + (g(y) - g(x)) * k, b(x) + (b(y) - b(x)) * k, a(x) + (a(y) - a(x)) * k);
    }

    static float luminance(int c) { return 0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c); }

    static final int WHITE = 0xFFFFFFFF, BLACK = 0xFF000000;

    // ------------------------------------------------------------ shapes

    void rect(PartyRect r, int col) { rect(r.x, r.y, r.w, r.h, col); }

    void rect(float x, float y, float w, float h, int col) {
        if (w <= 0 || h <= 0) return;
        fill.setColor(col);
        c.drawRect(x, y, x + w, y + h, fill);
    }

    void frame(PartyRect r, int col, float width) { frame(r.x, r.y, r.w, r.h, col, width); }

    void frame(float x, float y, float w, float h, int col, float width) {
        line.setColor(col);
        line.setStrokeWidth(width);
        c.drawRect(x, y, x + w, y + h, line);
    }

    void circle(PartyVec p, float rad, int col) { circle(p.x, p.y, rad, col); }

    void circle(float x, float y, float rad, int col) {
        fill.setColor(col);
        fill.setAntiAlias(true);
        c.drawCircle(x, y, rad, fill);
        fill.setAntiAlias(false);
    }

    void ring(float x, float y, float rad, int col, float width) {
        line.setColor(col);
        line.setStrokeWidth(width);
        line.setAntiAlias(true);
        c.drawCircle(x, y, rad, line);
        line.setAntiAlias(false);
    }

    void line(PartyVec a, PartyVec b, int col, float width) { line(a.x, a.y, b.x, b.y, col, width); }

    void line(float x0, float y0, float x1, float y1, int col, float width) {
        line.setColor(col);
        line.setStrokeWidth(width);
        c.drawLine(x0, y0, x1, y1, line);
    }

    void poly(int col, float... xy) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        path.close();
        fill.setColor(col);
        fill.setAntiAlias(true);
        c.drawPath(path, fill);
        fill.setAntiAlias(false);
    }

    void polyline(float[] xy, int col, float width) {
        path.reset();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
        line.setColor(col);
        line.setStrokeWidth(width);
        line.setAntiAlias(true);
        c.drawPath(path, line);
        line.setAntiAlias(false);
    }

    // ------------------------------------------------------------ sprites

    void tex(Bitmap b, PartyRect r) { tex(b, r.x, r.y, r.w, r.h, WHITE); }
    void tex(Bitmap b, PartyRect r, int modulate) { tex(b, r.x, r.y, r.w, r.h, modulate); }
    void tex(Bitmap b, float x, float y, float w, float h) { tex(b, x, y, w, h, WHITE); }

    /** A sprite stretched into a rectangle, pixels kept square-edged; modulate tints it as Godot's would. */
    void tex(Bitmap b, float x, float y, float w, float h, int modulate) {
        if (b == null) return;
        src.set(0, 0, b.getWidth(), b.getHeight());
        region(b, src, x, y, w, h, modulate);
    }

    void texRegion(Bitmap b, float x, float y, float w, float h, int sx, int sy, int sw, int sh) {
        if (b == null) return;
        src.set(sx, sy, sx + sw, sy + sh);
        region(b, src, x, y, w, h, WHITE);
    }

    private void region(Bitmap b, Rect from, float x, float y, float w, float h, int modulate) {
        float al = a(modulate);
        if (al <= 0f) return;
        sprite.setAlpha(Math.round(al * 255f));
        if ((modulate & 0xFFFFFF) != 0xFFFFFF) sprite.setColorFilter(new LightingColorFilter(modulate & 0xFFFFFF, 0));
        else sprite.setColorFilter(null);
        if (w < 0) { x += w; w = -w; }
        if (h < 0) { y += h; h = -h; }
        rf.set(x, y, x + w, y + h);
        c.drawBitmap(b, from, rf, sprite);
        sprite.setColorFilter(null);
        sprite.setAlpha(255);
    }

    // ------------------------------------------------------------ text

    float width(String s, float size) {
        text.setTextSize(size);
        return text.measureText(s);
    }

    /** Text with its baseline at y; centred on x when asked. */
    void text(String s, float x, float y, float size, int col, boolean center) {
        s = PartyTr.t(s);
        if (s == null || s.isEmpty()) return;
        text.setTextSize(size);
        text.setColor(col);
        float w = center ? text.measureText(s) : 0f;
        c.drawText(s, x - w * 0.5f, y, text);
    }

    void text(String s, PartyVec p, float size, int col, boolean center) { text(s, p.x, p.y, size, col, center); }

    /** Lines that fit into width w, broken between words. */
    java.util.List<String> wrap(String t, float w, float size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        text.setTextSize(size);
        for (String para : t.split("\n", -1)) {
            String ln = "";
            for (String word : para.split(" ", -1)) {
                String test = ln.isEmpty() ? word : ln + " " + word;
                if (text.measureText(test) > w && !ln.isEmpty()) {
                    out.add(ln);
                    ln = word;
                } else {
                    ln = test;
                }
            }
            out.add(ln);
        }
        return out;
    }
}

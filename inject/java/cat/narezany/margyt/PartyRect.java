package cat.narezany.margyt;

/** A rectangle the way the game was first written with one (Godot's Rect2): position and size. */
final class PartyRect {
    float x, y, w, h;

    PartyRect(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    PartyRect(PartyVec p, PartyVec s) { this(p.x, p.y, s.x, s.y); }

    PartyRect copy() { return new PartyRect(x, y, w, h); }
    float ex() { return x + w; }
    float ey() { return y + h; }
    PartyVec pos() { return new PartyVec(x, y); }
    PartyVec end() { return new PartyVec(x + w, y + h); }
    PartyVec center() { return new PartyVec(x + w * 0.5f, y + h * 0.5f); }
    float cx() { return x + w * 0.5f; }
    float cy() { return y + h * 0.5f; }

    PartyRect grow(float k) { return new PartyRect(x - k, y - k, w + 2 * k, h + 2 * k); }

    PartyRect grow(float left, float top, float right, float bottom) {
        return new PartyRect(x - left, y - top, w + left + right, h + top + bottom);
    }

    PartyRect moved(float dx, float dy) { return new PartyRect(x + dx, y + dy, w, h); }

    /** Overlap with some area; touching edges do not count. */
    boolean intersects(PartyRect o) {
        return !(x >= o.ex() || ex() <= o.x || y >= o.ey() || ey() <= o.y);
    }

    boolean encloses(PartyRect o) {
        return o.x >= x && o.y >= y && o.ex() <= ex() && o.ey() <= ey();
    }

    boolean has(PartyVec p) {
        return p.x >= x && p.y >= y && p.x < ex() && p.y < ey();
    }

    PartyRect intersection(PartyRect o) {
        float nx = Math.max(x, o.x), ny = Math.max(y, o.y);
        float nex = Math.min(ex(), o.ex()), ney = Math.min(ey(), o.ey());
        if (nex <= nx || ney <= ny) return new PartyRect(nx, ny, 0, 0);
        return new PartyRect(nx, ny, nex - nx, ney - ny);
    }

    static PartyRect lerp(PartyRect a, PartyRect b, float k) {
        return new PartyRect(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k, a.w + (b.w - a.w) * k, a.h + (b.h - a.h) * k);
    }
}

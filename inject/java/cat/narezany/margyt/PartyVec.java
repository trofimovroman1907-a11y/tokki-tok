package cat.narezany.margyt;

/** A point or a step on the birthday game's 640x480 screen. */
final class PartyVec {
    float x, y;

    PartyVec(float x, float y) { this.x = x; this.y = y; }

    PartyVec copy() { return new PartyVec(x, y); }
    PartyVec add(PartyVec o) { return new PartyVec(x + o.x, y + o.y); }
    PartyVec add(float dx, float dy) { return new PartyVec(x + dx, y + dy); }
    PartyVec sub(PartyVec o) { return new PartyVec(x - o.x, y - o.y); }
    PartyVec mul(float k) { return new PartyVec(x * k, y * k); }
    float length() { return (float) Math.hypot(x, y); }
    float dist(PartyVec o) { return (float) Math.hypot(x - o.x, y - o.y); }
    boolean zero() { return x == 0f && y == 0f; }

    PartyVec normalized() {
        float l = length();
        return l == 0f ? new PartyVec(0, 0) : new PartyVec(x / l, y / l);
    }

    PartyVec lerp(PartyVec o, float k) { return new PartyVec(x + (o.x - x) * k, y + (o.y - y) * k); }

    PartyVec clamp(PartyVec lo, PartyVec hi) {
        return new PartyVec(Math.max(lo.x, Math.min(hi.x, x)), Math.max(lo.y, Math.min(hi.y, y)));
    }

    static PartyVec angle(float a) { return new PartyVec((float) Math.cos(a), (float) Math.sin(a)); }
}

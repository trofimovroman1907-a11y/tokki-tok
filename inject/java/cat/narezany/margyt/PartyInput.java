package cat.narezany.margyt;

/**
 * The game's buttons: four directions, OK (Z), back (X) and the menu (C). Touches and keys both press them.
 * A press is seen as "just pressed" for one game frame even if the finger was already up by then.
 */
final class PartyInput {
    static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3, OK = 4, CANCEL = 5, MENU = 6, COUNT = 7;

    private final int[] held = new int[COUNT];
    private final boolean[] just = new boolean[COUNT];
    private final boolean[] latched = new boolean[COUNT];

    synchronized void press(int a) {
        if (held[a] == 0) latched[a] = true;
        held[a]++;
    }

    synchronized void release(int a) {
        if (held[a] > 0) held[a]--;
    }

    synchronized void releaseAll() {
        java.util.Arrays.fill(held, 0);
    }

    /** Start of a game frame: what was pressed since the last one becomes "just pressed". */
    synchronized void frame() {
        for (int i = 0; i < COUNT; i++) {
            just[i] = latched[i];
            latched[i] = false;
        }
    }

    /** A pressed state that has been seen already: one press, one action. */
    synchronized void consume(int a) { just[a] = false; }

    boolean pressed(int a) { return held[a] > 0 || just[a]; }
    boolean justPressed(int a) { return just[a]; }

    float axisX() { return (pressed(RIGHT) ? 1f : 0f) - (pressed(LEFT) ? 1f : 0f); }
    float axisY() { return (pressed(DOWN) ? 1f : 0f) - (pressed(UP) ? 1f : 0f); }
}

package cat.narezany.margyt;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

/**
 * The birthday game's screen: the 640x480 picture in the middle, sideways, a pad on the left and the buttons on the
 * right in the empty strips beside it. Keys work too.
 */
public final class PartyActivity extends Activity implements PartyGame.Host {
    private PartyGame game;
    private Stage stage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Build.VERSION.SDK_INT >= 28) {
                getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } catch (Throwable ignored) {
        }
        game = new PartyGame(this, this);
        stage = new Stage(this, game);
        setContentView(stage);
        immersive();
        Diary.note("party: the birthday game is open");
    }

    private void immersive() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                stage.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) immersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        game.resume();
        stage.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stage.stop();
        game.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stage.stop();
        game.release();
    }

    @Override
    public void onBackPressed() {
        game.back();
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent event) {
        int a = action(code);
        if (a < 0) return super.onKeyDown(code, event);
        if (event.getRepeatCount() == 0) game.input.press(a);
        return true;
    }

    @Override
    public boolean onKeyUp(int code, KeyEvent event) {
        int a = action(code);
        if (a < 0) return super.onKeyUp(code, event);
        game.input.release(a);
        return true;
    }

    private static int action(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W: return PartyInput.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_S: return PartyInput.DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: return PartyInput.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: return PartyInput.RIGHT;
            case KeyEvent.KEYCODE_Z: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_DPAD_CENTER: return PartyInput.OK;
            case KeyEvent.KEYCODE_X: case KeyEvent.KEYCODE_SHIFT_LEFT: case KeyEvent.KEYCODE_BUTTON_B: return PartyInput.CANCEL;
            case KeyEvent.KEYCODE_C: case KeyEvent.KEYCODE_CTRL_LEFT: case KeyEvent.KEYCODE_BUTTON_Y: return PartyInput.MENU;
        }
        return -1;
    }

    // ------------------------------------------------------------ PartyGame.Host

    @Override
    public void close() {
        finish();
    }

    /** The party hat, from the server; says in words what happened. */
    @Override
    public void claimBadge(java.util.function.Consumer<String> answer) {
        try {
            Mine.takeParty((ok, trouble) -> {
                if (ok) answer.accept("Значок «Праздничный колпак» теперь твой! Он уже в профиле MargyT.");
                else if (Mine.PROVE.equals(trouble)) answer.accept("Чтобы получить значок, подтверди свой аккаунт в настройках MargyT, раздел «Значки». Потом зайди сюда ещё раз: праздник сохранён.");
                else if ("early".equals(trouble)) answer.accept("Значок выдают с 24 ноября. Приходи в день рождения!");
                else answer.accept("Значок не выдался: " + (trouble == null || trouble.isEmpty() ? "нет связи с сервером" : trouble) + ". Зайди сюда ещё раз позже.");
            });
        } catch (Throwable error) {
            Diary.note("party badge: " + error);
            answer.accept("Значок не выдался. Зайди сюда ещё раз позже.");
        }
    }

    // ------------------------------------------------------------ the screen

    private static final class Stage extends View implements Choreographer.FrameCallback {
        private final PartyGame game;
        private final PartyGfx gfx;
        private final Paint ui = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint uiText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private boolean running;
        private long last;
        /** Which control each finger holds. */
        private final Map<Integer, String> held = new HashMap<>();
        private int padX, padY;

        Stage(Context context, PartyGame game) {
            super(context);
            this.game = game;
            gfx = new PartyGfx(game.font);
            uiText.setTypeface(game.font);
            uiText.setTextAlign(Paint.Align.CENTER);
            setKeepScreenOn(true);
            setFocusable(true);
        }

        void start() {
            if (running) return;
            running = true;
            last = 0;
            Choreographer.getInstance().postFrameCallback(this);
        }

        void stop() {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
            held.clear();
            padX = padY = 0;
        }

        @Override
        public void doFrame(long now) {
            if (!running) return;
            float dt = last == 0 ? 1f / 60f : Math.min((now - last) / 1e9f, 0.05f);
            last = now;
            try {
                game.update(dt);
            } catch (Throwable error) {
                Diary.note("party update: " + error);
            }
            invalidate();
            Choreographer.getInstance().postFrameCallback(this);
        }

        private float scale() { return Math.min(getWidth() / 640f, getHeight() / 480f); }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF000000);
            float s = scale();
            float ox = (getWidth() - 640f * s) * 0.5f, oy = (getHeight() - 480f * s) * 0.5f;
            int saved = canvas.save();
            canvas.translate(ox, oy);
            canvas.scale(s, s);
            canvas.clipRect(0, 0, 640, 480);
            gfx.begin(canvas);
            try {
                game.draw(gfx);
            } catch (Throwable error) {
                Diary.note("party draw: " + error);
            }
            gfx.end();
            canvas.restoreToCount(saved);
            drawControls(canvas);
        }

        // ------------------------------------------------------------ touch

        private static final class Layout {
            float padCx, padCy, padR, btnR;
            float okX, okY, cancelX, cancelY, menuX, menuY, closeX, closeY;
            boolean faded;
        }

        private Layout layout() {
            Layout l = new Layout();
            float w = getWidth(), h = getHeight();
            float bar = (w - 640f * scale()) * 0.5f;
            l.faded = bar < h * 0.25f;
            l.padR = h * 0.16f;
            l.btnR = h * 0.085f;
            l.padCx = Math.max(bar * 0.5f, l.padR + h * 0.05f);
            l.padCy = h - l.padR - h * 0.1f;
            float right = w - Math.max(bar * 0.5f, l.btnR * 2.4f);
            l.okX = right + l.btnR * 0.75f;
            l.okY = h * 0.6f;
            l.cancelX = right - l.btnR * 0.9f;
            l.cancelY = h * 0.82f;
            l.menuX = right - l.btnR * 0.7f;
            l.menuY = h * 0.36f;
            l.closeX = Math.max(bar * 0.5f, h * 0.07f);
            l.closeY = h * 0.1f;
            return l;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            Layout l = layout();
            int action = e.getActionMasked();
            int index = e.getActionIndex();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    int id = e.getPointerId(index);
                    float x = e.getX(index), y = e.getY(index);
                    String what = hit(l, x, y);
                    if (what != null) {
                        held.put(id, what);
                        if (what.equals("pad")) pad(x - l.padCx, y - l.padCy, l);
                        else if (what.equals("close")) game.host.close();
                        else game.input.press(button(what));
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE:
                    for (int i = 0; i < e.getPointerCount(); i++) {
                        if ("pad".equals(held.get(e.getPointerId(i)))) pad(e.getX(i) - l.padCx, e.getY(i) - l.padCy, l);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_CANCEL: {
                    int id = e.getPointerId(index);
                    if (action == MotionEvent.ACTION_CANCEL) {
                        for (String what : held.values()) release(what);
                        held.clear();
                    } else {
                        String what = held.remove(id);
                        if (what != null) release(what);
                    }
                    break;
                }
            }
            return true;
        }

        private void release(String what) {
            if (what.equals("pad")) {
                setPad(0, 0);
            } else if (!what.equals("close")) {
                game.input.release(button(what));
            }
        }

        private static int button(String what) {
            switch (what) {
                case "ok": return PartyInput.OK;
                case "cancel": return PartyInput.CANCEL;
                default: return PartyInput.MENU;
            }
        }

        private static float dist(float x0, float y0, float x1, float y1) { return (float) Math.hypot(x1 - x0, y1 - y0); }

        private String hit(Layout l, float x, float y) {
            if (dist(x, y, l.padCx, l.padCy) < l.padR * 1.5f) return "pad";
            if (dist(x, y, l.okX, l.okY) < l.btnR * 1.4f) return "ok";
            if (dist(x, y, l.cancelX, l.cancelY) < l.btnR * 1.4f) return "cancel";
            if (dist(x, y, l.menuX, l.menuY) < l.btnR * 1.1f) return "menu";
            if (dist(x, y, l.closeX, l.closeY) < l.btnR * 0.8f) return "close";
            return null;
        }

        /** Eight directions, a dead zone in the middle. */
        private void pad(float dx, float dy, Layout l) {
            int nx = 0, ny = 0;
            if (Math.hypot(dx, dy) > l.padR * 0.2f) {
                double a = Math.atan2(dy, dx);
                nx = (int) Math.max(-1, Math.min(1, Math.round(Math.cos(a) * 1.2)));
                ny = (int) Math.max(-1, Math.min(1, Math.round(Math.sin(a) * 1.2)));
            }
            setPad(nx, ny);
        }

        private void setPad(int nx, int ny) {
            if (nx == padX && ny == padY) return;
            PartyInput in = game.input;
            if (padX < 0 && nx >= 0) in.release(PartyInput.LEFT);
            if (padX > 0 && nx <= 0) in.release(PartyInput.RIGHT);
            if (padY < 0 && ny >= 0) in.release(PartyInput.UP);
            if (padY > 0 && ny <= 0) in.release(PartyInput.DOWN);
            if (nx < 0 && padX >= 0) in.press(PartyInput.LEFT);
            if (nx > 0 && padX <= 0) in.press(PartyInput.RIGHT);
            if (ny < 0 && padY >= 0) in.press(PartyInput.UP);
            if (ny > 0 && padY <= 0) in.press(PartyInput.DOWN);
            padX = nx;
            padY = ny;
        }

        private void drawControls(Canvas c) {
            Layout l = layout();
            float k = l.faded ? 0.45f : 1f;
            int mint = PartyGame.MINT;
            ui.setStyle(Paint.Style.FILL);
            ui.setColor(PartyGfx.rgba(1, 1, 1, 0.06f * k));
            c.drawCircle(l.padCx, l.padCy, l.padR, ui);
            ui.setStyle(Paint.Style.STROKE);
            ui.setStrokeWidth(Math.max(2f, l.padR * 0.025f));
            ui.setColor(PartyGfx.rgba(1, 1, 1, 0.35f * k));
            c.drawCircle(l.padCx, l.padCy, l.padR, ui);
            ui.setStyle(Paint.Style.FILL);
            int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] d : dirs) {
                boolean on = (d[0] != 0 && d[0] == padX) || (d[1] != 0 && d[1] == padY);
                float tipX = l.padCx + d[0] * l.padR * 0.72f, tipY = l.padCy + d[1] * l.padR * 0.72f;
                float side = l.padR * 0.2f;
                android.graphics.Path p = new android.graphics.Path();
                p.moveTo(tipX + d[0] * l.padR * 0.17f, tipY + d[1] * l.padR * 0.17f);
                p.lineTo(tipX - d[1] * side, tipY + d[0] * side);
                p.lineTo(tipX + d[1] * side, tipY - d[0] * side);
                p.close();
                ui.setColor(on ? mint : PartyGfx.rgba(1, 1, 1, 0.55f * k));
                c.drawPath(p, ui);
            }
            button(c, l, l.okX, l.okY, l.btnR, "Z", held.containsValue("ok"), true, k);
            button(c, l, l.cancelX, l.cancelY, l.btnR, "X", held.containsValue("cancel"), false, k);
            button(c, l, l.menuX, l.menuY, l.btnR * 0.65f, "C", held.containsValue("menu"), false, k);
            button(c, l, l.closeX, l.closeY, l.btnR * 0.5f, "✕", false, false, k * 0.8f);
        }

        private void button(Canvas c, Layout l, float x, float y, float r, String label, boolean down, boolean main, float k) {
            int mint = PartyGame.MINT;
            ui.setStyle(Paint.Style.FILL);
            ui.setColor(main ? PartyGfx.alpha(mint, (down ? 0.45f : 0.12f) * k) : PartyGfx.rgba(1, 1, 1, (down ? 0.35f : 0.08f) * k));
            c.drawCircle(x, y, r, ui);
            ui.setStyle(Paint.Style.STROKE);
            ui.setColor(PartyGfx.rgba(1, 1, 1, 0.5f * k));
            c.drawCircle(x, y, r, ui);
            uiText.setColor(PartyGfx.rgba(1, 1, 1, 0.8f * k));
            uiText.setTextSize(r * 0.75f);
            c.drawText(PartyTr.t(label), x, y + r * 0.27f, uiText);
        }
    }
}

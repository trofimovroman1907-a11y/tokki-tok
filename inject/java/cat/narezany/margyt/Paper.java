package cat.narezany.margyt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A letter on lined paper, by hand: the letters of the mailbox (2.3.1). */
final class Paper {
    private Paper() {}

    private static final int PAPER = 0xFFF6EFE1, PAPER_EDGE = 0xFFD9CDB4, INK = 0xFF2E2A33;

    private static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private static Typeface hand(Context c) {
        try {
            return Typeface.createFromAsset(c.getAssets(), "margyt/fonts/caveat.ttf");
        } catch (Throwable error) {
            return Typeface.SERIF;
        }
    }

    /** A letter on lined paper, by hand: the invitation and the ones in the mailbox. Without `go`, one button closes it. */
    static void show(final Activity a, String when, String heading, String text, String signed, String later, String go, final Runnable onGo) {
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        Typeface hand = hand(a);
        LinearLayout outer = new LinearLayout(a);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(a, 8), dp(a, 8), dp(a, 8), dp(a, 8));

        Sheet sheet = new Sheet(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(a, 42), dp(a, 22), dp(a, 20), dp(a, 22));
        sheet.setRotation(-1.2f);

        TextView date = new TextView(a);
        date.setText(when == null ? "" : when);
        date.setTypeface(hand);
        date.setTextSize(17);
        date.setTextColor(0xAA2E2A33);
        date.setGravity(Gravity.END);
        sheet.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (heading != null && heading.length() > 0) {
            TextView head = new TextView(a);
            head.setText(heading);
            head.setTypeface(hand, Typeface.BOLD);
            head.setTextSize(23);
            head.setTextColor(INK);
            head.setPadding(0, dp(a, 2), 0, dp(a, 6));
            sheet.addView(head);
        }
        TextView body = new TextView(a);
        body.setText(text);
        body.setTextIsSelectable(go == null);
        body.setTypeface(hand);
        body.setTextSize(19);
        body.setLineSpacing(0, 1.04f);
        body.setTextColor(INK);
        sheet.addView(body);

        TextView sign = new TextView(a);
        sign.setText(signed == null || signed.length() == 0 ? Local.of("Нарезанный") : signed);
        sign.setTypeface(hand, Typeface.BOLD);
        sign.setTextSize(23);
        sign.setTextColor(0xFF2B4FA0);
        sign.setGravity(Gravity.END);
        sign.setPadding(0, dp(a, 8), dp(a, 4), 0);
        sheet.addView(sign, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(sheet);

        LinearLayout buttons = new LinearLayout(a);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(a, 14), 0, 0);
        TextView close = button(a, later, go == null);
        if (go == null) {
            buttons.addView(close, new LinearLayout.LayoutParams(0, dp(a, 46), 1f));
        } else {
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, dp(a, 46), 1f);
            bl.setMarginEnd(dp(a, 8));
            buttons.addView(close, bl);
            TextView going = button(a, go, true);
            buttons.addView(going, new LinearLayout.LayoutParams(0, dp(a, 46), 1.6f));
            going.setOnClickListener(v -> {
                d.dismiss();
                if (onGo != null) onGo.run();
            });
        }
        outer.addView(buttons);
        close.setOnClickListener(v -> d.dismiss());

        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(outer);
        d.setContentView(scroll);
        try {
            android.view.Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
                w.setLayout(Math.min(dp(a, 380), a.getResources().getDisplayMetrics().widthPixels - dp(a, 24)), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (Throwable ignored) {
        }
        sheet.setTranslationY(dp(a, 24));
        sheet.setAlpha(0f);
        d.show();
        sheet.animate().translationY(0).alpha(1f).setDuration(220).start();
    }

    private static TextView button(Context a, String label, boolean main) {
        TextView b = new TextView(a);
        b.setText(label);
        Fonts.apply(b);
        b.setTypeface(b.getTypeface(), Typeface.BOLD);
        b.setTextSize(15);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(a, 23));
        if (main) {
            bg.setColor(0xFF2E2A33);
            b.setTextColor(0xFFF6EFE1);
        } else {
            bg.setColor(0xFFF6EFE1);
            b.setTextColor(0xFF2E2A33);
        }
        b.setBackground(bg);
        return b;
    }

    /** A sheet of lined paper with a red margin. */
    private static final class Sheet extends LinearLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Sheet(Context c) {
            super(c);
            setWillNotDraw(false);
            setElevation(6 * c.getResources().getDisplayMetrics().density);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFFFBF8F0);
            bg.setCornerRadius(2 * c.getResources().getDisplayMetrics().density);
            setBackground(bg);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            p.setStrokeWidth(Math.max(1f, d * 0.7f));
            p.setColor(0x334A7BC0);
            float step = 27.5f * d;
            for (float y = 48 * d; y < getHeight() - 6 * d; y += step) c.drawLine(0, y, getWidth(), y, p);
            p.setColor(0x66D04848);
            c.drawLine(32 * d, 0, 32 * d, getHeight(), p);
            super.onDraw(c);
        }
    }

}

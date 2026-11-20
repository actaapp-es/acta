package com.acta.recorder;

import android.content.Context;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Teclado numérico reutilizable (marcador y tonos durante la llamada). */
final class Keypad {

    interface Listener {
        void onKey(char c);
        void onLongKey(char c);
    }

    private static final char[] KEYS = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'};
    private static final String[] LETTERS = {"", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "", "+", ""};

    private Keypad() { }

    static void build(LinearLayout grid, boolean compact, final Listener l) {
        Context c = grid.getContext();
        float d = c.getResources().getDisplayMetrics().density;
        int size = Math.round((compact ? 58 : 70) * d);
        int gap = Math.round((compact ? 8 : 12) * d);
        for (int r = 0; r < 4; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 3; k++) {
                final int i = r * 3 + k;
                android.widget.FrameLayout cell = new android.widget.FrameLayout(c);
                LinearLayout key = new LinearLayout(c);
                key.setOrientation(LinearLayout.VERTICAL);
                key.setGravity(Gravity.CENTER);
                key.setBackgroundResource(R.drawable.bg_key);

                TextView digit = new TextView(c, null, 0, R.style.Text);
                digit.setText(String.valueOf(KEYS[i]));
                digit.setTextSize(compact ? 24 : 28);
                digit.setGravity(Gravity.CENTER);
                key.addView(digit);

                if (!compact) {
                    TextView letters = new TextView(c, null, 0, R.style.Text_Meta);
                    letters.setText(LETTERS[i].isEmpty() ? " " : LETTERS[i]);
                    letters.setTextSize(10);
                    letters.setLetterSpacing(0.14f);
                    letters.setTypeface(Ui.medium());
                    letters.setGravity(Gravity.CENTER);
                    letters.setPadding(0, Math.round(2 * d), 0, 0);
                    key.addView(letters);
                }

                key.setContentDescription(String.valueOf(KEYS[i]));
                key.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        l.onKey(KEYS[i]);
                    }
                });
                key.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View v) {
                        l.onLongKey(KEYS[i]);
                        return true;
                    }
                });
                cell.addView(key, new android.widget.FrameLayout.LayoutParams(size, size, Gravity.CENTER));
                row.addView(cell, new LinearLayout.LayoutParams(0, size + gap, 1f));
            }
            grid.addView(row, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }
}

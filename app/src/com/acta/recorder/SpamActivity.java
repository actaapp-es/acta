package com.acta.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Papelera de spam: llamadas rechazadas automáticamente. Se puede vaciar. */
public class SpamActivity extends Activity {

    private final List<Db.Call> items = new ArrayList<>();
    private Adapter adapter;
    private TextView empty;

    private final BroadcastReceiver updated = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { load(); }
    };

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Ui.wrap(base)); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.bg));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24), dp(20), dp(12), dp(8));
        TextView title = new TextView(this, null, 0, R.style.Text_Title);
        title.setText("Papelera de spam");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, tlp);
        TextView clear = new TextView(this, null, 0, R.style.Text_Meta);
        clear.setText("Vaciar");
        clear.setTextColor(getColor(R.color.danger));
        clear.setPadding(dp(12), dp(12), dp(12), dp(12));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmClear(); }
        });
        header.addView(clear);
        root.addView(header);

        TextView info = new TextView(this, null, 0, R.style.Text_Meta);
        info.setText("Las llamadas de números marcados como spam se rechazan solas y aparecen aquí.");
        info.setPadding(dp(24), 0, dp(24), dp(8));
        root.addView(info);

        ListView list = new ListView(this);
        list.setDivider(null);
        adapter = new Adapter();
        list.setAdapter(adapter);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(list, llp);

        empty = new TextView(this, null, 0, R.style.Text_Meta);
        empty.setText("No hay llamadas de spam.");
        empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.addView(empty);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Util.ACTION_UPDATED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updated, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updated, f);
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(updated); } catch (Exception ignored) { }
    }

    private void load() {
        new Thread(new Runnable() {
            @Override public void run() {
                final List<Db.Call> list = Db.get(SpamActivity.this).allSpam();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        items.clear();
                        items.addAll(list);
                        adapter.notifyDataSetChanged();
                        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        }).start();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Vaciar papelera")
                .setMessage("Se borrará el registro de llamadas de spam. Los números seguirán bloqueados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Vaciar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Db.get(SpamActivity.this).clearSpam();
                        load();
                    }
                }).show();
    }

    private void entryMenu(final Db.Call c) {
        new AlertDialog.Builder(this)
                .setTitle(TextUtils.isEmpty(c.name) ? Util.number(this, c.number) : c.name)
                .setItems(new String[]{"Quitar de spam (desbloquear)", "Llamar"},
                        new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                if (w == 0) {
                                    Util.removeSpam(SpamActivity.this, c.number);
                                    android.widget.Toast.makeText(SpamActivity.this, "Desbloqueado", android.widget.Toast.LENGTH_SHORT).show();
                                } else {
                                    SimInfo.call(SpamActivity.this, c.number);
                                }
                            }
                        }).show();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.row_call, parent, false);
            final Db.Call c = items.get(i);
            ((android.widget.ImageView) v.findViewById(R.id.dir)).setImageResource(R.drawable.ic_in);
            String formatted = Util.number(SpamActivity.this, c.number);
            ((TextView) v.findViewById(R.id.title)).setText(TextUtils.isEmpty(c.name) ? formatted : c.name);
            ((TextView) v.findViewById(R.id.sub)).setText("Rechazada  ·  " + Util.time(c.startMs));
            ((TextView) v.findViewById(R.id.dur)).setText("");
            v.findViewById(R.id.sep).setVisibility(View.VISIBLE);
            v.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { entryMenu(c); }
            });
            return v;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

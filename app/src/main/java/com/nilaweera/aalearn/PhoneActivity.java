package com.nilaweera.aalearn;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Phone side. Typing on a head unit is miserable, so the URL is set here. */
public class PhoneActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(this);
        label.setText(R.string.start_url_label);
        col.addView(label);

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(Prefs.startUrl(this));
        col.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);

        Button mobile = new Button(this);
        mobile.setText(R.string.hint_mobile);
        mobile.setOnClickListener(v -> input.setText(Prefs.MOBILE));
        presets.addView(mobile);

        Button tv = new Button(this);
        tv.setText(R.string.hint_tv);
        tv.setOnClickListener(v -> input.setText(Prefs.TV));
        presets.addView(tv);

        col.addView(presets);

        Button save = new Button(this);
        save.setText(R.string.save);
        save.setOnClickListener(v -> {
            Prefs.setStartUrl(this, input.getText().toString().trim());
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });
        col.addView(save);

        Button overlay = new Button(this);
        overlay.setText(R.string.grant_overlay);
        overlay.setOnClickListener(v -> requestOverlay());
        col.addView(overlay);

        TextView note = new TextView(this);
        note.setText(getString(R.string.setup_notes));
        note.setPadding(0, pad, 0, 0);
        col.addView(note);

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_needed, Toast.LENGTH_LONG).show();
        }
    }

    /** The car window is drawn as an overlay, so this permission is required. */
    private void requestOverlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_ok, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
    }
}

package net.complynx.lightcontroller;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

public class Settings extends AppCompatActivity {
    private static final String TAG="CLX.Settings";
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        settings = getSharedPreferences("settings", MODE_PRIVATE);

        final TextInputEditText ll = findViewById(R.id.localLink);
        ll.setText(settings.getString("Local link", "/ubus"));
        final TextInputEditText rl = findViewById(R.id.remoteLink);
        rl.setText(settings.getString("Remote link", "https://complynx.net/rgbdriver/ubus"));
        final TextInputEditText usr = findViewById(R.id.User);
        usr.setText(settings.getString("User", "rgb"));
        final TextInputEditText pass = findViewById(R.id.Password);
        pass.setText(settings.getString("Password", ""));
        final Context ctx = this;

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences.Editor sed = settings.edit();
                sed.putString("User", usr.getText().toString());
                sed.putString("Password", pass.getText().toString());
                sed.putString("Remote link", rl.getText().toString());
                sed.putString("Local link", ll.getText().toString());
                sed.apply();
                Intent intent = new Intent(ctx, Requester.class);
                intent.putExtra("T", Requester.UPDATE_STATE_TEST);
                startService(intent);
                finish();
            }
        });
    }

}

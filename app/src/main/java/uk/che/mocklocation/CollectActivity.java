package uk.che.mocklocation;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import uk.che.mocklocation.services.GpsCollectionService;

public class CollectActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collect);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);

        final Button startButton = (Button) findViewById(R.id.button_start_collection);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!GpsCollectionService.isRunning()) {
                    startService(new Intent(CollectActivity.this, GpsCollectionService.class));
                    Toast.makeText(CollectActivity.this, "Collecting in background.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    stopService(new Intent(CollectActivity.this, GpsCollectionService.class));
                    startButton.setText(R.string.title_activity_collect);
                }
            }
        });

        if (GpsCollectionService.isRunning())
            startButton.setText("Stop Collection");

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        final Resources res = getResources();
        updateWarningText((TextView) findViewById(R.id.text_view_warning), res);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Resources res = getResources();
        updateWarningText((TextView) findViewById(R.id.text_view_warning), res);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWarningText(TextView warningTextView, Resources res) {
        int rate = Integer.parseInt(prefs.getString(res.getString(R.string.pref_collect_rate), "1"));

        if (rate == 1)
            warningTextView.setText(R.string.collection_warning_one_second);
        else
            warningTextView.setText(String.format(res.getString(R.string.collection_warning), rate));
    }
}

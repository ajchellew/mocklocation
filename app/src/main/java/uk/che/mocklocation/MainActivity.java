package uk.che.mocklocation;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.model.LatLng;

import java.io.File;

import uk.che.common.utils.NumberUtils;
import uk.che.common.utils.StringUtils;
import uk.che.mocklocation.services.PlaybackService;
import uk.che.mocklocation.utils.file.FileDialog;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    public static final File FilesPath = new File(Environment.getExternalStorageDirectory() + File.separator + "NMEA_LOG");

    private static final int PLACE_PICKER_REQUEST = 1;

    private static FileDialog fileDialog;

    private String selectedFile = null;
    private String selectedLocation = null;

    private SharedPreferences prefs;
    private TextView selectedFileView;
    private TextView selectedLocationView;

    private ImageButton mockFileStartButton;
    private ImageButton mockLocationStartButton;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectedFileView = (TextView) findViewById(R.id.text_view_selected_file);
        selectedLocationView = (TextView) findViewById(R.id.text_view_selected_location);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final Resources res = getResources();

        mockFileStartButton = (ImageButton) findViewById(R.id.button_mock_file_start);
        mockFileStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isMockLocationEnabled())
                    showOptions();
                else {

                    if (!PlaybackService.isRunning()) {
                        if (selectedFile != null) {
                            Intent intent = new Intent(MainActivity.this, PlaybackService.class);
                            intent.putExtra("file", selectedFile);
                            startService(intent);
                            Toast.makeText(MainActivity.this, "Mocking Location. Swipe away notification to stop", Toast.LENGTH_SHORT).show();
                            finish();
                        } else
                            Toast.makeText(MainActivity.this, "Select file first", Toast.LENGTH_SHORT).show();
                    } else {
                        stopService(new Intent(MainActivity.this, PlaybackService.class));
                        updateButtonIcon();
                    }
                }
            }
        });

        mockLocationStartButton = (ImageButton) findViewById(R.id.button_mock_simple_location_start);
        mockLocationStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isMockLocationEnabled())
                    showOptions();
                else {

                    if (!PlaybackService.isRunning()) {
                        if (selectedLocation != null) {
                            Intent intent = new Intent(MainActivity.this, PlaybackService.class);
                            intent.putExtra("location", selectedLocation);
                            startService(intent);
                            Toast.makeText(MainActivity.this, "Mocking Location. Swipe away notification to stop", Toast.LENGTH_SHORT).show();
                            finish();
                        } else
                            Toast.makeText(MainActivity.this, "Select location first", Toast.LENGTH_SHORT).show();
                    } else {
                        stopService(new Intent(MainActivity.this, PlaybackService.class));
                        updateButtonIcon();
                    }
                }
            }
        });

        ImageButton selectFileButton = (ImageButton) findViewById(R.id.button_select_nmea_file);
        selectFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fileDialog.showDialog();
            }
        });

        ImageButton selectLocationButton = (ImageButton) findViewById(R.id.button_select_location);
        selectLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlacePicker.IntentBuilder builder = new PlacePicker.IntentBuilder();
                try {
                    startActivityForResult(builder.build(MainActivity.this), PLACE_PICKER_REQUEST);
                } catch (GooglePlayServicesRepairableException e) {
                    //e.printStackTrace();
                } catch (GooglePlayServicesNotAvailableException e) {
                    //e.printStackTrace();
                }
            }
        });

        ImageButton inputLocationButton = (ImageButton) findViewById(R.id.button_input_location);
        inputLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                float dpi = getResources().getDisplayMetrics().density;
                int padding = (int) (19*dpi);

                final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.AppTheme_Dialog);
                builder.setTitle("Input Location");
                final EditText inputEditText = new EditText(MainActivity.this);
                inputEditText.setHint("Latitude,Longitude");
                builder.setView(inputEditText, padding, 0, padding, 0);

                builder.setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        String input = String.valueOf(inputEditText.getText());
                                        if (!StringUtils.isNullOrEmpty(input) && input.contains(",")) {
                                            String[] split = input.split(",", 2);
                                            int latitude = Integer.parseInt(split[0]);
                                            int longitude = Integer.parseInt(split[1]);
                                            selectedLocation = NumberUtils.toFiveDecimalPlaces(latitude) + "," + NumberUtils.toFiveDecimalPlaces(longitude);
                                            selectedLocationView.setText(selectedLocation);
                                            savePreference(res.getString(R.string.pref_selected_location), selectedLocation);
                                        }
                                        imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
                                    }
                                })
                        .setNegativeButton("Cancel",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        imm.hideSoftInputFromWindow(inputEditText.getWindowToken(), 0);
                                        dialog.cancel();
                                    }
                                });

                builder.create().show();

                inputEditText.requestFocus();
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
            }
        });

        fileDialog = new FileDialog(this, FilesPath);
        fileDialog.setFileEndsWith(".txt");
        fileDialog.addFileListener(new FileDialog.FileSelectedListener() {
            public void fileSelected(File file) {
                Log.d(TAG, "selected file " + file.toString());
                if (file.exists()) {
                    savePreference(res.getString(R.string.pref_selected_file), file.toString());
                    selectedFile = file.toString();
                    displaySelectedFile();
                }
            }
        });
    }

    private void savePreference(String pref, String value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(pref, value);
        editor.apply();
    }

    private void showOptions() {
        Toast.makeText(MainActivity.this, "Error: Mock Location must be enabled in Developer Options!", Toast.LENGTH_SHORT).show();
        //Intent intent = new Intent(Settings.ACTION_SETTINGS);
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Resources res = getResources();
        selectedFile = prefs.getString(res.getString(R.string.pref_selected_file), null);
        displaySelectedFile();
        selectedLocation = prefs.getString(res.getString(R.string.pref_selected_location), null);
        selectedLocationView.setText(selectedLocation);

        updateButtonIcon();
    }

    private void updateButtonIcon() {
        if (!isMockLocationEnabled()) {
            mockFileStartButton.setImageResource(R.drawable.ic_location_disabled_black_24dp);
            mockLocationStartButton.setImageResource(R.drawable.ic_location_disabled_black_24dp);
        } else {
            if (PlaybackService.isRunning()) {
                mockFileStartButton.setImageResource(R.drawable.ic_stop_black_24dp);
                mockLocationStartButton.setImageResource(R.drawable.ic_stop_black_24dp);
            } else {
                mockFileStartButton.setImageResource(R.drawable.ic_place_black_24dp);
                mockLocationStartButton.setImageResource(R.drawable.ic_place_black_24dp);
            }
        }
    }

    private void displaySelectedFile() {
        if (selectedFile != null && !selectedFile.equals("")) {
            File file = new File(selectedFile);
            if (file.exists()) {
                selectedFileView.setText(file.getName());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_collect:
                startActivity(new Intent(this, CollectActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(data, this);
                LatLng latLon = place.getLatLng();
                selectedLocation = NumberUtils.toFiveDecimalPlaces(latLon.latitude) + "," + NumberUtils.toFiveDecimalPlaces(latLon.longitude);
                selectedLocationView.setText(selectedLocation);
                Resources res = getResources();
                savePreference(res.getString(R.string.pref_selected_location), selectedLocation);
            }
        }
    }

    public boolean isMockLocationEnabled() {
        boolean isMockLocation = false;
        try
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AppOpsManager opsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                isMockLocation = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), BuildConfig.APPLICATION_ID) == AppOpsManager.MODE_ALLOWED);
            } else
                isMockLocation = !android.provider.Settings.Secure.getString(getContentResolver(), "mock_location").equals("0");
        }
        catch (Exception e) {
            return isMockLocation;
        }

        return isMockLocation;
    }
}

package uk.che.mocklocation.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import uk.che.common.utils.StringUtils;
import uk.che.mocklocation.MainActivity;
import uk.che.mocklocation.R;
import uk.che.mocklocation.utils.NmeaParser;

public class GpsCollectionService extends Service implements GpsStatus.NmeaListener, LocationListener {

    private static final String TAG = GpsCollectionService.class.getSimpleName();

    private static final int NOTIFICATION_CODE = 999;
    private LocationManager locationManager;
    private NotificationManager notificationManager;

    private final SimpleDateFormat DateFormatter = new SimpleDateFormat("dd-MM-yyyy--HH-mm-ss");

    private FileWriter logWriter;
    private BufferedWriter out;

    private static boolean isRunning = false;

    private boolean addMarker = false;
    private boolean lookupMarker = false;
    private List<String> markers = new ArrayList<String>();
    private Hashtable<String, String> markerAddresses = new Hashtable<String, String>();

    private NmeaParser nmeaParser = new NmeaParser();
    private Geocoder geocoder;

    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "service start command - intent is " + intent);

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("cancel"))
                stopSelf();
            else if (intent.getAction().equals("marker"))
                addMarker = true;
        } else
            isRunning = true;

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "service onCreate");

        File dir = MainActivity.FilesPath;
        if (!dir.exists())
            dir.mkdirs();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int recordRate = Integer.parseInt(prefs.getString(getResources().getString(R.string.pref_collect_rate), "1"));
        lookupMarker = prefs.getBoolean(getResources().getString(R.string.pref_lookup_markers), false);

        Date now = new Date();
        String newFilePath = MainActivity.FilesPath + "/" + DateFormatter.format(now) + ".txt";
        File file = new File(newFilePath);
        try {

            if (file.createNewFile()) {
                logWriter = new FileWriter(file);
                out = new BufferedWriter(logWriter);

                out.write("# started logging - " + DateFormatter.format(now));
                out.write("\r\n");
                out.write("# record rate per second: " + recordRate);
                out.write("\r\n");
            }

            createNotification();


        } catch (IOException e) {
            Log.e(TAG, "failed to start", e);
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        locationManager.addNmeaListener(this);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, recordRate * 1000, 1, this);

        geocoder = new Geocoder(this, Locale.ENGLISH);
    }

    private void createNotification() {

        Intent notificationCancelIntent = new Intent(this, GpsCollectionService.class);
        notificationCancelIntent.setAction("cancel");
        PendingIntent deletePendingIntent =
                PendingIntent.getService(this, 0, notificationCancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notificationMarkerIntent = new Intent(this, GpsCollectionService.class);
        notificationMarkerIntent.setAction("marker");
        PendingIntent markerPendingIntent =
                PendingIntent.getService(this, 1, notificationMarkerIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPendingIntent =
                PendingIntent.getActivity(this, 2, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentIntent(mainPendingIntent)
                .setDeleteIntent(deletePendingIntent)
                .setSmallIcon(R.drawable.ic_place_black_24dp)
                .setContentTitle(getResources().getString(R.string.app_name))
                .setContentText("GPS Collection running")
                .addAction(0, "Add Marker", markerPendingIntent)
                .addAction(0, "Stop", deletePendingIntent)
                .build();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //notificationManager.notify(NOTIFICATION_CODE, notification);

        startForeground(NOTIFICATION_CODE, notification);
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {

        if (out != null) {
            try {
                if (!nmea.startsWith("$PGLOR") && !nmea.startsWith("$QZGSA")) {
                    out.write(nmea);

                    if (addMarker && nmea.startsWith("$GPRMC"))
                        writeMarker(nmea);
                }
            } catch (IOException e) {
                Log.e(TAG, "failed to write nmea line", e);
            }
        }
    }

    private void writeMarker(String nmea) throws IOException {
        NmeaParser.GPSPosition position = nmeaParser.parse(nmea);
        if (position.lat != 0.0f || position.lon != 0.0f) {

            int index = markers.size();

            String posStr = position.lat + "," + position.lon;
            markers.add(posStr);

            out.write("# marker " + markers.size() + " - " + posStr);
            out.write("\r\n");

            if (lookupMarker)
                doLookupMarker(String.valueOf(index), position.lat, position.lon);

            Toast.makeText(this, "Marker added at '" + posStr + "'", Toast.LENGTH_SHORT).show();
            addMarker = false;
        }
    }

    private void doLookupMarker(String key, double lat, double lon) throws IOException {
        new ReverseGeocodeTask().execute(Double.valueOf(key), lat, lon);
    }

    private class ReverseGeocodeTask extends AsyncTask<Double, Void, Void> {

        protected Void doInBackground(Double... params) {

            List<Address> addresses = null;
            try {
                String key = String.valueOf(params[0].intValue());
                addresses = geocoder.getFromLocation(params[1], params[2], 1);

                if (addresses != null) {
                    Address returnedAddress = addresses.get(0);

                    String address = null;

                    if (!StringUtils.isNullOrEmpty(returnedAddress.getFeatureName()))
                        address = appendAddress(address, returnedAddress.getFeatureName());

                    if (!StringUtils.isNullOrEmpty(returnedAddress.getThoroughfare()))
                        address = appendAddress(address, returnedAddress.getThoroughfare());

                    if (!StringUtils.isNullOrEmpty(returnedAddress.getSubLocality()))
                        address = appendAddress(address, returnedAddress.getSubLocality());
                    else if (!StringUtils.isNullOrEmpty(returnedAddress.getLocality()))
                        address = appendAddress(address, returnedAddress.getLocality());

                    if (!StringUtils.isNullOrEmpty(returnedAddress.getPostalCode()))
                        address = appendAddress(address, returnedAddress.getPostalCode());

                    if (address == null)
                        address = "Not resolved";

                    markerAddresses.put(key, address);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        private String appendAddress(String address, String addressPart) {

            if (address == null)
                address = addressPart;
            else
                address = address + ", " + addressPart;

            return address;
        }
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "on service destroy");

        if (notificationManager != null)
            notificationManager.cancel(NOTIFICATION_CODE);

        if (locationManager != null
                && (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationManager.removeUpdates(this);
            locationManager.removeNmeaListener(this);
        }

        if (out != null) {
            try {

                writeMarkerDescriptions();

                out.write("# finished logging - " + DateFormatter.format(new Date()));
                out.close();
                logWriter.close();
                out = null;
                logWriter = null;

                // make the media scanner parse the directory so that when you connect to a PC the files will show up
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, Uri.parse("file://"
                        + MainActivity.FilesPath)));

            } catch (IOException e) {
                Log.e(TAG, "failed to destroy", e);
            }
        }

        isRunning = false;

        super.onDestroy();
    }

    private void writeMarkerDescriptions() throws IOException {
        if (lookupMarker && markers.size() > 0) {
            for (int i = 0; i < markers.size(); i++) {
                String key = String.valueOf(i);
                if (markerAddresses.containsKey(key))
                    out.write("# marker " + (i+1) + " - " + markerAddresses.get(key));
                else
                    out.write("# marker " + (i+1) + " - Unresolved location");
                out.write("\r\n");
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "location changed - " + location.toString());
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }
}

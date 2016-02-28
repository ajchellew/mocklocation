package uk.che.mocklocation.utils;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import uk.che.common.utils.StringUtils;
import uk.che.mocklocation.services.PlaybackService;

public class MockLocationProvider {

    private static final String TAG = "MockLocationProvider";

    private final String providerName;
    private final Context ctx;

    private AsyncTask<String, Location, Void> backgroundTask;
    private boolean paused = false;

    private int playbackMultiplier;

    public MockLocationProvider(Context ctx) {
        this(LocationManager.GPS_PROVIDER, ctx, 1);
    }

    public MockLocationProvider(String name, Context ctx, int playbackMultiplier) {
        this.providerName = name;
        this.ctx = ctx;
        this.playbackMultiplier = playbackMultiplier;

        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);
        lm.addTestProvider(providerName, false, false, false, false, false,
                true, true, 0, 5);
        lm.setTestProviderEnabled(providerName, true);
    }

    public void pushLocation(double lat, double lon, float speedMs) {

        Location mockLocation = new Location(providerName);
        mockLocation.setLatitude(lat);
        mockLocation.setLongitude(lon);
        mockLocation.setSpeed(speedMs);
        mockLocation.setTime(System.currentTimeMillis());
        mockLocation.setAltitude(0);

        makeLocationComplete(mockLocation);

        pushLocation(mockLocation);
    }

    public void pushLocation(Location mockLocation) {
        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);

        try {
            lm.setTestProviderLocation(providerName, mockLocation);
        } catch (SecurityException ex) {

        }
    }

    public void startNmea(String nmeaTextfile) {

        File file = new File(nmeaTextfile);

        if (file.exists()) {

            backgroundTask = new ReadNMEAFileTask(file);
            backgroundTask.execute();

        } else {
            Toast.makeText(ctx, "Failed to start MockLocation - NMEA file not found", Toast.LENGTH_SHORT).show();
        }
    }

    public void start(String location) {

        if (!StringUtils.isNullOrEmpty(location)) {

            backgroundTask = new SimpleLocationTask(location);
            backgroundTask.execute();

        } else {
            Toast.makeText(ctx, "Failed to start MockLocation - location is empty", Toast.LENGTH_SHORT).show();
        }
    }

    public void togglePauseNmea() {
        paused = !paused;
    }

    public void shutdown() {

        if (backgroundTask != null)
            backgroundTask.cancel(true);

        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);
        lm.removeTestProvider(providerName);
    }

    private void makeLocationComplete(Location mockLocation) {

        mockLocation.setAccuracy(providerName.equals(LocationManager.NETWORK_PROVIDER) ? 100.0f : 5f);

        Method locationJellyBeanFixMethod = null;
        try {
            locationJellyBeanFixMethod = Location.class.getMethod("makeComplete");
            if (locationJellyBeanFixMethod != null) {
                locationJellyBeanFixMethod.invoke(mockLocation);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    private class SimpleLocationTask extends AsyncTask<String, Location, Void> {

        private final double latitude;
        private final double longitude;

        public SimpleLocationTask(String location){
            String[] split = location.split(",");
            this.latitude = Double.parseDouble(split[0]);
            this.longitude = Double.parseDouble(split[1]);
        }

        protected void onPreExecute(){

        }

        @Override
        protected Void doInBackground(String... params) {
            try {

                while (!paused) {

                    Location mockLocation = new Location(providerName);
                    mockLocation.setLatitude(latitude);
                    mockLocation.setLongitude(longitude);
                    mockLocation.setAltitude(32);
                    mockLocation.setSpeed(5);  // knots to M/S
                    mockLocation.setTime(System.currentTimeMillis());
                    mockLocation.setBearing(0);
                    makeLocationComplete(mockLocation);

                    publishProgress(mockLocation);
                    Thread.sleep((600 + ((1 - 1) * 1000)) / playbackMultiplier); // magic number 600 assumes a delay in processing a line as it seemed too slow
                }

            } catch (InterruptedException e) {
                Log.d(TAG, "Task interrupted");
            }
            return null;
        }

        protected void onProgressUpdate(Location... progress) {
            // done on the UI thread
            pushLocation(progress[0]);
        }

        protected void onPostExecute(Void result)    {
            Intent endIntent = new Intent(ctx, PlaybackService.class);
            endIntent.setAction("ended");
            ctx.startService(endIntent);
        }
    }

    private class ReadNMEAFileTask extends AsyncTask<String, Location, Void> {

        private final File file;

        public ReadNMEAFileTask(File file){
            this.file = file;
        }

        protected void onPreExecute(){

        }

        @Override
        protected Void doInBackground(String... params) {
            try {

                int rate = 1;

                NmeaParser nmeaParser = new NmeaParser();

                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {

                    if (line.startsWith("#") && line.contains("record rate"))
                        rate = Integer.parseInt(line.substring(line.lastIndexOf(' ')+1));

                    // TODO could add pause into the txt file

                    else if (line.startsWith("$GPRMC"))       // need this sentence to get speed
                    {
                        NmeaParser.GPSPosition pos = nmeaParser.parse(line);
                        if (pos.lat != 0.0f || pos.lon != 0.0f) {
                            do {
                                Location mockLocation = new Location(providerName);
                                mockLocation.setLatitude(pos.lat);
                                mockLocation.setLongitude(pos.lon);
                                mockLocation.setAltitude(pos.altitude);
                                mockLocation.setSpeed((float) (pos.velocity * 0.51444));  // knots to M/S
                                mockLocation.setTime(System.currentTimeMillis());
                                mockLocation.setBearing(pos.dir);
                                makeLocationComplete(mockLocation);

                                publishProgress(mockLocation);
                                Thread.sleep((600 + ((rate - 1) * 1000)) / playbackMultiplier); // magic number 600 assumes a delay in processing a line as it seemed too slow
                            }
                            while (paused);   // pauses in location but continues to update location as if being spotted there
                        }
                    }
                }
                br.close();

            } catch (InterruptedException e) {
                Log.d(TAG, "Task interrupted");
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found", e);
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
            }
            return null;
        }

        protected void onProgressUpdate(Location... progress) {
            // done on the UI thread
            pushLocation(progress[0]);
        }

        protected void onPostExecute(Void result)    {
            Toast.makeText(ctx, "Reached end of playback file", Toast.LENGTH_LONG).show();

            Intent endIntent = new Intent(ctx, PlaybackService.class);
            endIntent.setAction("ended");
            ctx.startService(endIntent);
        }
    }
}
package uk.che.mocklocation.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;

import uk.che.mocklocation.R;
import uk.che.mocklocation.utils.MockLocationProvider;

public class PlaybackService extends Service {

    private static final String TAG = PlaybackService.class.getSimpleName();

    private static final int NOTIFICATION_CODE = 888;

    private static final int RUNNING = 1;
    private static final int PAUSED = 2;
    private static final int ENDED = 3;
    private static final int FAILED = 4;

    private NotificationManager notificationManager;

    private MockLocationProvider mock;

    private boolean paused = false;

    private static boolean isRunning = false;

    private int playbackMultiplier = 1;
    /*final Messenger mMessenger = new Messenger(new IncomingHandler());

    class IncomingHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ActionServiceActivity.PAUSE_RESUME_MOCK:
                    togglePause();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }*/

    private void togglePause() {
        mock.togglePauseNmea();
        paused = !paused;
        //cancelNotification();
        createNotification(paused ? PAUSED : RUNNING);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // mMessenger.getBinder();
    }

    public static boolean isRunning() {
        return isRunning;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "service start command - intent is " + intent );

        if (!isRunning) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Resources res = getResources();

            int state = FAILED;

            if (intent != null) {
                if (intent.hasExtra("file")) {
                    String file = intent.getStringExtra("file");
                    File playbackFile = new File(file);
                    if (playbackFile.exists()) {
                        playbackMultiplier = prefs.getInt(res.getString(R.string.pref_playback_multiplier), 1);
                        mock = new MockLocationProvider(LocationManager.GPS_PROVIDER, this, playbackMultiplier);
                        mock.startNmea(playbackFile.getAbsolutePath());
                        state = RUNNING;
                    }
                } else {
                    String location = intent.getStringExtra("location");
                    mock = new MockLocationProvider(LocationManager.GPS_PROVIDER, this, 1);
                    mock.start(location);
                    state = RUNNING;
                }
            }
            createNotification(state);

            isRunning = true;
        }

        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("cancel"))
                stopSelf();
            else if (intent.getAction().equals("pauseresume"))
                togglePause();
            else if (intent.getAction().equals("ended")) {
                //cancelNotification();
                createNotification(ENDED);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void createNotification(int state) {

        Intent notificationCancelIntent = new Intent(this, PlaybackService.class);
        notificationCancelIntent.setAction("cancel");
        PendingIntent deletePendingIntent =
                PendingIntent.getService(this, 0, notificationCancelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        builder.setDeleteIntent(deletePendingIntent)
            .setSmallIcon(getNotificationIcon(state))
            .setContentTitle(getResources().getString(R.string.app_name))
            .setContentText(getNotificationText(state));

        if (state == RUNNING || state == PAUSED) {
            Intent notificationPauseResumeIntent = new Intent(this, PlaybackService.class);
            notificationPauseResumeIntent.setAction("pauseresume");
            PendingIntent pauseResumePendingIntent =
                    PendingIntent.getService(this, 1, notificationPauseResumeIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT);

            builder.addAction(0, state == RUNNING ? "Pause" : "Resume", pauseResumePendingIntent);
        }

        builder.addAction(0, state == RUNNING ||  state == PAUSED ? "Stop" : "Close", deletePendingIntent);

        Notification notification = builder.build();

        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_CODE, notification);
    }

    private int getNotificationIcon(int state) {
        switch (state) {
            default:
                return R.drawable.ic_place_black_24dp;
            case RUNNING:
                return R.drawable.ic_play_arrow_black_24dp;
            case PAUSED:
                return R.drawable.ic_pause_black_24dp;
            case ENDED:
                return R.drawable.ic_stop_black_24dp;
        }
    }

    private String getNotificationText(int state) {
        switch (state) {
            default:
            case RUNNING:
                return playbackMultiplier == 1 ? "Playback running" : "Playback running (x" + playbackMultiplier + " speed)";
            case PAUSED:
                return "Playback paused";
            case ENDED:
                return "End of playback file";
            case FAILED:
                return "Error: Failed to Start";
        }
    }

    @Override
    public void onDestroy() {

        Log.d(TAG, "on service destroy");

        cancelNotification();

        if (mock != null)
            mock.shutdown();

        isRunning = false;

        super.onDestroy();
    }

    private void cancelNotification() {
        if (notificationManager != null)
            notificationManager.cancel(NOTIFICATION_CODE);
    }
}

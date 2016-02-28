package uk.che.mocklocation.services;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;

public class ActionServiceActivity extends Activity {

    public static final String ACTION_ID_NAME = "ActionID";

    public static final int CANCEL_COLLECT = 1;
    public static final int CANCEL_MOCK = 2;
    public static final int PAUSE_RESUME_MOCK = 3;

    private int sendOnBind = -1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int id = getIntent().getIntExtra(ACTION_ID_NAME, -1);

        switch (id) {
            case CANCEL_COLLECT:
                stopService(new Intent(this, GpsCollectionService.class));
                break;
            case CANCEL_MOCK:
                stopService(new Intent(this, PlaybackService.class));
                break;
            case PAUSE_RESUME_MOCK:
                sendOnBind = PAUSE_RESUME_MOCK;
                checkIfMockServiceIsRunning();
                break;
        }

        finish();
    }

    private boolean mIsBound;
    private Messenger mService = null;
    //private final Messenger mMessenger = new Messenger(new IncomingHandler());

    private void checkIfMockServiceIsRunning() {
        //If the service is running when the activity starts, we want to automatically bind to it.
        if (PlaybackService.isRunning()) {
            doBindMockService();
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            if (sendOnBind != -1){
                try {
                    mService.send(Message.obtain(null, sendOnBind));
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            //textStatus.setText("Disconnected.");
        }
    };

    void doBindMockService() {
        bindService(new Intent(this, PlaybackService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        //textStatus.setText("Binding.");
    }

    void doUnbindMockService() {
        if (mIsBound) {
            // If we have received the service, and hence registered with it, then now is the time to unregister.
            /*if (mService != null) {
                try {
                    Message msg = Message.obtain(null, MyService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            }*/
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            //textStatus.setText("Unbinding.");
        }
    }

    @Override
    protected void onDestroy() {
        doUnbindMockService();
        super.onDestroy();
    }
}
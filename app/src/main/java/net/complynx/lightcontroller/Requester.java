package net.complynx.lightcontroller;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

public class Requester extends Service {
    public static final int NOT_DEFINED=0;
    public static final int UPDATE_STATE=1;
    public static final int UPDATE_STATE_TEST=1;
    private static final String TAG="CLX.Requester";
    private ServiceHandler mServiceHandler;
    private SharedPreferences settings;
    private SharedPreferences state;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        ServiceHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "Message handler...");
            String key = settings.getString("Session Key", "");
            String url = settings.getString("Remote link", "");

            if(url.equals("")){
                Log.d(TAG, "Needs setting!");
                stopSelf(msg.arg1);
                return;
            }
            // Stop the service using the startId, so that we don't stop
            // the service in the middle of handling another job
            stopSelf(msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Created");
        settings = getSharedPreferences("settings", MODE_PRIVATE);
        state = getSharedPreferences("state", MODE_PRIVATE);
        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        HandlerThread thread = new HandlerThread("ServiceStartArguments",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceHandler = new ServiceHandler(thread.getLooper());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int type = intent.getIntExtra("T", NOT_DEFINED);
        Log.d(TAG, "service starting " + type);

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.arg2 = type;
        msg.setData(intent.getBundleExtra("data"));
        mServiceHandler.sendMessage(msg);

        // If we get killed, after returning from here, restart
        return START_STICKY;
    }



    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed");
        // Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
    }

}

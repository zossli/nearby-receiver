package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.connection.Payload;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.UUID;


public class NearbyService extends ConnectionService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private String TAG = "NearbyService";
    private static final String SERVICE_ID = "li.zoss.bfh.bsc";
    private final String NAME = "Receiver " + UUID.randomUUID();
    private NotificationManager mNotificationManager;
    private String CHANNEL_ID_DEFAULT = "nearby_Information_System_Notification";
    private NearbyBroadcastReceiver nearbyBroadcastReceiver;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (nearbyBroadcastReceiver == null) nearbyBroadcastReceiver = new NearbyBroadcastReceiver(this);
        IntentFilter intentFilter = new IntentFilter(NearbyBroadcastReceiver.INTENT_ACTION_REQUEST_STOP);
        registerReceiver(nearbyBroadcastReceiver, intentFilter);

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);


        // The user-visible name of the channel.
        CharSequence name = "Nächster Halt";
        // The user-visible description of the channel.
        String description = "Meldungen zum nächsten Halt.";
        int importance = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            importance = NotificationManager.IMPORTANCE_HIGH;
        }
        NotificationChannel mChannel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mChannel = new NotificationChannel(CHANNEL_ID_DEFAULT, name, importance);

            // Configure the notification channel.
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.setLightColor(Color.RED);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200});
            mNotificationManager.createNotificationChannel(mChannel);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private void notification(String message, Boolean requestNeeded) {

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT)
                        .setSmallIcon(R.drawable.logoeinfach)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_klein))
                        .setContentTitle("Nächster Halt")
                        .setContentText(message)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0));

        if(requestNeeded)
        {
            Intent intent = new Intent();
            intent.setAction(NearbyBroadcastReceiver.INTENT_ACTION_REQUEST_STOP);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.logoeinfach,"Halt verlangen", pendingIntent);
            mBuilder.addAction(action);
        }

        NotificationManager mNotificationManager= (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);


        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        int mNotificationId = new Date().hashCode();
        Log.i(TAG, "notification: "+mNotificationId);
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }


    @Override
    public IBinder onBind(Intent intent) {
        //TODO for communication return IBinder implementation
        return null;
    }

    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        super.onReceive(endpoint, payload);
        if (payload.getType() == Payload.Type.BYTES) {
            try {
                JSONObject jsonObject = new JSONObject(new String(payload.asBytes()));
                Log.i(TAG, "onReceive: "+jsonObject);
                switch (NotType.valueOf((jsonObject.getString("Type")))){
                    case NEXT_STOP:
                        boolean requestNeeded = jsonObject.get("requestNeeded").toString().toLowerCase().equals("true");
                        Log.i(TAG, "onReceive: requestNeeded was "+requestNeeded +" because True="+jsonObject.get("requestNeeded"));
                        notification((String) jsonObject.get("station"), requestNeeded);
                        break;
                    case DELAY:
                        break;
                    case INFO:
                        break;
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onDestroy() {
        Toast toast = Toast.makeText(this, "Service got killed", Toast.LENGTH_SHORT);
        toast.show();

        if (nearbyBroadcastReceiver != null) unregisterReceiver(nearbyBroadcastReceiver);

        Log.i(TAG, "onDestroy: Killed...");
        super.onDestroy();
    }



    @Override
    protected String getName() {
        return NAME;
    }

    @Override
    protected String getServiceId() {
        return SERVICE_ID;
    }


    @Override
    public void onConnected(final Bundle bundle) {
        Log.i(TAG, "onConnected: ");
        startDiscovering();
    }

    @Override
    public void onConnectionSuspended(final int i) {
        super.onConnectionSuspended(i);
        Log.i(TAG, "onConnectionSuspended: " + i);
    }

    @Override
    public void onConnectionFailed(final ConnectionResult connectionResult) {
        Log.i(TAG, "onConnectionFailed: " + connectionResult);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.i(TAG, "onLowMemory: ");
    }


    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: ");
        return super.onUnbind(intent);
        
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "onConfigurationChanged: ");
        super.onConfigurationChanged(newConfig);
    }

}

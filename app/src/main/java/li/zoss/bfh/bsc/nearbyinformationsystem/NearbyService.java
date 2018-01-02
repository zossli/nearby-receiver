package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.nearby.connection.Payload;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * The Nearby Service is used for implementing the specific functions for the nearby connections.
 */
public class NearbyService extends ConnectionService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private State mState = State.UNKNOWN;
    private String TAG = "NearbyService";

    private static final String SERVICE_ID = "li.zoss.bfh.bsc";
    private final String NAME = "Receiver " + UUID.randomUUID();
    private NotificationManager mNotificationManager;
    private String CHANNEL_ID_DEFAULT = "nearby_Information_System_Notification";
    private NearbyBroadcastReceiver nearbyBroadcastReceiver;


    //Information to the current Train.
    private String trainInfo, trainDirection, trainNextStop;
    private Boolean trainRequestNeeded;

    //View refresh
    private MainActivity viewer;

    /**
     * The default Service onStartCommand
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: state is: "+mState );
            if (nearbyBroadcastReceiver == null)
                nearbyBroadcastReceiver = new NearbyBroadcastReceiver(this);
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

             if(mState.equals(State.CONNECTED)){
                 Log.i(TAG, "onStartCommand: already connected getting train info");
                Set<Endpoint> endpoints = getConnectedEndpoints();
                Endpoint endpoint = endpoints.iterator().next();
                getTrainInfoFromService(endpoint);
            }
        
        return super.onStartCommand(intent, flags, startId);

    }

    private void notification(String station, Boolean requestNeeded, Endpoint endpoint) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this,0,contentIntent,PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID_DEFAULT)
                        .setSmallIcon(R.drawable.logoeinfach)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_klein))
                        .setContentTitle("Nächster Halt")
                        .setContentText(station)
                        .setDefaults(Notification.DEFAULT_ALL)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setContentIntent(resultPendingIntent);

        if (requestNeeded) {
            Intent intent = new Intent();
            intent.putExtra("Endpoint", endpoint.getId());
            intent.putExtra("forStation", station);
            intent.setAction(NearbyBroadcastReceiver.INTENT_ACTION_REQUEST_STOP);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.logoeinfach, "Halt verlangen", pendingIntent);
            mBuilder.addAction(action);
        }

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        // mNotificationId is a unique integer your app uses to identify the
        // notification. For example, to cancel the notification, you can pass its ID
        // number to NotificationManager.cancel().
        int mNotificationId = new Date().hashCode();
        Log.i(TAG, "notification: " + mNotificationId);
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }


    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        super.onReceive(endpoint, payload);
        if (payload.getType() == Payload.Type.BYTES) {
            try {
                JSONObject jsonObject = new JSONObject(new String(payload.asBytes()));
                Log.i(TAG, "onReceive: " + jsonObject);
                switch (NotType.valueOf((jsonObject.getString("Type")))) {
                    case NEXT_STOP:
                        trainRequestNeeded = jsonObject.get("requestNeeded").toString().toLowerCase().equals("true");
                        trainNextStop = (String) jsonObject.get("station");
                        notification(trainNextStop, trainRequestNeeded, endpoint);
                        break;
                    case DELAY:
                        break;
                    case INFO:
                        break;
                    case TRAIN_INFO:
                        trainInfo = jsonObject.getString("trainInfo");
                        trainDirection = jsonObject.getString("direction");
                        trainNextStop = jsonObject.getString("nextStop");
                        Intent intent = new Intent();
                        intent.putExtra("trainInfo", trainInfo);
                        intent.putExtra("trainDirection", trainDirection);
                        intent.putExtra("trainNextStop", trainNextStop);
                        intent.setAction(MainActivity.INTENT_REFRESH_TRAIN_INFO);
                        sendBroadcast(intent);
                        break;
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onDestroy() {
        disconnectFromAllEndpoints();
        if (nearbyBroadcastReceiver != null) unregisterReceiver(nearbyBroadcastReceiver);
        super.onDestroy();
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: did nothing");
        return null;
    }

    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        super.onEndpointConnected(endpoint);
        getTrainInfoFromService(endpoint);
        setState(State.CONNECTED);
        Intent intent = new Intent();
        intent.putExtra("Type", NotType.CONNECTED_TO_SYSTEM);
        intent.setAction(MainActivity.INTENT_REFRESH_TRAIN_CONNECTED);
        sendBroadcast(intent);

    }


    private void getTrainInfoFromService(Endpoint endpoint) {
        JSONObject gTrain = new JSONObject();
        try {
            gTrain.put("Type", NotType.GET_TRAIN);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        send(Payload.fromBytes(gTrain.toString().getBytes()), endpoint.getId());
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        super.onEndpointDisconnected(endpoint);
        setState(State.UNKNOWN);
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
        setState(State.DISCOVERING);
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
    protected void onDiscoveryFailed() {
        super.onDiscoveryFailed();
        if (!isDiscovering()) startDiscovering();
    }

    private enum State {
        DISCOVERING,
        CONNECTED,
        KILLED,
        UNKNOWN
    }

    private void setState(State state) {
        State previousState = mState;
        mState = state;

        switch (state) {
            case DISCOVERING:
                if (previousState.equals(State.UNKNOWN)) {
                    if (!isDiscovering()) {
                        startDiscovering();
                        Log.i(TAG, "setState: Discovering");
                    }
                }
                break;
            case CONNECTED:
                if (previousState.equals(State.DISCOVERING)) {
                    stopDiscovering();
                    Log.i(TAG, "setState: Connected");
                }
                break;
            case UNKNOWN:
                if (!previousState.equals(State.KILLED)) {
                    setState(State.DISCOVERING);
                    Log.i(TAG, "setState: Unknown from connected or discovering. so try to discover again.");
                }
                break;
            case KILLED:
                Log.i(TAG, "setState: KILLED - no idea what happened.");
                break;
        }
    }

}

package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.nearby.connection.Payload;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * The Nearby Service is used for implementing the specific functions for the nearby connections.
 */
public class NearbyService extends ConnectionService {
    private State mState = State.UNKNOWN;
    private String TAG = "NearbyService";

    private static final String SERVICE_ID = "li.zoss.bfh.bsc";
    private final String NAME = "Receiver " + UUID.randomUUID();
    private NotificationManager mNotificationManager;

    private static final String CHANNEL_ID_DEFAULT = "nearby_Information_System_Notification";
    private static final String CHANNEL_ID_DELAY = "nearby_Information_System_Notification_DELAY";
    private static final String CHANNEL_ID_INFO = "nearby_Information_System_Notification_INFO";

    private static final int NOTIFICIATION_ID_NEXT_STOP = 1;
    private static final int NOTIFICIATION_ID_CURRENT_DELAY = 2;

    private SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();

    private NearbyBroadcastReceiver nearbyBroadcastReceiver;
    private boolean playSound = false;

    //Information to the current Train.
    private String trainInfo, trainDirection, trainNextStop, trainCoachInfo, currentDelay, stationInfo,stationNextDep;
    private Boolean trainRequestNeeded;

    private long[] mVibrationOne = new long[]{100, 200, 300, 400, 500, 600};
    private long[] mVibrationTwo = new long[]{100, 200};
    private AudioManager myAudioManager;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: state is: " + mState);

        if (nearbyBroadcastReceiver == null)
            nearbyBroadcastReceiver = new NearbyBroadcastReceiver(this);

        IntentFilter intentFilter = new IntentFilter(NearbyBroadcastReceiver.INTENT_ACTION_REQUEST_STOP);
        registerReceiver(nearbyBroadcastReceiver, intentFilter);

        intentFilter = new IntentFilter(NearbyBroadcastReceiver.INTENT_ACTION_SET_SOUND);
        registerReceiver(nearbyBroadcastReceiver, intentFilter);

        createNotificationChannels();

        checkIfAlreadyConnected();

        return super.onStartCommand(intent, flags, startId);

    }


    private void notificationDefault(String station, Boolean requestNeeded, String stationInfo, Endpoint endpoint) {

        NotificationCompat.Builder mBuilder = createNotiBuilder("Nächster Halt", CHANNEL_ID_DEFAULT, station);

        if (requestNeeded) {
            Intent intent = new Intent();
            intent.putExtra("Endpoint", endpoint.getId());
            intent.putExtra("forStation", station);
            intent.setAction(NearbyBroadcastReceiver.INTENT_ACTION_REQUEST_STOP);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            NotificationCompat.Action action = new NotificationCompat.Action(R.drawable.logoeinfach, "Halt verlangen", pendingIntent);
            mBuilder.addAction(action);
        }
        if (!stationInfo.isEmpty()) {
            mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(station + "\n" + stationInfo)
            );
        }
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICIATION_ID_NEXT_STOP, mBuilder.build());
    }

    private void notificationInfo(String info) {

        NotificationCompat.Builder mBuilder = createNotiBuilder("Zugsinformation", CHANNEL_ID_INFO, info);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(info));

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICIATION_ID_NEXT_STOP, mBuilder.build());
    }

    private void notificationDelay(String currentDelay) {
        NotificationCompat.Builder mBuilder = createNotiBuilder("Verspätungsmeldung", CHANNEL_ID_DELAY, currentDelay);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIFICIATION_ID_CURRENT_DELAY, mBuilder.build());
    }

    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.BYTES) {
            try {
                JSONObject jsonObject = new JSONObject(new String(payload.asBytes()));
                Log.i(TAG, "onReceive: " + jsonObject);
                switch (NotType.valueOf((jsonObject.getString("Type")))) {
                    case PUBLISH_NEXT_STOP:
                        trainRequestNeeded = jsonObject.get("trainNextStopRequestNeeded").toString().toLowerCase().equals("true");
                        trainNextStop = jsonObject.getString("trainNextStop");
                        stationInfo = jsonObject.getString("trainNextStationInfo");
                        stationNextDep = jsonObject.getString("trainNextStationDep");
                        notificationDefault(trainNextStop, trainRequestNeeded, stationInfo, endpoint);
                        sendViewRefresh(endpoint);
                        break;
                    case REQUEST_STOP:
                        break;
                    case PUBLISH_DELAY:
                        currentDelay = jsonObject.getString("trainDelay");
                        notificationDelay(currentDelay);
                        sendViewRefresh(endpoint);
                        break;
                    case PUBLISH_COACH_INFO:
                        trainCoachInfo = jsonObject.getString("trainSpecialCoachInfo");
                        notificationInfo(trainCoachInfo);
                        sendViewRefresh(endpoint);
                        break;
                    case REQUEST_TRAIN_INFO:
                        break;
                    case RESPONSE_TRAIN_INFO:
                        trainInfo = jsonObject.getString("trainInfo");
                        trainDirection = jsonObject.getString("trainDirection");
                        trainNextStop = jsonObject.getString("trainNextStop");
                        stationInfo = jsonObject.getString("trainNextStationInfo");
                        trainRequestNeeded = jsonObject.getBoolean("trainNextStopRequestNeeded");
                        trainCoachInfo = jsonObject.getString("trainSpecialCoachInfo");
                        currentDelay = jsonObject.getString("trainCurrentDelay");
                        sendViewRefresh(endpoint);
                        break;
                    case REQUEST_WITH_SOUND:
                        break;
                    case RESPONSE_WITH_SOUND:
                        playSound = jsonObject.getBoolean("willPlaySound");
                        sendSoundIntent();
                        break;
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (payload.getType() == Payload.Type.STREAM) {
            File file = null;
            try {
                InputStream instream = payload.asStream().asInputStream();
                file = new File(getCacheDir().getParent(), "test.mp3");
                OutputStream outputStream = new FileOutputStream(file);
                IOUtils.copy(instream, outputStream);
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


            try {
                if (myAudioManager == null)
                    myAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                Boolean canPlay = false;
                ArrayList<Integer> allowedDevices = new ArrayList<>();
                allowedDevices.add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP);
                allowedDevices.add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
                allowedDevices.add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES);
                allowedDevices.add(AudioDeviceInfo.TYPE_WIRED_HEADSET);
                AudioDeviceInfo[] devices = myAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
                for (int i = 0; devices.length > i; i++) {
                    for (int device : allowedDevices) {
                        canPlay = device == devices[i].getType();
                        if (canPlay)
                            break;
                    }
                    if (canPlay)
                        break;
                }
                if (canPlay) {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(this, Uri.fromFile(file));
                    mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            mp.start();
                        }
                    });
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            Log.i(TAG, "onCompletion: Player will be deleted");
                            mp.stop();
                            mp.reset();
                            mp.release();
                        }
                    });
                    mediaPlayer.prepareAsync();
                } else {
                    playSound = false;
                    sendSoundIntent();
                    // TODO: Send not only to UI - but also to Sender Device. Fewer Bandwith usage...

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void sendSoundIntent() {
        Intent intent = new Intent();
        intent.putExtra("willPlaySound", playSound);
        intent.setAction(MainActivity.INTENT_PLAY_SOUND);
        sendBroadcast(intent);
    }

    public static File stream2file(InputStream in) throws IOException {
        final File tempFile = File.createTempFile("stream2file", ".tmp");
        tempFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            IOUtils.copy(in, out);
        }
        return tempFile;
    }

    public void sendViewRefresh(Endpoint endpoint) {
        Intent intent = new Intent();
        intent.putExtra("trainInfo", trainInfo);
        intent.putExtra("trainDirection", trainDirection);
        intent.putExtra("trainNextStop", trainNextStop);
        intent.putExtra("trainNextStopRequestNeeded", trainRequestNeeded);
        intent.putExtra("endpointId", endpoint.getId());
        intent.putExtra("trainNextStationInfo", stationInfo);
        intent.putExtra("trainCurrentDelay", currentDelay);
        intent.putExtra("trainSpecialCoachInfo", trainCoachInfo);
        intent.putExtra("trainNextStationDep", stationNextDep);
        intent.setAction(MainActivity.INTENT_REFRESH_TRAIN_INFO);
        sendBroadcast(intent);
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

    }

    private void getTrainInfoFromService(Endpoint endpoint) {
        getTrainInfoFromService(endpoint, false);
    }

    private void getTrainInfoFromService(Endpoint endpoint, Boolean coachInfoAlreadyReceived) {
        JSONObject gTrain = new JSONObject();
        try {
            gTrain.put("Type", NotType.REQUEST_TRAIN_INFO);
            gTrain.put("coachInfoAlreadyReceived", coachInfoAlreadyReceived);
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
        STOPPED, UNKNOWN
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
                        Intent intent = new Intent();
                        intent.putExtra("isConnedted", false);
                        intent.setAction(MainActivity.INTENT_REFRESH_TRAIN_CONNECTED);
                        sendBroadcast(intent);
                    }
                }
                break;
            case CONNECTED:
                if (previousState.equals(State.DISCOVERING)) {
                    stopDiscovering();
                    Log.i(TAG, "setState: Connected");
                    Intent intent = new Intent();
                    intent.putExtra("isConnedted", true);
                    intent.setAction(MainActivity.INTENT_REFRESH_TRAIN_CONNECTED);
                    sendBroadcast(intent);
                }
                break;
            case STOPPED:
                setState(State.STOPPED);
                Log.i(TAG, "setState: Stopped.");
                break;
            case UNKNOWN:
                if (!previousState.equals(State.KILLED) || !previousState.equals(State.STOPPED)) {
                    setState(State.DISCOVERING);
                    Log.i(TAG, "setState: Unknown from connected or discovering. so try to discover again.");
                }
                break;
            case KILLED:
                Log.i(TAG, "setState: KILLED - no idea what happened.");
                break;
        }
    }


    private void createNotificationChannels() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // The user-visible name of the channel.
        CharSequence name = "Nächster Halt";
        // The user-visible description of the channel.
        String description = "Meldungen zum nächsten Halt.";
        int importance = 0;
        importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mChannel = new NotificationChannel(CHANNEL_ID_DEFAULT, name, importance);
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            mChannel.setSound(null, null);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(mVibrationTwo);
            mNotificationManager.createNotificationChannel(mChannel);
        }

        // The user-visible name of the channel.
        name = "Verspätungsminuten";
        // The user-visible description of the channel.
        description = "Die aktuelle Verspätung";
        importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannelDelay = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mChannelDelay = new NotificationChannel(CHANNEL_ID_DELAY, name, importance);
            mChannelDelay.setDescription(description);
            mChannelDelay.enableVibration(true);
            mChannelDelay.setSound(null, null);
            mChannelDelay.setVibrationPattern(mVibrationOne);
            mNotificationManager.createNotificationChannel(mChannelDelay);
        }

        // The user-visible name of the channel.
        name = "Informationen";
        // The user-visible description of the channel.
        description = "Allgemeine Informationen";
        importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannelInfo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mChannelInfo = new NotificationChannel(CHANNEL_ID_INFO, name, importance);
            mChannelInfo.setDescription(description);
            mChannelInfo.enableVibration(true);
            mChannelInfo.setSound(null, null);
            mChannelInfo.setVibrationPattern(mVibrationTwo);
            mNotificationManager.createNotificationChannel(mChannelInfo);
        }
    }

    private void checkIfAlreadyConnected() {
        if (mState.equals(State.CONNECTED)) {
            Log.i(TAG, "onStartCommand: already connected getting train info");
            Set<Endpoint> endpoints = getConnectedEndpoints();
            Endpoint endpoint = endpoints.iterator().next();
            getTrainInfoFromService(endpoint, true);
        }
    }

    private NotificationCompat.Builder createNotiBuilder(String title, String channelId, String content) {
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.logoeinfach)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_klein))
                        .setContentTitle(title)
                        .setContentText(content)
                        .setDefaults(Notification.DEFAULT_VIBRATE)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setContentIntent(resultPendingIntent)
                        .setAutoCancel(true);
        return mBuilder;
    }
}

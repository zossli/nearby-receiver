package li.zoss.bsc.bfh.nearby_receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.util.SimpleArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class MainActivity extends ConnectionsActivity {

    private String TAG = "MainActivity";

    //Used for ConnectionsActivity
    private static final String SERVICE_ID = "li.zoss.bfh.bsc";
    private final String NAME = "Receiver " + UUID.randomUUID();

    //Receive large Data
    private SimpleArrayMap<Long, Payload> incomingPayloads = new SimpleArrayMap<>();
    private SimpleArrayMap<String, String> filePayloadFilenames = new SimpleArrayMap<>();

    //View
    private ImageButton btnconnectToTrain;
    private ProgressBar progressBar;

    private State mState = State.UNKNOWN;
    private boolean googleApiClientIsReady = false;
    private MediaPlayer mAudioPlayer;
    private NotificationManager mNotificationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //View
        btnconnectToTrain = findViewById(R.id.connectToTrain);
        progressBar = findViewById(R.id.progressBar);
        setState(State.UNKNOWN);

        //Add Listener
        btnconnectToTrain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (googleApiClientIsReady) {
                    switch (getState()) {
                        case UNKNOWN:
                        case ERROR:
                        case WAIT_FOR_USER_TO_START:
                            setState(State.DISCOVERING);
                            break;
                        case CONNECTED:
                            setState(State.WAIT_FOR_USER_TO_START);
                            break;
                        default:

                            break;
                    }
                } else {
                    Toast.makeText(getBaseContext(), getString(R.string.dialect_notReady), Toast.LENGTH_LONG).show();
                }
            }
        });
        mNotificationManager = (NotificationManager) getSystemService(this.NOTIFICATION_SERVICE);
        // The id of the channel.
        String id = "fis_notifcation";
        // The user-visible name of the channel.
        CharSequence name = "Fahrgast Informationä";
        // The user-visible description of the channel.
        String description = "Dass du immer informiert bisch über ds momentane Gscheh uf de Gleis.";
        int importance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel mChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mChannel = new NotificationChannel(id, name, importance);

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
    }

    private void notification(String message) {
        String CHANNEL_ID = "fis_notifcation";
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bls)
                        .setContentTitle("Nächster Halt")
                        .setContentText(message);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

// mNotificationId is a unique integer your app uses to identify the
// notification. For example, to cancel the notification, you can pass its ID
// number to NotificationManager.cancel().
        int mNotificationId = 12;
        mNotificationManager.notify(mNotificationId, mBuilder.build());
    }


    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        setState(State.UNKNOWN);
        super.onStop();
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
    public void onConnected(@Nullable Bundle bundle) {
        super.onConnected(bundle);
        googleApiClientIsReady = true;
        setState(State.WAIT_FOR_USER_TO_START);
    }

    @Override
    public void onConnectionSuspended(int reason) {
        super.onConnectionSuspended(reason);
        googleApiClientIsReady = false;
        setState(State.ERROR);
    }


    @Override
    protected void onConnectionInitiated(final Endpoint endpoint, ConnectionInfo connectionInfo) {
        acceptConnection(endpoint);
        /*new AlertDialog.Builder(this)
                    .setTitle("Accept connection to " + connectionInfo.getEndpointName())
                    .setMessage("Confirm the code " + connectionInfo.getAuthenticationToken() + " is also displayed on the other device")
                    .setPositiveButton("Accept", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The user confirmed, so we can accept the connection.
                            acceptConnection(endpoint);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // The user canceled, so we should reject the connection.
                            rejectConnection(endpoint);
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();*/
    }


    @Override
    protected void onDiscoveryFailed() {
        super.onDiscoveryFailed();
        setState(State.ERROR);
    }

    @Override
    protected void onDiscoveryStarted() {
        super.onDiscoveryStarted();

    }

    @Override
    protected void onEndpointConnected(Endpoint endpoint) {
        super.onEndpointConnected(endpoint);
        setState(State.CONNECTED);
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        super.onEndpointDisconnected(endpoint);
        if (!getState().equals(State.WAIT_FOR_USER_TO_START)) {
            setState(State.DISCOVERING);
        } else {
            setState(State.UNKNOWN);
        }
    }


    /**
     * {@see ConnectionsActivity#onReceive(Endpoint, Payload)}
     */
    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {

        if (payload.getType() == Payload.Type.BYTES) {
            setState(State.PLAY_INFORMATION);
            String str = new String(payload.asBytes());
            Log.i(TAG, "onReceive: " + str);
            notification(str);
            setState(State.CONNECTED);
            return;
        }
        if (payload.getType() == Payload.Type.FILE) {
            // Add this to our tracking map, so that we can retrieve the payload later.
            incomingPayloads.put(payload.getId(), payload);
            Log.i(TAG, "onReceive: putting in id: "+payload.getId());
        }
    }


    @Override
    protected void onReceiveUpdate(Endpoint endpoint, PayloadTransferUpdate update) {
        super.onReceiveUpdate(endpoint, update);
        long payloadId = update.getPayloadId();
        File payloadFile = null;

        if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
            Payload payload = incomingPayloads.remove(payloadId);
            if(payload==null)
                return;
            if (payload.getType() == Payload.Type.FILE) {
                // Retrieve the filename that was received in a bytes payload.
                String newFilename = "file.mp3";
                try {
                    payloadFile = payload.asFile().asJavaFile();
                }
                catch (Exception e)
                {

                }

                // Rename the file.
                payloadFile.renameTo(new File(payloadFile.getParentFile(), newFilename));

                mAudioPlayer = MediaPlayer.create(this, Uri.parse("file://"+payloadFile.getParent()+"/"+newFilename));
                mAudioPlayer.start();
            }
        }
    }

    //STATES
    enum State {
        UNKNOWN,
        DISCOVERING,
        PLAY_INFORMATION,
        CONNECTED, ERROR,
        WAIT_FOR_USER_TO_START;

    }


    private void setState(State state) {
        Log.d(TAG, "State set to " + state);
        State oldState = mState;
        mState = state;
        onStateChanged(oldState, state);
    }

    private State getState() {
        return mState;
    }

    private void onStateChanged(State oldState, State state) {
        switch (state) {
            case UNKNOWN:

                break;
            case DISCOVERING:
                progressBar.setVisibility(View.VISIBLE);
                if (googleApiClientIsReady && !isDiscovering())
                    startDiscovering();
                else if (googleApiClientIsReady && isDiscovering()) {
                    stopDiscovering();
                    startDiscovering();
                }
                break;
            case PLAY_INFORMATION:
                break;
            case CONNECTED:
                btnconnectToTrain.setImageResource(R.drawable.ic_nearby_color);
                progressBar.setVisibility(View.GONE);
                break;
            case ERROR:
                break;
            case WAIT_FOR_USER_TO_START:
                if (oldState.equals(State.CONNECTED)) {
                    disconnectFromAllEndpoints();
                }
                progressBar.setVisibility(View.GONE);
                btnconnectToTrain.setImageResource(R.drawable.ic_nearby_white);
                break;
            default:
                // no-op
                break;
        }
    }
}

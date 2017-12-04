package li.zoss.bsc.bfh.nearby_receiver;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.Payload;

import java.util.Iterator;
import java.util.UUID;

public class MainActivity extends ConnectionsActivity {

    private String TAG = "MainActivity";

    //Used for ConnectionsActivity
    private static final String SERVICE_ID = "li.zoss.bfh.bsc";
    private final String NAME = "Receiver " + UUID.randomUUID();

    //View
    private TextView txtID;
    private TextView txtState;
    private TextView txtConnectedClients;
    private TextView txtLog;

    private State mState = State.UNKNOWN;
    private boolean googleApiClientIsReady = false;
    private AudioPlayer mAudioPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //View
        txtID = findViewById(R.id.txtIDvalue);
        txtState = findViewById(R.id.txtStatevalue);
        txtConnectedClients = findViewById(R.id.txtConnectedDevices);
        txtLog = findViewById(R.id.txtLog);
        txtConnectedClients.setText("no Clients connected");
        txtLog.setText("Log:");
        txtID.setText(NAME);
        txtState.setText(mState.toString());
        setState(State.UNKNOWN);
    }

    @Override
    protected void onStart() {
        super.onStart();
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
        setState(State.DISCOVERING);
    }

    @Override
    protected void onEndpointDisconnected(Endpoint endpoint) {
        super.onEndpointDisconnected(endpoint);
        setState(State.DISCOVERING);
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
    protected void onStop() {
        setState(State.UNKNOWN);
        super.onStop();
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
    public void refreshConnectedClients() {
        txtConnectedClients.setText("");
        Iterator<Endpoint> iterator = getConnectedEndpoints().iterator();
        while (iterator.hasNext()) {
            String endpoint = iterator.next().getName();
            txtConnectedClients.append(endpoint + "\n");
        }
    }
    /** {@see ConnectionsActivity#onReceive(Endpoint, Payload)} */
    @Override
    protected void onReceive(Endpoint endpoint, Payload payload) {
        if (payload.getType() == Payload.Type.STREAM) {
            if (mAudioPlayer != null) {
                mAudioPlayer.stop();
                mAudioPlayer = null;
            }

            AudioPlayer player =
                    new AudioPlayer(payload.asStream().asInputStream()) {
                        @WorkerThread
                        @Override
                        protected void onFinish() {
                            runOnUiThread(
                                    new Runnable() {
                                        @UiThread
                                        @Override
                                        public void run() {
                                            mAudioPlayer = null;
                                        }
                                    });
                        }
                    };
            mAudioPlayer = player;
            player.start();
        }
    }
    //STATES
    enum State {
        UNKNOWN,
        DISCOVERING,
        PLAY_INFORMATION,
        CONNECTED, ERROR

    }

    private void setState(State state) {
        Log.d(TAG, "State set to " + state);
        State oldState = mState;
        mState = state;
        txtState.setText(state.toString()
        );
        onStateChanged(oldState, state);
    }

    private State getState() {
        return mState;
    }

    private void onStateChanged(State oldState, State state) {
        switch (state) {
            case UNKNOWN:
                if(googleApiClientIsReady) {
                    disconnectFromAllEndpoints();
                    stopDiscovering();
                }
                googleApiClientIsReady = false;
                break;
            case DISCOVERING:
                if (googleApiClientIsReady && !isDiscovering())
                    startDiscovering();
                else if (googleApiClientIsReady && isDiscovering())
                {
                    stopDiscovering();
                    startDiscovering();
                }
                refreshConnectedClients();
                break;
            case PLAY_INFORMATION:
                break;
            case CONNECTED:
                refreshConnectedClients();
                break;
            case ERROR:
                break;
            default:
                // no-op
                break;
        }
        txtLog.append("\n");
        txtLog.append("Now in state: " + state.toString());
    }
}

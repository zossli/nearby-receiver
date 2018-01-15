package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by Reto on 30.12.2017.
 */

public abstract class ConnectionService extends Service implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
    private String TAG = "ConnectionService";


    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    /**
     * We'll talk to Nearby Connections through the GoogleApiClient.
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * The devices we've discovered near us.
     */
    private final Map<String, Endpoint> mDiscoveredEndpoints = new HashMap<>();

    /**
     * The devices we have pending connections to. They will stay pending until we call {@link
     * #acceptConnection(Endpoint)} or {@link #rejectConnection(Endpoint)}.
     */
    private final Map<String, Endpoint> mPendingConnections = new HashMap<>();

    /**
     * The devices we are currently connected to. For advertisers, this may be large. For discoverers,
     * there will only be one entry in this map.
     */
    private final Map<String, Endpoint> mEstablishedConnections = new HashMap<>();

    /**
     * True if we are asking a discovered device to connect to us. While we ask, we cannot ask another
     * device.
     */
    private boolean mIsConnecting = false;

    /**
     * True if we are discovering.
     */
    private boolean mIsDiscovering = false;

    /**
     * True if we are advertising.
     */
    private boolean mIsAdvertising = false;

    /**
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mGoogleApiClient == null) {
            mGoogleApiClient =
                    new GoogleApiClient.Builder(this)
                            .addApi(Nearby.CONNECTIONS_API)
                            .addConnectionCallbacks(this)
                            .addOnConnectionFailedListener(this)
                            .build();
            mGoogleApiClient.connect();
        } else if (!mGoogleApiClient.isConnected())
            mGoogleApiClient.connect();

        return START_STICKY;
    }

    /**
     * Callbacks for connections to other devices.
     */
    private final ConnectionLifecycleCallback mConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                    Log.d(TAG,
                            String.format(
                                    "onConnectionInitiated(endpointId=%s, endpointName=%s)",
                                    endpointId, connectionInfo.getEndpointName()));
                    Endpoint endpoint = new Endpoint(endpointId, connectionInfo.getEndpointName());
                    mPendingConnections.put(endpointId, endpoint);
                    ConnectionService.this.onConnectionInitiated(endpoint, connectionInfo);
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    Log.d(TAG, String.format("onConnectionResponse(endpointId=%s, result=%s)", endpointId, result));

                    // We're no longer connecting
                    mIsConnecting = false;

                    if (!result.getStatus().isSuccess()) {
                        Log.w(TAG,
                                String.format(
                                        "Connection failed. Received status %s.",
                                        ConnectionService.toString(result.getStatus())));
                        onConnectionFailed(mPendingConnections.remove(endpointId));
                        return;
                    }
                    connectedToEndpoint(mPendingConnections.remove(endpointId));
                }

                @Override
                public void onDisconnected(String endpointId) {
                    if (!mEstablishedConnections.containsKey(endpointId)) {
                        Log.w(TAG, "Unexpected disconnection from endpoint " + endpointId);
                        return;
                    }
                    disconnectedFromEndpoint(mEstablishedConnections.get(endpointId));
                }
            };

    /**
     * Callbacks for payloads (bytes of data) sent from another device to us.
     */
    private final PayloadCallback mPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    onReceive(mEstablishedConnections.get(endpointId), payload);
                }

                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                    onReceiveUpdate(mEstablishedConnections.get(endpointId), update);
                }
            };

    private void resetState() {
        mDiscoveredEndpoints.clear();
        mPendingConnections.clear();
        mEstablishedConnections.clear();
        mIsConnecting = false;
        mIsDiscovering = false;
        mIsAdvertising = false;
    }


    /**
     * We've connected to Nearby Connections' GoogleApiClient.
     */
    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.v(TAG, "onConnected");
    }

    /**
     * We've been temporarily disconnected from Nearby Connections' GoogleApiClient.
     */
    @CallSuper
    @Override
    public void onConnectionSuspended(int reason) {
        Log.w(TAG, String.format("onConnectionSuspended(reason=%s)", reason));
        resetState();
    }


    /**
     * We are unable to connect to Nearby Connections' GoogleApiClient. Oh uh.
     */
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.w(TAG,
                String.format(
                        "onConnectionFailed(%s)",
                        ConnectionService.toString(new Status(connectionResult.getErrorCode()))));
        mGoogleApiClient.connect();
    }


    /**
     * A pending connection with a remote endpoint has been created. Use {@link ConnectionInfo} for
     * metadata about the connection (like incoming vs outgoing, or the authentication token). If we
     * want to continue with the connection, call {@link #acceptConnection(Endpoint)}. Otherwise, call
     * {@link #rejectConnection(Endpoint)}.
     */
    protected void onConnectionInitiated(Endpoint endpoint, ConnectionInfo connectionInfo) {
        acceptConnection(endpoint);
    }

    /**
     * Accepts a connection request.
     */
    protected void acceptConnection(final Endpoint endpoint) {
        Nearby.Connections.acceptConnection(mGoogleApiClient, endpoint.getId(), mPayloadCallback)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG,
                                            String.format(
                                                    "acceptConnection failed. %s", ConnectionService.toString(status)));
                                }
                            }
                        });
    }

    /**
     * Rejects a connection request.
     */
    protected void rejectConnection(Endpoint endpoint) {
        Nearby.Connections.rejectConnection(mGoogleApiClient, endpoint.getId())
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG,
                                            String.format(
                                                    "rejectConnection failed. %s", ConnectionService.toString(status)));
                                }
                            }
                        });
    }

    /**
     * Sets the device to discovery mode. It will now listen for devices in advertising mode. Either
     * {@link #onDiscoveryStarted()} ()} or {@link #onDiscoveryFailed()} ()} will be called once we've
     * found out if we successfully entered this mode.
     */
    protected void startDiscovering() {
        mIsDiscovering = true;
        mDiscoveredEndpoints.clear();
        Nearby.Connections.startDiscovery(
                mGoogleApiClient,
                getServiceId(),
                new EndpointDiscoveryCallback() {
                    @Override
                    public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                        Log.d(TAG,
                                String.format(
                                        "onEndpointFound(endpointId=%s, serviceId=%s, endpointName=%s)",
                                        endpointId, info.getServiceId(), info.getEndpointName()));

                        if (getServiceId().equals(info.getServiceId())) {
                            Endpoint endpoint = new Endpoint(endpointId, info.getEndpointName());
                            mDiscoveredEndpoints.put(endpointId, endpoint);
                            onEndpointDiscovered(endpoint);
                        }
                    }

                    @Override
                    public void onEndpointLost(String endpointId) {
                        Log.d(TAG, String.format("onEndpointLost(endpointId=%s)", endpointId));
                    }
                },
                new DiscoveryOptions(STRATEGY))
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (status.isSuccess()) {
                                    onDiscoveryStarted();
                                } else {
                                    mIsDiscovering = false;
                                    Log.w(TAG,
                                            String.format(
                                                    "Discovering failed. Received status %s.",
                                                    ConnectionService.toString(status)));
                                    onDiscoveryFailed();
                                }
                            }
                        });
    }

    /**
     * Stops discovery.
     */
    protected void stopDiscovering() {
        mIsDiscovering = false;
        Nearby.Connections.stopDiscovery(mGoogleApiClient);
    }

    /**
     * @return True if currently discovering.
     */
    protected boolean isDiscovering() {
        return mIsDiscovering;
    }

    /**
     * Discovery has successfully started. Override this method to act on the event.
     */
    protected void onDiscoveryStarted() {
    }

    /**
     * Discovery has failed to start. Override this method to act on the event.
     */
    protected void onDiscoveryFailed() {
    }

    /**
     * A remote endpoint has been discovered. Override this method to act on the event. To connect to
     * the device, call {@link #connectToEndpoint(Endpoint)}.
     */
    protected void onEndpointDiscovered(Endpoint endpoint) {
        connectToEndpoint(endpoint);
    }

    protected void disconnect(Endpoint endpoint) {
        Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, endpoint.getId());
        mEstablishedConnections.remove(endpoint.getId());
    }


    protected void send(Payload payload) {
        send(payload, mEstablishedConnections.keySet());
    }

    protected void send(Payload payload, String endpointID) {
        Nearby.Connections.sendPayload(mGoogleApiClient, endpointID, payload)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG,
                                            String.format(
                                                    "sendUnreliablePayload failed. %s",
                                                    ConnectionService.toString(status)));
                                }
                            }
                        });
    }

    protected void send(Payload payload, Set<String> endpoints) {
        Nearby.Connections.sendPayload(mGoogleApiClient, new ArrayList<>(endpoints), payload)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG,
                                            String.format(
                                                    "sendUnreliablePayload failed. %s",
                                                    ConnectionService.toString(status)));
                                }
                            }
                        });
    }

    protected void disconnectFromAllEndpoints() {
        if(mGoogleApiClient.isConnected()) {
            for (Endpoint endpoint : mEstablishedConnections.values()) {
                Nearby.Connections.disconnectFromEndpoint(mGoogleApiClient, endpoint.getId());
            }
        }
        mEstablishedConnections.clear();
    }

    /**
     * Sends a connection request to the endpoint.
     */
    protected void connectToEndpoint(final Endpoint endpoint) {
        // If we already sent out a connection request, wait for it to return
        // before we do anything else. P2P_STAR only allows 1 outgoing connection.
        if (mIsConnecting) {
            Log.w(TAG, "Already connecting, so ignoring this endpoint: " + endpoint);
            return;
        }

        Log.v(TAG, "Sending a connection request to endpoint " + endpoint);
        // Mark ourselves as connecting so we don't connect multiple times
        mIsConnecting = true;

        // Ask to connect
        Nearby.Connections.requestConnection(
                mGoogleApiClient, getName(), endpoint.getId(), mConnectionLifecycleCallback)
                .setResultCallback(
                        new ResultCallback<Status>() {
                            @Override
                            public void onResult(@NonNull Status status) {
                                if (!status.isSuccess()) {
                                    Log.w(TAG,
                                            String.format(
                                                    "requestConnection failed. %s", ConnectionService.toString(status)));
                                    mIsConnecting = false;
                                    onConnectionFailed(endpoint);
                                }
                            }
                        });
    }

    /**
     * True if we're currently attempting to connect to another device.
     */
    protected boolean isConnecting() {
        return mIsConnecting;
    }

    private void connectedToEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("connectedToEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.put(endpoint.getId(), endpoint);
        onEndpointConnected(endpoint);
    }

    private void disconnectedFromEndpoint(Endpoint endpoint) {
        Log.d(TAG, String.format("disconnectedFromEndpoint(endpoint=%s)", endpoint));
        mEstablishedConnections.remove(endpoint.getId());
        onEndpointDisconnected(endpoint);
    }

    /**
     * A connection with this endpoint has failed. Override this method to act on the event.
     */
    protected void onConnectionFailed(Endpoint endpoint) {
    }

    /**
     * Someone has connected to us. Override this method to act on the event.
     */
    protected void onEndpointConnected(Endpoint endpoint) {
        Log.i(TAG, endpoint.toString());
    }

    /**
     * Someone has disconnected. Override this method to act on the event.
     */
    protected void onEndpointDisconnected(Endpoint endpoint) {
    }

    /**
     * @return A list of currently connected endpoints.
     */
    protected Set<Endpoint> getDiscoveredEndpoints() {
        Set<Endpoint> endpoints = new HashSet<>();
        endpoints.addAll(mDiscoveredEndpoints.values());
        return endpoints;
    }

    /**
     * @return A list of currently connected endpoints.
     */
    protected Set<Endpoint> getConnectedEndpoints() {
        Set<Endpoint> endpoints = new HashSet<>();
        endpoints.addAll(mEstablishedConnections.values());
        return endpoints;
    }

    /**
     * Someone connected to us has sent us data. Override this method to act on the event.
     *
     * @param endpoint The sender.
     * @param payload  The data.
     */
    protected void onReceive(Endpoint endpoint, Payload payload) {
    }

    /**
     * Someone connected to us has sent us data. Override this method to act on the event.
     *
     * @param endpoint The sender.
     * @param update   The Update.
     */
    protected void onReceiveUpdate(Endpoint endpoint, PayloadTransferUpdate update) {
    }

    @Override
    public void onDestroy() {
        if (mGoogleApiClient != null)
            if (mGoogleApiClient.isConnected())
                Nearby.Connections.stopAllEndpoints(mGoogleApiClient);
                mGoogleApiClient.disconnect();
        super.onDestroy();
    }

    /**
     * @return The client's name. Visible to others when connecting.
     */
    protected abstract String getName();

    /**
     * @return The service id. This represents the action this connection is for. When discovering,
     * we'll verify that the advertiser has the same service id before we consider connecting to
     * them.
     */
    protected abstract String getServiceId();

    /**
     * Transforms a {@link Status} into a English-readable message for logging.
     *
     * @param status The current status
     * @return A readable String. eg. [404]File not found.
     */
    private static String toString(Status status) {
        return String.format(
                Locale.US,
                "[%d]%s",
                status.getStatusCode(),
                status.getStatusMessage() != null
                        ? status.getStatusMessage()
                        : ConnectionsStatusCodes.getStatusCodeString(status.getStatusCode()));
    }


    /**
     * Represents a device we can talk to.
     */
    protected static class Endpoint {
        @NonNull
        private final String id;
        @NonNull
        private final String name;

        private Endpoint(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj != null && obj instanceof Endpoint) {
                Endpoint other = (Endpoint) obj;
                return id.equals(other.id);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return String.format("Endpoint{id=%s, name=%s}", id, name);
        }
    }


}

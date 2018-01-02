package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String INTENT_REFRESH_TRAIN_INFO = "li.zoss.bfh.bsc.nearbyinformationsystem.refreshTrainInfoView";
    public static final String INTENT_REFRESH_TRAIN_CONNECTED = "li.zoss.bfh.bsc.nearbyinformationsystem.isConnectedToTrain";

    private NearbyService mBoundService;
    private boolean mIsBound;
    private Context mContext;
    private ServiceConnection mConnection;

    private ImageButton btnConnectToNearbySystem;
    private String TAG = "MainActivity";

    /**
     * These permissions are required before connecting to Nearby Connections. Only {@link
     * Manifest.permission#ACCESS_COARSE_LOCATION} is considered dangerous, so the others should be
     * granted just by having them in our AndroidManfiest.xml
     */
    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;
    private Intent intentService;
    private Button btnRequestStop;
    private TextView txtTrainInfo, txtTrainDirection;
    private BroadcastReceiver mBroadcastReceiver;
    private boolean startWasRequested = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getBaseContext();

        //Just create service intent for later use.
        intentService = new Intent(this, NearbyService.class);


        btnConnectToNearbySystem = findViewById(R.id.btnConnect);
        btnConnectToNearbySystem.setOnClickListener(this);

        btnRequestStop = findViewById(R.id.btnRequestStop);
        btnRequestStop.setOnClickListener(this);

        txtTrainInfo = findViewById(R.id.txtTrain);
        txtTrainDirection = findViewById(R.id.txtDirection);

    }


    /**
     * An optional hook to pool any permissions the app needs with the permissions ConnectionService
     * will request.
     *
     * @return All permissions required for the app to properly function.
     */
    protected String[] getRequiredPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    /**
     * @return True if the app was granted all the permissions. False otherwise.
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * The user has accepted (or denied) our permission request.
     */
    @CallSuper
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(this, R.string.missingPermission, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
            recreate();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        if (intentService != null)
            stopService(intentService);
        Log.i(TAG, "onDestroy: ");
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mBroadcastReceiver == null) mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "onReceive broadcast..." + intent);
                if (intent.getAction().equals(INTENT_REFRESH_TRAIN_INFO)) {
                    Log.i(TAG, "onReceive: Broadcast was "+INTENT_REFRESH_TRAIN_INFO);
                    final String trainInfo = intent.getStringExtra("trainInfo");
                    final String trainDirection = intent.getStringExtra("trainDirection");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshView(trainInfo, trainDirection);
                        }
                    });
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(INTENT_REFRESH_TRAIN_INFO);
        registerReceiver(mBroadcastReceiver, intentFilter);

        Log.i(TAG, "onStart: StartWasRequested:" + startWasRequested);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i(TAG, "onStop: ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume:");
        if (startWasRequested) startNearbyService();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("INTENT_START_WAS_REQUESTED", true);
        Log.i(TAG, "onSaveInstanceState: ");
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // recovering the instance state
        Log.i(TAG, "onRestoreInstanceState: ");
        if (savedInstanceState != null) {
            startWasRequested = savedInstanceState.getBoolean("INTENT_START_WAS_REQUESTED");
        }
        Log.i(TAG, "startWasRequested" + startWasRequested);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause: ");
        startWasRequested = true;

    }

    @Override
    public void onClick(View v) {
        if (btnRequestStop.equals(v)) {

        } else if (btnConnectToNearbySystem.equals(v)) {
            startNearbyService();
        }

    }

    private void startNearbyService() {
        if (hasPermissions(this, getRequiredPermissions())) {
            Log.i(TAG, "hasPermissions");
            // use this to start and trigger a service
            startService(intentService);

        } else {
            requestPermissions(getRequiredPermissions(), REQUEST_CODE_REQUIRED_PERMISSIONS);
            startWasRequested = true;
        }
    }

    public void refreshView(String trainInfo, String trainDirection) {
        txtTrainInfo.setText(trainInfo);
        txtTrainDirection.setText(trainDirection);
    }
}

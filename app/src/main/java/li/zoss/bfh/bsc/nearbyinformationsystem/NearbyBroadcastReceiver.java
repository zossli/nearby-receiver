package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

/**
 * Created by Reto on 31.12.2017.
 */

public class NearbyBroadcastReceiver extends BroadcastReceiver {

    public static final String INTENT_ACTION_REQUEST_STOP = "li.zoss.bfh.bsc.nearbyinfomrationsystem.requeststop";
    private final NearbyService mNearbyServices;
    private String TAG = "NearbyBroadcastReceiver";

    public NearbyBroadcastReceiver(NearbyService nearbyService) {
        mNearbyServices = nearbyService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: without if..."+intent);
        if (intent.getAction().equals(INTENT_ACTION_REQUEST_STOP)) {
            Log.i(TAG, "onReceive: " + intent);
        }
    }
}

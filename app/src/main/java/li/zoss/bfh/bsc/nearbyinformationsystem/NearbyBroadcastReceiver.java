package li.zoss.bfh.bsc.nearbyinformationsystem;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.google.android.gms.nearby.connection.Payload;

import org.json.JSONException;
import org.json.JSONObject;

public class NearbyBroadcastReceiver extends BroadcastReceiver {

    public static final String INTENT_ACTION_REQUEST_STOP = "li.zoss.bfh.bsc.nearbyinfomrationsystem.requeststop";
    private final NearbyService mNearbyServices;
    private String TAG = "NearbyBroadcastReceiver";

    public NearbyBroadcastReceiver(NearbyService nearbyService) {
        mNearbyServices = nearbyService;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: intent");
        if (intent.getAction().equals(INTENT_ACTION_REQUEST_STOP)) {
            String endpointid = intent.getStringExtra("Endpoint");
            String forStation = intent.getStringExtra("forStation");
            if (endpointid.isEmpty()) return;
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("Type", NotType.REQUEST_STOP);
                jsonObject.put("forStation", forStation);
                mNearbyServices.send(Payload.fromBytes(jsonObject.toString().getBytes()),endpointid);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}

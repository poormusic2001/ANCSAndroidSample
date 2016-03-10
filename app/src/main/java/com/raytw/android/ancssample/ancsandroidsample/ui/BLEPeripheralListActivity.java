package com.raytw.android.ancssample.ancsandroidsample.ui;

import android.Manifest;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.raytw.android.ancssample.ancsandroidsample.R;
import com.raytw.android.ancssample.ancsandroidsample.util.PermissionsRequest;

import java.util.ArrayList;
import java.util.List;


public class BLEPeripheralListActivity extends ListActivity {

    public String TAG = getClass().getSimpleName();
    private PermissionsRequest mPermissionsRequest;
    public static final String PREFS_NAME = "MyPrefsFile";
    public static final String BleStateKey = "ble_state";
    public static final String BleAddrKey = "ble_addr";
    public static final String BleAutoKey = "ble_auto_connect";
    private BluetoothAdapter mBluetoothAdapter;
    private boolean isBLEScaning = false;
    private Button buttonScan;
    private List<BluetoothDevice> mBluetoothDeviceList = new ArrayList<BluetoothDevice>();
    private BLEDeviceListAdapter mListAdapter = new BLEDeviceListAdapter();

    private LeScanCallback mLEScanCallback = new LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean found = false;
                    for (BluetoothDevice dev : mBluetoothDeviceList) {
                        if (dev.getAddress().equals(device.getAddress())) {
                            Log.i(TAG, "found ble device:" + device.getName());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        mBluetoothDeviceList.add(device);
                        Log.v(TAG, "name[" + device.getName() + "],device=>" + device.getAddress());
                        mListAdapter.notifyDataSetChanged();
                    }
                }
            });

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);
        mPermissionsRequest = buildPermissionsRequest();
        mPermissionsRequest.doCheckPermission(false);

        buttonScan = (Button) findViewById(R.id.scan);
        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                if (PermissionsRequest.isPermissionGranted(
                        BLEPeripheralListActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (!isBLEScaning) {
                        mBluetoothDeviceList.clear();
                        scan(true);
                    } else {
                        scan(false);
                    }
                } else {
                    Toast.makeText(getApplicationContext(),
                            "please setting app permission", Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });
        PackageManager pm = getPackageManager();
        boolean support = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
        if (!support) {
            Toast.makeText(this, "BLE is not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mgr.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }
        mBluetoothDeviceList.clear();
        SharedPreferences sp = this.getSharedPreferences(PREFS_NAME, 0);
        int ble_state = sp.getInt(BleStateKey, 0);
        Log.i(TAG, "read ble state : " + ble_state);
    /*
     * // if(ANCSGattCallback.BleDisconnect != ble_state){ if( ble_state > -1){ //must be boolean
     * auto = sp.getBoolean(BleAutoKey, true); String addr = sp.getString(BleAddrKey, ""); Intent
     * intent = new Intent(this, BLEConnect.class); intent.putExtra("addr", addr);
     * intent.putExtra("auto", auto); intent.putExtra("state", ble_state); startActivity(intent);
     * finish(); return; }
     */
        // scan(true);
        getListView().setAdapter(mListAdapter);
        // stop automatic scan , I try to list my iphone by mac address
        // connect is success, but status is 133, keep the code temp.
        // BluetoothDevice device =
        // BluetoothAdapter.getDefaultAdapter().getRemoteDevice("76:88:CE:4D:3F:AE");
        // mList.add(device);
        mListAdapter.notifyDataSetChanged();
    }


    void scan(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            Log.i(TAG, "start to scan.");
            isBLEScaning = true;
            mBluetoothAdapter.startLeScan(mLEScanCallback);
            buttonScan.setText(R.string.stop_scan);
        } else {
            if (isBLEScaning) {
                mBluetoothAdapter.stopLeScan(mLEScanCallback);
                isBLEScaning = false;
                buttonScan.setText(R.string.scan);
                Log.i(TAG, "stop scan");
            }
        }
    }

    @Override
    protected void onDestroy() {
        scan(false);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                mBluetoothDeviceList.clear();
                scan(true);
                break;
        }
        return true;
    }

    protected void onListItemClick(ListView l, View v, int position, long id) {
        BluetoothDevice dev = mBluetoothDeviceList.get(position);
        scan(false);
        Intent intent = new Intent(this, BLEConnectActivity.class);
        intent.putExtra("addr", dev.getAddress());
        intent.putExtra("auto", true);
        startActivity(intent);
        finish();
    }


    private PermissionsRequest buildPermissionsRequest() {
        return new PermissionsRequest(this) {
            // 要請求權限時的callback
            @Override
            public List<String> getCheckPeremission() {
                ArrayList<String> permissionsNeeded = new ArrayList<String>();
                permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);

                return permissionsNeeded;
            }

            // 檢查權限完成，不論是否有取得授權，onCheckPeremissionCompleted一定會執行
            @Override
            public void onCheckPeremissionCompleted() {
            }
        };
    }

    @Override
    public final void onRequestPermissionsResult(int requestCode,
                                                 String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        mPermissionsRequest.onRequestPermissionsResult(requestCode,
                permissions, grantResults);
    }

    private class BLEDeviceListAdapter extends BaseAdapter{

        @Override
        public View getView(int i, View arg1, ViewGroup arg2) {
            if (arg1 == null) {
                arg1 = LayoutInflater.from(BLEPeripheralListActivity.this).inflate(R.layout.bledevice_list_item, null);
            }
            BluetoothDevice dev = mBluetoothDeviceList.get(i);
            String name = dev.getName();
            if (TextUtils.isEmpty(name)) {
                name = dev.getAddress();
            }

            ((TextView)arg1.findViewById(R.id.device_name)).setText(name);
            return arg1;
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public Object getItem(int arg0) {
            return null;
        }

        @Override
        public int getCount() {
            return mBluetoothDeviceList.size();
        }
    }
}

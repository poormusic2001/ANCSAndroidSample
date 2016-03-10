package com.raytw.android.ancssample.ancsandroidsample.ui;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.raytw.android.ancssample.ancsandroidsample.ANCSGattCallback;
import com.raytw.android.ancssample.ancsandroidsample.ANCSGattCallback.StateListener;
import com.raytw.android.ancssample.ancsandroidsample.BLEservice;
import com.raytw.android.ancssample.ancsandroidsample.BLEservice.MyBinder;
import com.raytw.android.ancssample.ancsandroidsample.R;


public class BLEConnectActivity extends Activity implements StateListener {
    private String TAG = getClass().getSimpleName();
    SharedPreferences mSharedPreference;
    String address;
    boolean isAuto; // whether connectGatt(,auto,)
    boolean isBond;
    TextView tv_State;
    CheckBox checkBox_ExitService;
    BLEservice mBLEservice;
    Intent mIntent;
    int mCachedState;
    BroadcastReceiver mBtOnOffReceiver;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i(TAG, "onCreate");

        setContentView(R.layout.ble_connect);
        tv_State = (TextView) findViewById(R.id.ble_state);
        checkBox_ExitService = (CheckBox) findViewById(R.id.exit_service);

        address = getIntent().getStringExtra("addr");
        isAuto = getIntent().getBooleanExtra("auto", true);

        mSharedPreference = getSharedPreferences(BLEPeripheralListActivity.PREFS_NAME, 0);

        Log.e(TAG, "mAuto:" + isAuto);

        if (!isAuto) {
            tv_State.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    if (null != mBLEservice) {
                        mBLEservice.connect();
                        Toast.makeText(BLEConnectActivity.this, R.string.connect_notice, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        mCachedState = getIntent().getIntExtra("state", 0);
        mIntent = new Intent(this, BLEservice.class);
        mIntent.putExtra("addr", address);
        mIntent.putExtra("auto", isAuto);
        startService(mIntent);

        // if (!BluetoothAdapter.checkBluetoothAddress(addr)) {
        // finish();
        // return;
        // }
        mBtOnOffReceiver = new BroadcastReceiver() {
            public void onReceive(Context arg0, Intent intent) {
                // action must be bt on/off .
                String act = intent.getAction();
                if (act.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                    if (state != BluetoothAdapter.STATE_ON) {
                        finish();
                    }
                }
            }
        };
    }

    @Override
    public void onStart() {

        super.onStart();
        Log.i(TAG, "onStart");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);// bt on/off

        registerReceiver(mBtOnOffReceiver, filter);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        bindService(mIntent, conn, BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        unregisterReceiver(mBtOnOffReceiver);
        unbindService(conn);
        if (checkBox_ExitService.isChecked()) {
            stopService(mIntent);
        }
        super.onStop();
    }

    ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName cn, IBinder binder) {
            Log.i(TAG, "onServiceConnected");
            MyBinder mbinder = (MyBinder) binder;
            mBLEservice = mbinder.getService();
            isBond = true;
            startConnectGatt();// now not connect ,

        }

        @Override
        public void onServiceDisconnected(ComponentName cn) {
            isBond = false;
            Log.i(TAG, "onServiceDisconnected");
        }
    };

    private void startConnectGatt() {
        // FIXME: there is a bug in here.
        Log.i(TAG, "startConnectGatt " + "mCachedState:" + mCachedState + "getmBleANCS_state:" + mBLEservice.getmBleANCS_state());
        if (mBLEservice.getmBleANCS_state() != ANCSGattCallback.BleDisconnect) {
            final String str = mBLEservice.getStateDes();
            tv_State.setText(str);
        } else if (ANCSGattCallback.BleDisconnect == mCachedState) {
            Log.i(TAG, "connect ble");
            mBLEservice.startBleConnect(address, isAuto);
            mBLEservice.registerStateChanged(this);
        } else { // just display current state

            final String str = mBLEservice.getStateDes();
            tv_State.setText(str);
        }
    }

    @Override
    public void onStateChanged(final int state) {
        SharedPreferences.Editor edit = mSharedPreference.edit();
        edit.putInt(BLEPeripheralListActivity.BleStateKey, state);
        edit.putString(BLEPeripheralListActivity.BleAddrKey, address);
        edit.putBoolean(BLEPeripheralListActivity.BleAutoKey, isAuto);
        // edit.commit();
        // log("put state : "+state);
        runOnUiThread(new Runnable() {
            public void run() {
                tv_State.setText(mBLEservice.getStateDes());
            }
        });
    }

}

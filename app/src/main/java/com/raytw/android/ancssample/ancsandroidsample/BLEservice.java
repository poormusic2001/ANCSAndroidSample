package com.raytw.android.ancssample.ancsandroidsample;


import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.raytw.android.ancssample.ancsandroidsample.ANCSGattCallback.StateListener;
import com.raytw.android.ancssample.ancsandroidsample.ui.BLEPeripheralListActivity;

import java.util.List;

public class BLEservice extends Service implements ANCSParser.onIOSNotification, ANCSGattCallback.StateListener {
    private String TAG = getClass().getSimpleName();
    private final IBinder mBinder = new MyBinder();
    private ANCSParser mANCSHandler;
    private ANCSGattCallback mANCSGCattCallback;
    BluetoothGatt mBluetoothGatt;
    BroadcastReceiver mBtOnOffReceiver;
    boolean isAuto;
    String addr;
    private int mBleANCSstate = 0;

    public class MyBinder extends Binder {
        public BLEservice getService() {
            // Return this instance so clients can call public methods
            return BLEservice.this;
        }
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 11: // bt off, stopSelf()
                    stopSelf();
                    startActivityMsg();
                    break;
            }
        }
    };

    // when bt off, show a Message to notify user that ble need re_connect
    private void startActivityMsg() {
        Intent i = new Intent(this, Notice.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate");
        mANCSHandler = ANCSParser.getDefault(this);
        mANCSGCattCallback = new ANCSGattCallback(this, mANCSHandler);
        mBtOnOffReceiver = new BroadcastReceiver() {
            public void onReceive(Context arg0, Intent intent) {
                // action must be bt on/off .
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.i(TAG, "bluetooth OFF !");
                    mHandler.sendEmptyMessageDelayed(11, 500);
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);// bt on/off
        registerReceiver(mBtOnOffReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            isAuto = intent.getBooleanExtra("auto", true);
            addr = intent.getStringExtra("addr");
        }
        Log.i(TAG, "onStartCommand() flags=" + flags + ",stardId=" + startId);
        return START_STICKY_COMPATIBILITY;
        // return startId;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, " onDestroy()");
        mANCSGCattCallback.stop();
        mANCSHandler.unlistenIOSNotification(this);
        unregisterReceiver(mBtOnOffReceiver);
        Editor editor = getSharedPreferences(BLEPeripheralListActivity.PREFS_NAME, 0).edit();
        editor.putInt(BLEPeripheralListActivity.BleStateKey, ANCSGattCallback.BleDisconnect);
        editor.commit();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        Log.i(TAG, " onBind()thread id =" + android.os.Process.myTid());
        return mBinder;
    }

    // ** when ios notification changed
    @Override
    public void onIOSNotificationAdd(IOSNotification noti) {
        NotificationCompat.Builder build = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(noti.title).setContentText(noti.message);
        build.setTicker(noti.title);
        Log.e(TAG, "noti.title===>" + noti.title);
        Log.e(TAG, "noti.message===>" + noti.message);
        // set default sound
        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        build.setSound(uri);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(noti.uid, build.build());
    }

    @Override
    public void onIOSNotificationRemove(int uid) {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(uid);
    }

    // ** public method , for client to call
    public void startBleConnect(String addr, boolean auto) {
        Log.i(TAG, "startBleConnect-begin-");
        if (mBleANCSstate != 0) {
            Log.i(TAG, "stop ancs,then restart it");
            mANCSGCattCallback.stop();
        }
        isAuto = auto;
        this.addr = addr;
        BluetoothDevice dev = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(addr);
        mANCSHandler.listenIOSNotification(this);
        mBluetoothGatt = dev.connectGatt(this, auto, mANCSGCattCallback);
        mANCSGCattCallback.setBluetoothGatt(mBluetoothGatt);
        mANCSGCattCallback.setStateStart();
        Log.i(TAG, "startBleConnect-end-(waiting callback)");
    }

    public void registerStateChanged(StateListener stateListener) {
        Log.i(TAG, "registerStateChanged");
        if (null != stateListener)
            mANCSGCattCallback.addStateListen(stateListener);
        mANCSGCattCallback.addStateListen(this);
    }

    public void connect() {
        if (!isAuto)
            mBluetoothGatt.connect();
    }

    public String getStateDes() {
        return mANCSGCattCallback.getState();
    }

    public List<BluetoothGattService> getBluetoothGattServices(){
        return mBluetoothGatt.getServices();
    }

    public int getBleANCSstate() {
        return mBleANCSstate;
    }

    @Override
    public void onStateChanged(int state) {
        mBleANCSstate = state;
    }

}

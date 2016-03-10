package com.raytw.android.ancssample.ancsandroidsample;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

public class ANCSGattCallback extends BluetoothGattCallback {
    public static final int BleDisconnect = 0;// this is same to onConnectionStateChange()'s state
    public static final int BleAncsConnected = 10;// connected to iOS's ANCS
    public static final int BleBuildStart = 1;// after connectGatt(), before onConnectionStateChange()
    public static final int BleBuildConnectedGatt = 2; // onConnectionStateChange() state==2
    public static final int BleBuildDiscoverService = 3;// discoverServices()... this block
    public static final int BleBuildDiscoverOver = 4; // discoverServices() ok
    public static final int BleBuildDiscovered = 5; // discoverServices() BleBuildDiscovered callback
    public static final int BleBuildSetingANCS = 6; // settingANCS eg. need pwd...
    public static final int BleBuildNotify = 7; // notify arrive

    private String TAG = getClass().getSimpleName();
    private Context mContext;
    public int mBleState;
    public static ANCSParser mANCSHandler;
    private BluetoothGatt mBluetoothGatt;
    BluetoothGattService mBluetoothGattService;
    boolean isWritedNS, isWriteNS_DespOk;
    private ArrayList<StateListener> mStateListenersList = new ArrayList<StateListener>();

    public static interface StateListener {
        public void onStateChanged(int state);
    }

    public ANCSGattCallback(Context context, ANCSParser ancsParser) {
        mContext = context;
        mANCSHandler = ancsParser;
    }

    public void addStateListen(StateListener stateListener) {
        if (!mStateListenersList.contains(stateListener)) {
            mStateListenersList.add(stateListener);
            stateListener.onStateChanged(mBleState);
        }
    }


    public void stop() {
        Log.i(TAG, "stop connectGatt..");
        mBleState = BleDisconnect;
        for (StateListener stateLlistener : mStateListenersList) {
            stateLlistener.onStateChanged(mBleState);
        }
        if (null != mBluetoothGatt) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }
        mBluetoothGatt = null;
        mBluetoothGattService = null;
        mStateListenersList.clear();
    }


    public void setBluetoothGatt(BluetoothGatt BluetoothGatt) {
        mBluetoothGatt = BluetoothGatt;
    }

    public void setStateStart() {
        mBleState = BleBuildStart;
        for (StateListener stateListener : mStateListenersList) {
            stateListener.onStateChanged(mBleState);
        }
    }

    public String getState() {
        String state = "[unknown]";
        switch (mBleState) {
            case BleDisconnect: // 0
                state = "GATT [Disconnected]\n\n";
                break;
            case BleBuildStart: // 1
                state = "waiting state change after connectGatt()\n\n";
                break;
            case BleBuildConnectedGatt: // 2
                state = "GATT [Connected]\n\n";
                break;
            case BleBuildDiscoverService: // 3
                state = "GATT [Connected]\n" + "discoverServices...\n";
                break;
            case BleBuildDiscoverOver: // 4
                state = "GATT [Connected]\n" + "discoverServices OVER\n";
                break;
            case BleBuildDiscovered: // 5
                state = "GATT [Connected]\n" + "BleBuildDiscovered\n";
                break;
            case BleBuildSetingANCS: // 6
                state = "GATT [Connected]\n" + "discoverServices OVER\n" + "setting ANCS...password";
                break;
            case BleBuildNotify: // 7
                state = "ANCS notify arrive\n";

                break;
            case BleAncsConnected: // 10
                state = "GATT [Connected]\n" + "discoverServices OVER\n" + "ANCS[Connected] success !!";
                break;
        }
        return state;
    }


    @Override
    public void onCharacteristicChanged(BluetoothGatt mBluetoothGatt, BluetoothGattCharacteristic mBluetoothGattCharacteristic) {
        UUID uuid = mBluetoothGattCharacteristic.getUuid();
        Log.d(TAG, "測試::onCharacteristicChanged,uuid[" + uuid + "]");
        if (uuid.equals(GattConstant.Apple.sUUIDChaNotify)) {

            Log.i(TAG, "Notify uuid");
            byte[] data = mBluetoothGattCharacteristic.getValue();

            mANCSHandler.onNotification(data);

            mBleState = BleBuildNotify;// 6
            for (StateListener stateListener : mStateListenersList) {
                stateListener.onStateChanged(mBleState);
            }
        } else if (uuid.equals(GattConstant.Apple.sUUIDDataSource)) {

            byte[] data = mBluetoothGattCharacteristic.getValue();
            mANCSHandler.onDSNotification(data);
            Log.i(TAG, "datasource uuid");
        } else {
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.d(TAG, "測試::onConnectionStateChange,status[" + status + "],newState[" + newState + "]");
        Log.i(TAG, "onConnectionStateChange" + "newState " + newState + "status:" + status);
        mBleState = newState;
        // below code is necessary?
        for (StateListener stateListener : mStateListenersList) {
            stateListener.onStateChanged(mBleState);
        }
        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "start discover service");
            mBleState = BleBuildDiscoverService;

            mBluetoothGatt.discoverServices();
            Log.i(TAG, "discovery service end");
            mBleState = BleBuildDiscoverOver;
            for (StateListener stateListener : mStateListenersList) {
                stateListener.onStateChanged(mBleState);
            }
        } else if (0 == newState/* && mDisconnectReq */ && mBluetoothGatt != null) {
        }
    }

    @Override
    // New services discovered
    public void onServicesDiscovered(BluetoothGatt mBluetoothGatt, int status) {
        Log.d(TAG, "onServicesDiscovered,status[" + status + "]");
        mBleState = BleBuildDiscovered;
        for (StateListener stateListener : mStateListenersList) {
            stateListener.onStateChanged(mBleState);
        }
        if (status != 0)
            return;
        //TODO
        Log.d("service_uuid","getServices,size=>" + mBluetoothGatt.getServices());
        for(BluetoothGattService obj : mBluetoothGatt.getServices()){
            Log.d("service_uuid","service_uuid=>" + obj.getUuid());
        }

        BluetoothGattService bluetoothGattService = mBluetoothGatt.getService(GattConstant.Apple.sUUIDANCService);
        if (bluetoothGattService == null) {
            Log.i(TAG, "cannot find ANCS uuid");
            return;
        }

        Log.i(TAG, "find ANCS service");
        // Toast.makeText(mContext, "find ANCS service",Toast.LENGTH_LONG).show();

        BluetoothGattCharacteristic bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(GattConstant.Apple.sUUIDDataSource);
        if (bluetoothGattCharacteristic == null) {
            Log.i(TAG, "cannot find DataSource(DS) characteristic");
            return;
        }
        boolean registerDS = mBluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, true);
        if (!registerDS) {
            Log.i(TAG, " Enable (DS) notifications failed. ");
            return;
        }
        BluetoothGattDescriptor btDescriptor = bluetoothGattCharacteristic.getDescriptor(GattConstant.DESCRIPTOR_UUID);
        if (null != btDescriptor) {
            boolean r = btDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            boolean rr = mBluetoothGatt.writeDescriptor(btDescriptor);

            Log.i(TAG, "Descriptoer setvalue " + r + "writeDescriptor() " + rr);
        } else {
            Log.i(TAG, "can not find descriptor from (DS)");
        }
        isWriteNS_DespOk = isWritedNS = false;
        bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(GattConstant.Apple.sUUIDControl);
        if (bluetoothGattCharacteristic == null) {
            Log.i(TAG, "can not find ANCS's ControlPoint cha ");
        }

        mBluetoothGattService = bluetoothGattService;
        mANCSHandler.setService(bluetoothGattService, mBluetoothGatt);
        ANCSParser.get().reset();
        Log.i(TAG, "found ANCS service & set DS character,descriptor OK !");


    }

    @Override
    // the result of a descriptor write operation.
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        Log.d(TAG, "測試::onDescriptorWrite,status[" + status + "]");
        Log.i(TAG, "onDescriptorWrite" + "status:" + status);

        if (15 == status || 5 == status) {
            mBleState = BleBuildSetingANCS;// 5
            for (StateListener stateListener : mStateListenersList) {
                stateListener.onStateChanged(mBleState);
            }
            return;
        }
        if (status != BluetoothGatt.GATT_SUCCESS)
            return;
        if (mContext != null) {
            if (status == 5 || status == 133) {
                Toast.makeText(mContext, "status = " + status, Toast.LENGTH_LONG).show();
                ;
            }
        }
        // for some ble device, writedescriptor on sUUIDDataSource will return 133. fixme.
        // status is 0, SUCCESS.
        if (isWritedNS && isWriteNS_DespOk) {
            for (StateListener stateListener : mStateListenersList) {
                mBleState = BleAncsConnected;
                stateListener.onStateChanged(mBleState);
            }

        }
        if (mBluetoothGattService != null && !isWritedNS) { // set NS
            isWritedNS = true;
            BluetoothGattCharacteristic bluetoothGattCharacteristic = mBluetoothGattService.getCharacteristic(GattConstant.Apple.sUUIDChaNotify);
            if (bluetoothGattCharacteristic == null) {
                Log.i(TAG, "can not find ANCS's NS cha");
                return;
            } else {
            }
            boolean registerNS = mBluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, true);
            if (!registerNS) {
                Log.i(TAG, " Enable (NS) notifications failed  ");
                return;
            }
            BluetoothGattDescriptor desp = bluetoothGattCharacteristic.getDescriptor(GattConstant.DESCRIPTOR_UUID);
            if (null != desp) {
                boolean r = desp.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                boolean rr = mBluetoothGatt.writeDescriptor(desp);
                isWriteNS_DespOk = rr;
                Log.i(TAG, "(NS)Descriptor.setValue(): " + r + ",writeDescriptor(): " + rr);
            } else {
                Log.i(TAG, "null descriptor");
            }
        }
    }
}

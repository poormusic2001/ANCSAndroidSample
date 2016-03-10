package com.raytw.android.ancssample.ancsandroidsample;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ANCSParser {
    // ANCS constants
    public final static int NotificationAttributeIDAppIdentifier = 0;
    public final static int NotificationAttributeIDTitle = 1; // , (Needs to be followed by a 2-bytes
    // max length parameter)
    public final static int NotificationAttributeIDSubtitle = 2; // , (Needs to be followed by a
    // 2-bytes max length parameter)
    public final static int NotificationAttributeIDMessage = 3; // , (Needs to be followed by a
    // 2-bytes max length parameter)
    public final static int NotificationAttributeIDMessageSize = 4; // ,
    public final static int NotificationAttributeIDDate = 5; // ,
    public final static int AppAttributeIDDisplayName = 0;

    public final static int CommandIDGetNotificationAttributes = 0;
    public final static int CommandIDGetAppAttributes = 1;

    public final static int EventFlagSilent = (1 << 0);
    public final static int EventFlagImportant = (1 << 1);
    public final static int EventIDNotificationAdded = 0;
    public final static int EventIDNotificationModified = 1;
    public final static int EventIDNotificationRemoved = 2;

    public final static int CategoryIDOther = 0;
    public final static int CategoryIDIncomingCall = 1;
    public final static int CategoryIDMissedCall = 2;
    public final static int CategoryIDVoicemail = 3;
    public final static int CategoryIDSocial = 4;
    public final static int CategoryIDSchedule = 5;
    public final static int CategoryIDEmail = 6;
    public final static int CategoryIDNews = 7;
    public final static int CategoryIDHealthAndFitness = 8;
    public final static int CategoryIDBusinessAndFinance = 9;
    public final static int CategoryIDLocation = 10;
    public final static int CategoryIDEntertainment = 11;

    // !ANCS constants

    private final static int MSG_ADD_NOTIFICATION = 100;
    private final static int MSG_DO_NOTIFICATION = 101;
    private final static int MSG_RESET = 102;
    private final static int MSG_ERR = 103;
    private final static int MSG_CHECK_TIME = 104;
    private final static int MSG_FINISH = 105;
    private final static int FINISH_DELAY = 700;
    private final static int TIMEOUT = 15 * 1000;
    protected static final String TAG = "ANCSParser";

    private List<ANCSData> mANCSDataList = new LinkedList<ANCSData>();
    private Handler mHandler;

    private ANCSData mCurrentANCSData;
    BluetoothGatt mBTGatt;

    BluetoothGattService mBTGattService;
    Context mContext;
    private static ANCSParser mANCSParser;

    private ArrayList<onIOSNotification> onIOSNotificationList = new ArrayList<onIOSNotification>();

    public interface onIOSNotification {
        void onIOSNotificationAdd(IOSNotification n);

        void onIOSNotificationRemove(int uid);
    }

    private ANCSParser(Context c) {
        mContext = c;
        mHandler = new Handler(c.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                int what = msg.what;
                if (MSG_CHECK_TIME == what) {
                    if (mCurrentANCSData == null) {
                        return;
                    }
                    if (System.currentTimeMillis() >= mCurrentANCSData.timeExpired) {

                        Log.i(TAG, "msg timeout!");
                    }
                } else if (MSG_ADD_NOTIFICATION == what) {
                    Log.e(TAG, "MSG_ADD_NOTIFICATION == what");
                    // mPendingNotifcations 可能需要每次收到前都要clear
                    mANCSDataList.clear();
                    mANCSDataList.add(new ANCSData((byte[]) msg.obj));
                    mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
                } else if (MSG_DO_NOTIFICATION == what) {
                    processNotificationList();
                } else if (MSG_RESET == what) {
                    mHandler.removeMessages(MSG_ADD_NOTIFICATION);
                    mHandler.removeMessages(MSG_DO_NOTIFICATION);
                    mHandler.removeMessages(MSG_RESET);
                    mHandler.removeMessages(MSG_ERR);
                    mANCSDataList.clear();
                    mCurrentANCSData = null;

                    Log.i(TAG, "ANCSHandler reseted");
                } else if (MSG_ERR == what) {

                    Log.i(TAG, "error,skip_cur_data");
                    mCurrentANCSData.clear();
                    mCurrentANCSData = null;
                    mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
                } else if (MSG_FINISH == what) {
                    Log.i(TAG, "msg data.finish()");
                    if (null != mCurrentANCSData)
                        mCurrentANCSData.finish();
                }
            }
        };
    }

    public void listenIOSNotification(onIOSNotification onIOSNotify) {
        if (!onIOSNotificationList.contains(onIOSNotify))
            onIOSNotificationList.add(onIOSNotify);
    }


    public void setService(BluetoothGattService btGattService, BluetoothGatt btGatt) {
        mBTGatt = btGatt;
        mBTGattService = btGattService;
    }

    public static ANCSParser getDefault(Context context) {
        if (mANCSParser == null) {
            mANCSParser = new ANCSParser(context);
        }
        return mANCSParser;
    }

    public static ANCSParser get() {
        return mANCSParser;
    }

    private void sendNotification(final IOSNotification notiification) {
        Log.i(TAG, "[Add Notification] : " + notiification.uid);
        for (onIOSNotification notificationItem : onIOSNotificationList) {
            notificationItem.onIOSNotificationAdd(notiification);
        }
        // TODO 解析完notication並顯示
        Toast.makeText(mContext, "收到訊息,title[" + notiification.title + "],message[" + notiification.message + "]", Toast.LENGTH_LONG).show();
    }

    private void cancelNotification(int uid) {
        Log.i(TAG, "[cancel Notification] : " + uid);
        for (onIOSNotification onNotificationItem : onIOSNotificationList) {
            onNotificationItem.onIOSNotificationRemove(uid);
        }
    }

    private class ANCSData {
        long timeExpired;
        int curStep = 0;

        final byte[] notifyData; // 8 bytes

        ByteArrayOutputStream byteArrayOutputStream;
        IOSNotification notification;

        ANCSData(byte[] data) {
            notifyData = data;
            curStep = 0;
            timeExpired = System.currentTimeMillis();
            notification = new IOSNotification();
        }

        void clear() {
            if (byteArrayOutputStream != null) {
                byteArrayOutputStream.reset();
            }
            byteArrayOutputStream = null;
            curStep = 0;
        }

        int getUID() {
            return (0xff & notifyData[7] << 24) | (0xff & notifyData[6] << 16) | (0xff & notifyData[5] << 8) | (0xff & notifyData[4]);
        }

        void finish() {
            Log.d(TAG, "finish");
            if (null == byteArrayOutputStream) {
                return;
            }

            final byte[] data = byteArrayOutputStream.toByteArray();
            logD(data);
            if (data.length < 5) {
                return; //
            }
            // check if finished ?
            int cmdId = data[0]; // should be 0 //0 commandID
            if (cmdId != 0) {
                Log.i(TAG, "bad cmdId: " + cmdId);
                return;
            }
            int uid = ((0xff & data[4]) << 24) | ((0xff & data[3]) << 16) | ((0xff & data[2]) << 8) | ((0xff & data[1]));
            if (uid != mCurrentANCSData.getUID()) {

                Log.i(TAG, "bad uid: " + uid + "->" + mCurrentANCSData.getUID());
                return;
            }

            // read attributes
            notification.uid = uid;
            int curIdx = 5; // hard code
            while (true) {
                if (notification.isAllInit()) {
                    break;
                }
                if (data.length < curIdx + 3) {
                    return;
                }
                // attributes head
                int attrId = data[curIdx];
                int attrLen = ((data[curIdx + 1]) & 0xFF) | (0xFF & (data[curIdx + 2] << 8));
                curIdx += 3;
                if (data.length < curIdx + attrLen) {
                    return;
                }
                String val = new String(data, curIdx, attrLen);// utf-8 encode
                if (attrId == NotificationAttributeIDTitle) {
                    notification.title = val;
                } else if (attrId == NotificationAttributeIDMessage) {
                    notification.message = val;
                } else if (attrId == NotificationAttributeIDDate) {
                    notification.date = val;
                } else if (attrId == NotificationAttributeIDSubtitle) {
                    notification.subtitle = val;
                } else if (attrId == NotificationAttributeIDMessageSize) {
                    notification.messageSize = val;
                }
                curIdx += attrLen;
            }
            Log.i(TAG, "noti.title:" + notification.title);
            Log.i(TAG, "noti.message:" + notification.message);
            Log.i(TAG, "noti.date:" + notification.date);
            Log.i(TAG, "noti.subtitle:" + notification.subtitle);
            Log.i(TAG, "noti.messageSize:" + notification.messageSize);
            Log.i(TAG, "got a notification! data size = " + data.length);
            mCurrentANCSData = null;
            // mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // continue next!
            sendNotification(notification);
        }
    }


    private void processNotificationList() {

        Log.d(TAG, "1 processNotificationList==>mCurData" + mCurrentANCSData);
        mHandler.removeMessages(MSG_DO_NOTIFICATION);
        // handle curData!
        if (mCurrentANCSData == null) {
            if (mANCSDataList.size() == 0) {
                return;
            }

            mCurrentANCSData = mANCSDataList.remove(0);
            mANCSDataList.clear();
            Log.i(TAG, "ANCS New CurData");
        } else if (mCurrentANCSData.curStep == 0) { // parse notify data
            Log.d(TAG, "2 processNotificationList==>mCurData" + mCurrentANCSData);
            do {
                if (mCurrentANCSData.notifyData == null || mCurrentANCSData.notifyData.length != 8) {
                    mCurrentANCSData = null; // ignore

                    Log.i(TAG, "ANCS Bad Head!");
                    break;
                }
                if (EventIDNotificationRemoved == mCurrentANCSData.notifyData[0]) {
                    Log.d(TAG, "3 processNotificationList==>mCurData" + mCurrentANCSData);
                    int uid =
                            (mCurrentANCSData.notifyData[4] & 0xff) | (mCurrentANCSData.notifyData[5] & 0xff << 8) | (mCurrentANCSData.notifyData[6] & 0xff << 16) | (mCurrentANCSData.notifyData[7] & 0xff << 24);
                    cancelNotification(uid);
                    mCurrentANCSData = null;
                    break;
                }
                if (EventIDNotificationAdded != mCurrentANCSData.notifyData[0]) {
                    Log.d(TAG, "4 processNotificationList==>mCurData" + mCurrentANCSData);
                    mCurrentANCSData = null; // ignore
                    Log.i(TAG, "ANCS NOT Add!");
                    break;
                }
                Log.d(TAG, "5 processNotificationList==>mCurData" + mCurrentANCSData);
                // get attribute if needed!
                BluetoothGattCharacteristic btGattcharacteristic = mBTGattService.getCharacteristic(GattConstant.Apple.sUUIDControl);
                Log.d(TAG, "6 processNotificationList==>cha" + btGattcharacteristic);
                if (null != btGattcharacteristic) {
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();

                    bout.write((byte) 0);

                    bout.write(mCurrentANCSData.notifyData[4]);
                    bout.write(mCurrentANCSData.notifyData[5]);
                    bout.write(mCurrentANCSData.notifyData[6]);
                    bout.write(mCurrentANCSData.notifyData[7]);


                    bout.write(NotificationAttributeIDTitle);
                    bout.write(50);
                    bout.write(0);
                    // subtitle
                    bout.write(NotificationAttributeIDSubtitle);
                    bout.write(100);
                    bout.write(0);

                    // message
                    bout.write(NotificationAttributeIDMessage);
                    bout.write(500);
                    bout.write(0);

                    // message size
                    bout.write(NotificationAttributeIDMessageSize);
                    bout.write(10);
                    bout.write(0);
                    // date
                    bout.write(NotificationAttributeIDDate);
                    bout.write(10);
                    bout.write(0);

                    byte[] data = bout.toByteArray();

                    btGattcharacteristic.setValue(data);
                    mBTGatt.writeCharacteristic(btGattcharacteristic);
                    Log.i(TAG, "request ANCS(CP) the data of Notification. = ");
                    mCurrentANCSData.curStep = 1;
                    mCurrentANCSData.byteArrayOutputStream = new ByteArrayOutputStream();
                    mCurrentANCSData.timeExpired = System.currentTimeMillis() + TIMEOUT;
                    // mHandler.removeMessages(MSG_CHECK_TIME);
                    // mHandler.sendEmptyMessageDelayed(MSG_CHECK_TIME, TIMEOUT);
                    return;
                } else {
                    Log.i(TAG, "ANCS has No Control Point !");
                    // has no control!// just vibrate ...
                    mCurrentANCSData.byteArrayOutputStream = null;
                    mCurrentANCSData.curStep = 1;
                }

            } while (false);
        } else if (mCurrentANCSData.curStep == 1) {
            // check if finished!
            // mCurData.finish();
            Log.d(TAG, "7 processNotificationList==>mCurData" + mCurrentANCSData);
            return;
        } else {
            Log.d(TAG, "8 processNotificationList==>mCurData" + mCurrentANCSData);
            return;
        }
        Log.d(TAG, "9 processNotificationList==>mCurData" + mCurrentANCSData);
        mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION); // do next step
    }


    public void onDSNotification(byte[] data) {
        if (mCurrentANCSData == null) {

            Log.i(TAG, "got ds notify without cur data");
            return;
        }
        try {
            mHandler.removeMessages(MSG_FINISH);
            mCurrentANCSData.byteArrayOutputStream.write(data);
            mHandler.sendEmptyMessageDelayed(MSG_FINISH, FINISH_DELAY);
        } catch (IOException e) {
            Log.i(TAG, e.toString());
        }
    }

    void onWrite(BluetoothGattCharacteristic characteristic, int status) {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "write err: " + status);
            mHandler.sendEmptyMessage(MSG_ERR);
        } else {
            Log.i(TAG, "write OK");
            mHandler.sendEmptyMessage(MSG_DO_NOTIFICATION);
        }
    }

    public void onNotification(byte[] data) {
        Log.e(TAG, "onNotification...");
        if (data == null || data.length != 8) {
            Log.i(TAG, "bad ANCS notification data");
            return;
        }
        logD(data);
        Message msg = mHandler.obtainMessage(MSG_ADD_NOTIFICATION);
        msg.obj = data;
        msg.sendToTarget();
    }

    public void reset() {
        mHandler.sendEmptyMessage(MSG_RESET);
    }

    void logD(byte[] d) {
        StringBuffer sb = new StringBuffer();
        int len = d.length;
        for (int i = 0; i < len; i++) {
            sb.append(d[i] + ", ");
        }
        Log.i(TAG, "log Data size[" + len + "] : " + sb);
    }

}

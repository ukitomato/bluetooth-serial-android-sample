package com.example.bluetoothserial;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EventListener;
import java.util.Set;
import java.util.UUID;


///**
// * Android Activity
// *
// * 1. ActivityクラスにBluetoothSerialListenerInterfaceをimplements
// *
// * 2. BluetoothSerialインスタンス生成
// *
// *    BluetoothSerial bluetoothSerial;
// *    private final String DEVICE_NAME = "hogehoge"; // 自分のBluetoothデバイス名
// *    onCreate(){
// *        ...
// *
// *        bluetoothSerial = new BluetoothSerial(DEVICE_NAME, this);
// *    }
// *
// *    void onDestroy() {
// *        super.onDestroy();
// *        bluetoothSerial.disconnect();
// *    }
// *
// *    public void onSerialReceive(receiveMessage) {
// *        // 受信したときにやりたい処理
// *
// *    }
// *
// * ************* Option **************
// * バックグラウンドで処理させたくない場合は
// *    void onStop() {
// *        super.onStop();
// *        bluetoothSerial.disconnect();
// *    }
// *    void onResume() {
// *        super.onResume();
// *        bluetoothSerial.reconnect();
// *    }
// *
// * ***********************************
// *
// * 3. 受信する文字列には，必ず終端文字（"\n"）を入れる
// */

class BluetoothSerial {

    private static final String SSP_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    private static final String TAG = "BluetoothSerial";
    private BluetoothSocket btSocket = null; //ソケット情報を格納する
    private OutputStream outputStream = null; //出力ストリーム
    private BluetoothServer btServer = null;

    private BluetoothSerialListenerInterface listener = null;

    private String deviceName;

    private BluetoothDevice device;

    private Context context;

    private int statusCode;

    /**
     * @param deviceName Bluetoothデバイス名
     * @param context    BluetoothSerialListenerInterfaceをimplementsをしたActivity
     */
    BluetoothSerial(String deviceName, Context context) {
        this(deviceName, context, false);
    }

    /**
     * 自分のタイミングでデバイスにつなぎたい場合，manualStart = true にする
     *
     * @param deviceName  Bluetoothデバイス名
     * @param context     BluetoothSerialListenerInterfaceをimplementsをしたActivity
     * @param manualStart インスタンス時に接続させたくないときにtrue
     */
    BluetoothSerial(String deviceName, Context context, boolean manualStart) {
        this.deviceName = deviceName;
        this.context = context;
        if (manualStart) {
            this.statusCode = Status.MANUAL_INITIALIZE;
        } else {
            this.statusCode = Status.NOT_INITIALIZE;
            init();
        }
    }


    /**
     * 初期化
     *
     * @return 初期化結果
     */
    private boolean init() {
        // デバイス探索
        if (!searchDevice()) return false;

        // ソケットを確立する関数
        if (!connect(device)) return false;

        // ソケットが取得出来たら、出力用ストリームを作成する
        btServer = new BluetoothServer(btSocket);
        btServer.start();
        // リスナ設定
        setListener();
        statusCode = Status.CONNECTING;
        Log.d(TAG, "Connected");
        return true;
    }

    /**
     * デバイス検索
     *
     * @return 使用するBluetoothDevice
     */
    private boolean searchDevice() {
        //BTアダプタのインスタンスを取得
        //Bluetooth通信を行うために必要な情報を格納する
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice btDevice = null;
        //相手先BTデバイスのインスタンスを取得
        Set<BluetoothDevice> devices = adapter.getBondedDevices();
        for (BluetoothDevice device : devices) {
            if (device.getName().equals(deviceName)) {
                btDevice = device;
            }
        }
        adapter.cancelDiscovery();
        if (btDevice == null) {
            Log.d(TAG, "Can't find " + deviceName + ". Please pairing first");
            statusCode = Status.DEVICE_NOT_FOUND;
            return false;
        }
        device = btDevice;
        return true;
    }

    /**
     * 接続
     *
     * @param bluetoothDevice 使用するデバイス
     * @return 結果
     */
    private boolean connect(BluetoothDevice bluetoothDevice) {
        //ソケットの設定
        try {
            btSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(SSP_UUID));
            btSocket.connect();
            outputStream = btSocket.getOutputStream();
            statusCode = Status.SOCKET_ESTABLISHED;
            Log.d(TAG, "Established socket connection to " + deviceName);
            return true;
        } catch (IOException e) {
            btSocket = null;
            Log.d(TAG, "Can't establish socket connection to " + deviceName);
            statusCode = Status.CANT_SOCKET_ESTABLISH;
            return false;
        }
    }

    /**
     * 再接続
     *
     * @return 結果
     */
    boolean reconnect() {
        switch (statusCode) {
            case Status.MANUAL_INITIALIZE:
                Log.d(TAG, "Please call init() first");
                return false;
            case Status.NOT_INITIALIZE:
                return init();
            case Status.DISCONNECTED:
                // 接続
                if (!connect(device)) return false;
                // サーバ作成
                btServer = new BluetoothServer(btSocket);
                btServer.start();
                // リスナ設定
                setListener();
                Log.d(TAG, "Reconnected");
                return true;
            default:
                return false;
        }
    }

    /**
     * 切断
     *
     * @return 結果
     */
    boolean disconnect() {
        if (statusCode == Status.CONNECTING) {
            try {
                btServer.interrupt();
                btServer = null;
                btSocket.close();
                btSocket = null;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            removeListener();
            statusCode = Status.DISCONNECTED;
            Log.d(TAG, "Disconnected");
            return true;
        } else {
            Log.d(TAG, "server is not started");
            return false;
        }
    }

    /**
     * リスナ生成
     */
    private void setListener() {
        this.listener = (BluetoothSerialListenerInterface) context;
    }

    /**
     * リスナ削除
     */
    private void removeListener() {
        this.listener = null;
    }

    /**
     * デバイスへ送信
     *
     * @param str 送信文字列
     */
    void send(String str) {
        //文字列を送信する
        byte[] bytes;
        bytes = str.getBytes();
        try {
            //ここで送信
            outputStream.write(bytes);
            btSocket.close();
        } catch (IOException ignored) {
        }
    }

    /**
     * @return ステータスコード
     */
    int getStatus() {
        return statusCode;
    }

    /**
     * 接続判定
     *
     * @return 判定結果
     */
    boolean isConnecting() {
        return statusCode == Status.CONNECTING;
    }

    /**
     * BluetoothServer
     * デバイスとの送受信を行う
     */
    public class BluetoothServer extends Thread {
        InputStream inputStream;

        /**
         * コンストラクタ
         *
         * @param socket Bluetoothデバイスのソケット
         */
        BluetoothServer(BluetoothSocket socket) {
            InputStream tmpInput = null;
            try {
                // ソケットのStream取得
                tmpInput = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = tmpInput;
        }

        /**
         * 受信処理
         * - 受信時：受信文字列をリスナに通知
         */
        public void run() {
            byte[] buf;
            String receiveText;
            while (true) {
                int tmpBuf;
                try {
                    buf = new byte[1024];
                    tmpBuf = inputStream.read(buf);
                } catch (IOException e) {
                    break;
                }
                // 取得文字数判定
                if (tmpBuf > 0) {
                    receiveText = new String(buf);
                    receiveText = receiveText.split("\n")[0];

                    // 通知 (onSerialReceive)
                    if (listener != null) {
                        listener.onSerialReceive(receiveText);
                    }

                }
            }
        }

    }


    public interface BluetoothSerialListenerInterface extends EventListener {
        void onSerialReceive(String text);
    }

    public class Status {
        static final int MANUAL_INITIALIZE = 0;
        static final int NOT_INITIALIZE = 1;
        static final int SOCKET_ESTABLISHED = 2;
        static final int CONNECTING = 3;
        static final int DISCONNECTED = 4;

        static final int DEVICE_NOT_FOUND = 10;
        static final int CANT_SOCKET_ESTABLISH = 11;
    }

}

package com.example.myapplication;

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


/**
 * Android Activity
 * <p>
 * 1. ActivityクラスにBluetoothSerialListenerInterfaceをimplements
 * <p>
 * 2. BluetoothSerialインスタンス生成
 * <p>
 *  BluetoothSerial bluetoothSerial;
 *  private final String DEVICE_NAME = "hogehoge"; // 自分のBluetoothデバイス名
 *  onCreate(){
 *      bluetoothSerial = new BluetoothSerial(DEVICE_NAME, this);
 *  }
 * <p>
 *  void onDestroy() {
 *  super.onDestroy();
 *      bluetoothSerial.disconnect();
 *  }
 * <p>
 *  public void onSerialReceive() {
 *      // 受信したときにやりたい処理
 *      String receiveMessage = bluetoothSerial.receive();
 *  }
 * <p>
 * <p>
 * ************* Option **************
 * バックグラウンドで処理させたくない場合は
 *  void onStop() {
 *      super.onStop();
 *      bluetoothSerial.disconnect();
 *  }
 *  void onResume() {
 *      super.onResume();
 *      bluetoothSerial.reconnect();
 *  }
 * <p>
 * ***********************************
 * <p>
 * 3. 受信する文字列には，必ず終端文字（"\n"）を入れる
 */
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

    private boolean shoudInit = true;

    /**
     * @param deviceName Bluetoothデバイス名
     * @param context    BluetoothSerialListenerInterfaceをimplementsをしたActivity
     */
    BluetoothSerial(String deviceName, Context context) {
        this(deviceName, context, false);
    }

    /**
     * 自分のタイミングでデバイスにつなぎたい場合，lateStart = true にする
     *
     * @param deviceName Bluetoothデバイス名
     * @param context    BluetoothSerialListenerInterfaceをimplementsをしたActivity
     * @param lateStart  インスタンス時に接続させたくないときにtrue
     */
    BluetoothSerial(String deviceName, Context context, boolean lateStart) {
        this.deviceName = deviceName;
        this.context = context;
        if (!lateStart) {
            init();
        }
    }


    /**
     * 初期化
     * @return 初期化結果
     */
    boolean init() {
        // デバイス探索
        device = searchDevice();
        if (device == null) {
            Log.d(TAG, "Can't find " + deviceName+". Please pairing first");
            return false;
        }
        // ソケットを確立する関数
        if (!connect(device)) return false;

        // ソケットが取得出来たら、出力用ストリームを作成する
        btServer = new BluetoothServer(btSocket);
        btServer.start();
        // リスナ設定
        setListener();
        shoudInit = false;

        return true;
    }

    /**
     * デバイス検索
     *
     * @return 使用するBluetoothDevice
     */
    private BluetoothDevice searchDevice() {
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
        return btDevice;
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
            return true;
        } catch (IOException e) {
            btSocket = null;
            Log.d(TAG, "Can't establish socket connection to " + deviceName);
            return false;
        }
    }

    /**
     * 再接続
     * @return 結果
     */
    boolean reconnect() {
        if (!shoudInit) {
            if (btSocket == null) {
                // 接続
                if (!connect(device)) return false;
                // サーバ作成
                btServer = new BluetoothServer(btSocket);
                btServer.start();
                // リスナ設定
                setListener();
                return true;
            }
        } else {
            Log.d(TAG, "Please call init() first");
            return false;
        }
        return false;
    }

    /**
     * 切断
     * @return 結果
     */
    boolean disconnect() {
        if (!shoudInit) {
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
            return true;
        } else {
            Log.d(TAG, "Please call init() first");
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
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e1) {/*ignore*/}
        }
    }

    /**
     * BluetoothServerクラス
     * <p>
     * デバイスとの送受信を行うThread
     */
    public class BluetoothServer extends Thread {
        InputStream mInputStream;

        /**
         * コンストラクタ
         *
         * @param socket Bluetoothデバイスのソケット
         */
        BluetoothServer(BluetoothSocket socket) {
            InputStream tmpIn = null;
            try {
                // ソケットのStream取得
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputStream = tmpIn;
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
                    tmpBuf = mInputStream.read(buf);
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


}

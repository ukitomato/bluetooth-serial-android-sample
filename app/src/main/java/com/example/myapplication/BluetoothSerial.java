package com.example.myapplication; // 適当なものに変更

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EventListener;
import java.util.Set;
import java.util.UUID;


/**
 * Android Activity
 *
 * 1. ActivityクラスにBluetoothSerialListenerInterfaceをimplements
 *
 * 2. BluetoothSerialインスタンス生成
 *
 *      BluetoothSerial bluetoothSerial;
 *      private final String DEVICE_NAME = "hogehoge"; // 自分のBluetoothデバイス名
 *      onCreate(){
 *          bluetoothSerial = new BluetoothSerial(DEVICE_NAME, this);
 *      }
 *
 *      @Override
 *      protected void onDestroy() {
 *         super.onDestroy();
 *         bluetoothSerial.disconnect();
 *      }
 *
 *      @Override
 *      public void onSerialReceive() {
 *          // 受信したときにやりたい処理
 *          String receiveMessage = bluetoothSerial.receive();
 *      }
 *
 *
 *     ************* Option **************
 *     バックグラウンドで処理させたくない場合は
 *     @Override
 *     protected void onStop() {
 *         super.onStop();
 *         bluetoothSerial.disconnect();
 *     }
 *
 *     @Override
 *     protected void onResume() {
 *         super.onResume();
 *         bluetoothSerial.reconnect();
 *     }
 *
 *     ***********************************
 *
 * 3. 受信する文字列には，必ず終端文字（"\n"）を入れる
 *
 */
class BluetoothSerial {

    private static final String SSP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private BluetoothSocket btSocket = null; //ソケット情報を格納する
    private OutputStream outputStream = null; //出力ストリーム
    private BluetoothServer btServer = null;

    private BluetoothSerialListenerInterface listener = null;

    private String receiveString;//受け取った文字

    private String deviceName;

    private BluetoothDevice device;

    private Context context;

    //    コンストラクタの定義
    //
    //    con：ActivityのContext
    BluetoothSerial(String deviceName, Context context) {
        this.deviceName = deviceName;
        this.context = context;

        init();
    }

    private void init() {
        // デバイス探索
        device = searchDevice();

        //ソケットを確立する関数
        connect(device);
        //ソケットが取得出来たら、出力用ストリームを作成する
        if (btSocket != null) {
            btServer = new BluetoothServer(btSocket);
            btServer.start();
        }
        setListener();
    }

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

    // 接続
    private void connect(BluetoothDevice bluetoothDevice) {
        //ソケットの設定
        try {
            btSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString(SSP_UUID));
            btSocket.connect();
            outputStream = btSocket.getOutputStream();
        } catch (IOException e) {
            btSocket = null;
        }
    }

    // 再接続
    void reconnect() {
        if (btSocket == null) {
            connect(device);
            btServer = new BluetoothServer(btSocket);
            btServer.start();
            setListener();
        }
    }

    // 切断
    void disconnect() {
        btServer.interrupt();
        btServer = null;
        try {
            btSocket.close();
            btSocket = null;
        } catch (IOException e) {
            e.printStackTrace();
        }

        removeListener();
    }


    //  リスナを作製
    private void setListener() {
        this.listener = (BluetoothSerialListenerInterface) context;
    }

    //  リスナを削除
    private void removeListener() {
        this.listener = null;
    }

    //  送信メソッド
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

    //  受信した文字列を返すメソッド
    String receive() {
        return receiveString;
    }


    //  Bluetoothサーバクラス
    public class BluetoothServer extends Thread {
        InputStream mInputStream;

        //コンストラクタの定義
        BluetoothServer(BluetoothSocket socket) {
            //各種初期化
            //サーバー側の処理
            InputStream tmpIn = null;
            try {
                //自デバイスのBluetoothサーバーソケットの取得
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mInputStream = tmpIn;
        }

        //受信を繰り返す
        @RequiresApi(api = Build.VERSION_CODES.O)
        public void run() {
            byte[] buf;
            String rcvStr;
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
                    rcvStr = new String(buf);
                    rcvStr = rcvStr.split("\n")[0];

                    // 通知 (onSerialReceive)
                    receiveString = rcvStr;
                    if (listener != null) {
                        listener.onSerialReceive();
                    }

                }
            }
        }

    }


    public interface BluetoothSerialListenerInterface extends EventListener {
        void onSerialReceive();
    }
}

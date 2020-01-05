package com.liuxl.wifisend;

import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.MainThread;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private TextView mReceivedDataTv1;
    private TextView mSendDataTv;
    private static final String TAG = "WifiSend";
    private static final String HOST = Constant.IP;
    private static final int PORT = Constant.PORT;
    private OutputStream out;
    private InputStream in;
    private ExecutorService mExecutorService = null;
    private String receiveMsg;
    Socket socket;
    Button mChooseBtn;
    private String sendMsg = Constant.SEND_DATA_1;
    boolean running = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mReceivedDataTv1 = findViewById(R.id.tv_received_data_1);
        mChooseBtn = findViewById(R.id.btn_choose);
        mSendDataTv = findViewById(R.id.tv_send_data_1);
        mChooseBtn.setOnClickListener(this);
        mExecutorService = Executors.newCachedThreadPool();
        running = true;
        connect();
    }


    public void connect() {
        mSendDataTv.setText("正在连接。。。");
        mExecutorService.execute(new connectService());  //在一个新的线程中请求 Socket 连接
    }

    public void send(String sendMsg) {

        mExecutorService.execute(new sendService(Utils.hexStr2bytes(sendMsg)));
    }

    public void disconnect(View view) {

    }

    int choose = 0;

    @Override
    public void onClick(View v) {
        final String[] items = {Constant.SEND_DATA_1, Constant.SEND_DATA_2};

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("请选择发送数据")
                .setSingleChoiceItems(items, choose, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        choose = which;
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendMsg = items[choose];
                        Log.d(TAG, sendMsg);

                    }
                });
        builder.create().show();
    }

    private class sendService implements Runnable {
        private byte[] msg;

        sendService(byte[] bytes) {
            this.msg = bytes;
        }

        @Override
        public void run() {
            try {
                out.write(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private class connectService implements Runnable {
        @Override
        public void run() {//可以考虑在此处添加一个while循环，结合下面的catch语句，实现Socket对象获取失败后的超时重连，直到成功建立Socket连接
            try {
                socket = new Socket(HOST, PORT);      //步骤一
                socket.setSoTimeout(60000);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                handler.sendEmptyMessage(2);
                receiveMsg();
            } catch (Exception e) {
                Log.e(TAG, ("connectService:" + e.getMessage()));   //如果Socket对象获取失败，即连接建立失败，会走到这段逻辑
                Message message = new Message();
                message.what = 3;
                message.obj = "连接异常：" + e.getMessage();
                handler.sendMessage(message);
            }
        }
    }

    @Override
    protected void onDestroy() {
        in = null;
        out = null;
        super.onDestroy();
        running = false;
        handler = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private void receiveMsg() {
        try {
            while (running) {                                      //步骤三
                byte[] received = new byte[64];
                if (in == null) {
                    receiveMsg = "连接不可用";
                    SystemClock.sleep(200);
                    Message msg = new Message();
                    msg.obj = receiveMsg;
                    msg.what = 1;
                    if (handler != null) {
                        handler.sendMessage(msg);
                    }
                } else if (in.available() > 0) {
                    int size = in.read(received);
                    if (size > 0) {
                        receiveMsg = Utils.bytes2HexStr(received, 0, size);
                    } else {
                        receiveMsg = "暂未收到数据";
                    }
                    Log.d(TAG, "receiveMsg:" + receiveMsg);
                    Message msg = new Message();
                    msg.obj = receiveMsg;
                    msg.what = 1;
                    if (handler != null) {
                        handler.sendMessage(msg);
                    }
                }

                SystemClock.sleep(50);
            }
        } catch (IOException e) {
            receiveMsg = "连接异常，请重启!" + e.getMessage();
            e.printStackTrace();
            if (handler != null) {
                Message msg = new Message();
                msg.what = 1;
                msg.obj = receiveMsg;
                handler.sendMessage(msg);
            }

        }

    }

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                mReceivedDataTv1.setText((String) msg.obj);
            } else if (msg.what == 2) {
                mSendDataTv.setText("发送数据:" + sendMsg);
                if (running) {
                    send(sendMsg);
                    handler.sendEmptyMessageDelayed(2, 1000);
                }
            } else if (msg.what == 3) {
                mSendDataTv.setText((String) msg.obj);
            }
        }
    };

}

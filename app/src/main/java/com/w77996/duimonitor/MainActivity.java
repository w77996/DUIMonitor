package com.w77996.duimonitor;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.orhanobut.logger.Logger;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.UUID;

public class MainActivity extends Activity {

    //定义组件
    TextView mStatusLabel;
    Button mConnectBtn, mSendBtn, mQuitBtn;
    TextView txReceiveddata1,txReceiveddata2;
    String numberdata1;
    String numberdate2;
    //device var
    private ProgressDialog progressDialog;
    //蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter = null;
    //蓝牙套接字
    private BluetoothSocket btSocket = null;
    //输入输出流
    private OutputStream outStream = null;
    private InputStream inStream = null;
    //这条是蓝牙串口通用的UUID，不要更改
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static String address = "00:21:13:01:89:8C"; // <==要连接的目标蓝牙设备MAC地址
     // private static String address = "20:15:11:09:47:91"; // <==要连接的目标蓝牙设备MAC地址
    private ReceiveThread rThread=null;  //数据接收线程
    //接收到的字符串
    String ReceiveData="";
    MyHandler handler;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //首先调用初始化函数
        Init();
        InitBluetooth();
        handler=new MyHandler();
        //连接按钮
        mConnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //判断蓝牙是否打开
                if(!mBluetoothAdapter.isEnabled())
                {
                    mBluetoothAdapter.enable();
                }
                mBluetoothAdapter.startDiscovery();
                showProgressDialog("提示", "正在连接......");
                //创建连接
                new ConnectTask().execute(address);
            }
        });
        //退出按钮
        mQuitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disConnect();
            }
        });
        //发送指令按钮
        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //发送指令
                new SendInfoTask().execute("1");
            }
        });
    }
    /**
     * 显示加载框
     * @param title
     * @param message
     */
    public void showProgressDialog(String title, String message) {
        if (progressDialog == null) {
            progressDialog = ProgressDialog.show(MainActivity.this, title,
                    message, true, false);
        } else if (progressDialog.isShowing()) {
            progressDialog.setTitle(title);
            progressDialog.setMessage(message);
        }
        progressDialog.show();

    }
    /**
     * 隐藏加载框
     */
    public void hideProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

    }
    /**
     * 初始化控件
     */
    public void Init()
    {
        mStatusLabel =(TextView)this.findViewById(R.id.textView1);
        mConnectBtn =(Button)this.findViewById(R.id.blue_btn_conn);
        mSendBtn =(Button)this.findViewById(R.id.blue_btn_send);
        mQuitBtn =(Button)this.findViewById(R.id.blue_btn_disconn);
        mQuitBtn.setClickable(false);
        mSendBtn.setClickable(false);
        txReceiveddata1=(TextView)this.findViewById(R.id.blue_rec_data1);
        txReceiveddata2=(TextView)this.findViewById(R.id.blue_rec_data2);
    }
    /**
     * 初始化蓝牙适配器
     */
    public void InitBluetooth()
    {
        //得到一个蓝牙适配器
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null){
            Toast.makeText(this, "你的手机不支持蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if(!mBluetoothAdapter.isEnabled()){
            mBluetoothAdapter.enable();
        }
    }
    /**
     * 异步蓝牙连接
     */
    class ConnectTask extends AsyncTask<String,String,String>
    {
        @Override
        protected String doInBackground(String... params) {
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(params[0]);
            try {
                btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);//等同于传入Ip
                btSocket.connect();//连接
                Logger.e( "连接没有建立");
            } catch (IOException e) {
                try {
                    btSocket.close();
                    btSocket = null;
                    return "连接设备失败";
                } catch (IOException e2) {
                    Logger.e("无法关闭socket,连接失败", e2);
                    return "连接关闭失败";
                }
            }
            //取消搜索
            mBluetoothAdapter.cancelDiscovery();
            try {
                outStream = btSocket.getOutputStream();
                // inStream = btSocket.getInputStream();
            } catch (IOException e) {
                Logger.e("流创建失败", e);
                return "Socket 流创建失败";
            }
            return "设备连接成功";
        }
        //这个方法是在主线程中运行的，所以可以更新界面
        @Override
        protected void onPostExecute(String result) {
            //连接成功则启动监听
            rThread=new ReceiveThread();
            rThread.start();
            mStatusLabel.setText(result);//设置状态
            hideProgressDialog();//隐藏对话框
            if("设备连接成功".equals(result)){
                mQuitBtn.setClickable(true);
                mSendBtn.setClickable(true);
            }
            super.onPostExecute(result);
        }
    }
    /**
     * 发送数据到蓝牙设备的异步任务
     */
    class SendInfoTask extends AsyncTask<String,String,String>
    {
        @Override
        protected String doInBackground(String... arg0) {
            if(btSocket==null)
            {
                return "还没有创建连接";
            }
            try {
                byte[] msgBuffer = arg0[0].getBytes();
                //  将msgBuffer中的数据写到outStream对象中
                outStream.write(msgBuffer);
            } catch (IOException e) {
                Logger.e("error", "ON RESUME: Exception during write.", e);
                return "获取失败";
            }
            return "获取成功";
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            mStatusLabel.setText(result);
            //将发送框清空
            //  etSend.setText("");
        }
    }
    /**
     * 从蓝牙接收信息的线程
     */
    class ReceiveThread extends Thread
    {
        private InputStream inStream1 = null;
        @Override
        public void run() {
            //阻塞监听
            while(true )
            {
                //定义一个存储空间buff，定义长度为1024
                if(btSocket != null){
                    byte[] buff=new byte[2048];
                    byte[] buff1=new byte[2048];
                    String data1 = "";
                    String data = "";
                    try {
                        //从socket中获取数据
                        inStream = btSocket.getInputStream();
                        int b;
                        b = inStream.read(buff);
                        if(b > 0){
                            Logger.d("第一次获取长度 "+b);
                            data = buffertobyte(buff,2048);
                            Logger.d("第一次数据"+new String(data));
                        }else{
                            Logger.d("第一次数据未获取");
                        }
                        //延迟两秒
                        Thread.sleep(2000);
                        //第二次从socket中获取数据
                        inStream1 = btSocket.getInputStream();
                        int b1 = inStream1.read(buff1);
                        if(b1 > 0){
                            Logger.d("第二次长度"+b1);
                           data1 =  buffertobyte(buff1,2048);
                            Logger.d("第二次数据"+new String(data1));
                        }else{
                            Logger.d("第二次为获取数据");
                        }
                        Logger.d("data :"+new String(data)+new String(data1));
                        ReceiveData = new String(data)+new String(data1);
                        //解析接收到的数据
                        parseBuffer(ReceiveData);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        /**
         * 将byte转化成string
         * @param buff
         * @param size
         * @return
         */
        private String buffertobyte(byte[] buff,int size)
        {
            int length=0;
            //计算buff的长度
            for(int i=0;i<size;i++)
            {
                //未到最后一位，计算长度
                if(buff[i]>'\0')
                {
                    length++;
                }
                else
                {
                    break;
                }
            }
            byte[] newbuff=new byte[length];  //newbuff字节数组，用于存放真正接收到的数据
            for(int j=0;j<length;j++)
            {
                newbuff[j]=buff[j];
            }
             return new String(newbuff);
        }
    }
    /**
     * 对传来的数据进行解析
     * @param receiveData
     */
    private void  parseBuffer(String receiveData){
        //去除空格
        String jsondata = receiveData.trim();
        try {
            //解析设备传来的json
            JSONObject resultData = new JSONObject(jsondata);
           // numberdata1 = resultData.getString("data1");
           // numberdate2  = resultData.getString("data2");
            //获取数据
            Double data1 = resultData.getDouble("data1");
            Double data2 = resultData.getDouble("data2");
            NumberFormat ddf1= NumberFormat.getNumberInstance() ;

            //解析数据，保留两位小数
            ddf1.setMaximumFractionDigits(2);
            numberdata1= ddf1.format(data1) ;
            //解析数据，不保留小数点后的数字
            ddf1.setMaximumFractionDigits(0);
            numberdate2= ddf1.format(data2) ;

            //设置数据置界面
            txReceiveddata1.post(new Runnable() {
                @Override
                public void run() {
                    txReceiveddata1.setText(numberdata1);
                }
            });
            txReceiveddata2.post(new Runnable() {
                @Override
                public void run() {
                    txReceiveddata2.setText(numberdate2);
                }
            });
        }catch (Exception e){

        }
       /* Message msg=Message.obtain();
        // msg.obj = temp;
        msg.what=1;
        handler.sendMessageDelayed(msg,0); */ //发送消息:系统会自动调用handleMessage( )方法来处理消息
    }
    /**
     * 更新界面的Handler类
     */
    class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case 1:
                    //txReceived.setText(ReceiveData);
                    break;

            }
        }
    }

    /**
     * 断开连接
     */
    public void disConnect(){
        if(btSocket!=null)
        {
            try {
                btSocket.close();//关闭socket
                btSocket=null;
                if(rThread!=null)
                {
                    rThread.join();
                }
                mStatusLabel.setText("当前连接已断开");
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
        }
    }
    /**
     * 复写ActiveonDestroy方法
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            //判断线程是否存在
            if(rThread!=null)
            {
                //关闭蓝牙传输
                if(btSocket!=null){
                    btSocket.close();
                    btSocket=null;
                }
                rThread.join();
            }
            this.finish();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}


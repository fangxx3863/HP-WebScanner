package com.fangxx.hpwebscan;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.SyncStateContract;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import com.tbruyelle.rxpermissions3.RxPermissions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    Button scan, PreviewBtn;
    ImageView Preview;
    EditText IPText;
    final RxPermissions rxPermissions = new RxPermissions(this); // where this is an Activity or Fragment instance

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //初始化纸张选择下拉菜单控件
        Spinner paper = (Spinner) findViewById(R.id.paper);
        //设置纸张选择控件的默认值
        paper.setSelection(2, true);



        /**
         * 修改纸张类型弹出吐司提示
         */
        //设置item的被选择的监听
        paper.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            //当item被选择后调用此方法
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //获取我们所选中的内容
                String s = parent.getItemAtPosition(position).toString();
                //弹一个吐司提示我们所选中的内容
                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
            }
            //只有当patent中的资源没有时，调用此方法
            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });



        /**
         * 动态申请权限
         * 控件初始化
         * 数据持久化
         */
        rxPermissions
                .request(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.INTERNET)
                .subscribe(granted -> {
                    if (granted) {
                    } else {

                    }
                });
        //初始化控件
        scan = (Button) findViewById(R.id.scan);
        PreviewBtn = (Button) findViewById(R.id.PreviewBtn);
        Preview = (ImageView) findViewById(R.id.Preview);
        IPText = (EditText) findViewById(R.id.IPText);
        //读取持久化的IP数据
        FileHelper fHelper = new FileHelper(MainActivity.this);
        try {
            String oldip = fHelper.read("IP");
            IPText.setText(oldip);
        } catch (IOException e) {
            e.printStackTrace();
        }



        /**
         * 预览按键监听
         */
        PreviewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("事件","预览按钮按下");

                /**
                 * 传送开始扫描表单数据
                 */
                //设置OkHttp的超时
                OkHttpClient okHttpClient  = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10,TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build();
                //POST方式提交的表单数据
                RequestBody formBody = new FormBody.Builder()
                        .add("ws_operation", "1")
                        .add("ws_scanid", "0")
                        .add("ws_type", "0")
                        .add("ws_format", "0")
                        .add("ws_size", "0")
                        .add("ws_scan_method", "0")
                        .build();
                //获取IP控件的文本
                IPText = (EditText) findViewById(R.id.IPText);
                String ip = IPText.getText().toString();
                //设置请求URL
                String wsurl = "http://" + ip + "/wsStatus.htm";
                final Request request = new Request.Builder()
                        .addHeader("Content-Type", " application/x-www-form-urlencoded")//这里必须要加请求头否则会404
                        .url(wsurl)//请求的url
                        .post(formBody)
                        .build();
                //创建Call
                Call call = okHttpClient.newCall(request);
                //加入队列 异步操作
                call.enqueue(new Callback() {
                    //请求错误回调方法
                    @Override
                    public void onFailure(Call call, IOException e) {
                        System.out.println("连接失败");
                    }
                    //请求成功打印响应内容
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if(response.code()==200) {
                            System.out.println(response.body().string());
                        }
                    }
                });
                //休眠800ms等待请求发送
                SystemClock.sleep(800);



                /**
                 * 修改图片控件
                 */
                //获取时间戳
                long timeStamp = System.currentTimeMillis();
                //获取纸张类型
                int sizeint = (int) paper.getSelectedItemId();
                String size = Integer.toString(sizeint);
                //整合URL
                String PreUrl = "http://" + ip + "/scan/image1.jpg?id=0&type=4&prev=1&size=" + size + "&fmt=1&time=" + timeStamp;
                Log.v("事件", "预览URL：" + PreUrl);
                //在消息队列执行修改图片控件操作
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bmp = getURLimage(PreUrl);
                        Message msg = new Message();
                        msg.what = 0;
                        msg.obj = bmp;
                        Log.v("事件", "修改控件图片");
                        handle.sendMessage(msg);
                    }
                }).start();

            }
        });

        //扫描按键监听
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("事件","扫描按钮按下");

                /**
                 * 传送开始扫描表单数据
                 */
                //设置OkHttp的超时
                OkHttpClient okHttpClient  = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10,TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build();
                //POST方式提交的数据
                RequestBody formBody = new FormBody.Builder()
                        .add("ws_operation", "1")
                        .add("ws_scanid", "0")
                        .add("ws_type", "0")
                        .add("ws_format", "0")
                        .add("ws_size", "0")
                        .add("ws_scan_method", "0")
                        .build();
                IPText = (EditText) findViewById(R.id.IPText);
                String ip = IPText.getText().toString();
                String wsurl = "http://" + ip + "/wsStatus.htm";
                final Request request = new Request.Builder()
                        .addHeader("Content-Type", " application/x-www-form-urlencoded")
                        .url(wsurl)//请求的url
                        .post(formBody)
                        .build();
                //创建/Call
                Call call = okHttpClient.newCall(request);
                //加入队列 异步操作
                call.enqueue(new Callback() {
                    //请求错误回调方法
                    @Override
                    public void onFailure(Call call, IOException e) {
                        System.out.println("连接失败");
                    }
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if(response.code()==200) {
                            System.out.println(response.body().string());
                        }
                    }
                });
                //休眠800ms等待POST表单传送
                SystemClock.sleep(800);



                /**
                 * 下载图片
                 */
                //下载文件
                Log.v("事件", "下载进程开始");
                long timeStamp = System.currentTimeMillis();
                //读取下拉菜单的纸张类型
                int sizeint = (int) paper.getSelectedItemId();
                String size = Integer.toString(sizeint);
                //整合下载链接
                String url = "http://" + ip + "/scan/image1.jpg?id=0&type=4&&size=" + size + "&fmt=1&time=" + timeStamp;
                Log.v("事件", "下载链接为:" + url);
                //传送至消息队列修改及下载图片
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bmp = SaveImage(url);
                        Message msg = new Message();
                        msg.what = 0;
                        msg.obj = bmp;
                        Log.v("事件", "修改控件图片与下载图片");
                        handle.sendMessage(msg);
                    }
                }).start();
            }
        });
    }



    /**
     * 在消息队列中实现对控件的更改
     */
    private Handler handle = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    Log.v("事件", "图片控件修改");
                    Bitmap bmp=(Bitmap)msg.obj;
                    Preview.setImageBitmap(bmp);
                    break;
            }
        };
    };



    /**
     * 加载图片
     * @param url
     * @return
     */
    public Bitmap getURLimage(String url) {
        Bitmap bmp = null;
        try {
            URL myurl = new URL(url);
            // 获得连接
            HttpURLConnection conn = (HttpURLConnection) myurl.openConnection();
            conn.setConnectTimeout(20000);//设置超时
            conn.setDoInput(true);
            conn.setUseCaches(false);//不缓存
            conn.connect();
            InputStream is = conn.getInputStream();//获得图片的数据流
            bmp = BitmapFactory.decodeStream(is);//读取图像数据
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bmp;
    }



    /**
     * 加载+下载图片
     * @param url
     * @return
     */
    public Bitmap SaveImage(String url) {
        Bitmap bmp = null;
        try {
            URL myurl = new URL(url);
            // 获得连接
            HttpURLConnection conn = (HttpURLConnection) myurl.openConnection();
            conn.setConnectTimeout(50000);//设置超时
            conn.setDoInput(true);
            conn.setUseCaches(false);//不缓存
            conn.connect();//连接
            InputStream is = conn.getInputStream();//获得图片的数据流
            //bmp = BitmapFactory.decodeStream(is);//读取图像数据
            long timeStamp = System.currentTimeMillis();
            //如果没有目录则创建目录
            String fileSavePath = Environment.getExternalStorageDirectory() + "/Pictures/Scan";
            File dir = new File(fileSavePath);
            if (!dir.exists()){
                dir.mkdirs();
            }

            File imgFile = new File(fileSavePath, "Scan_" + timeStamp + ".jpg");
            FileOutputStream fos = new FileOutputStream(imgFile);

            //下载图片循环
            byte[] buffer = new byte[1024];
            while (true) {
                int numbed = is.read(buffer);
                // 下载完成
                if (numbed < 0) {
                    break;
                }
                fos.write(buffer, 0, numbed);
            }
            fos.close();
            is.close();

            //读取下载的图片并返回
            bmp = BitmapFactory.decodeFile(fileSavePath + "/Scan_" + timeStamp + ".jpg");
            //发送媒体扫描信号
            File scanfile = new File(fileSavePath);
            new SingleMediaScanner(MainActivity.this, scanfile);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return bmp;
    }



    /**
     * 持久化数据
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        //持久化IP数据
        String ip = IPText.getText().toString();
        FileHelper fHelper = new FileHelper(MainActivity.this);
        try {
            fHelper.save("IP", ip);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
package com.quantum.crash;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Process;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Administrator on 2015/7/9 0009.
 */
public class SendCrashTask extends AsyncTask<Void, Integer, Boolean> implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "SendCrashTask";
    private static final boolean DEBUG = true;
    public static final String SERVER = "test";
    private static final String PATH = Environment.getExternalStorageDirectory().getPath() + "/log/";
    private static final String FILE_NAME = "crash";

    //log文件的后缀名
    private static final String FILE_NAME_SUFFIX = ".trace";
    private static Thread.UncaughtExceptionHandler sDefaultCrashHandler;

    public void setContext(Context mContext) {
        this.mContext = mContext;
    }

    private Context mContext;
    private static SendCrashTask sInstance = new SendCrashTask();

    public void setThread(Thread thread) {
        this.thread = thread;
    }

    public void setEx(Throwable ex) {
        this.ex = ex;
    }

    private Throwable ex = null;
    private Thread thread = null;


    private SendCrashTask() {
    }

    public static SendCrashTask getInstance() {
        return sInstance;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        uploadExceptionToServer(ex);
        return true;
    }

    private void dumpExceptionToSDCard(Throwable ex) throws IOException {
        //如果SD卡不存在或无法使用，则无法把异常信息写入SD卡
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            if (DEBUG) {
                Log.w(TAG, "sdcard unmounted,skip dump exception");
                return;
            }
        }

        File dir = new File(PATH);
        if (!dir.exists()) {
            dir.mkdir();
        }
        String time = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        //以当前时间创建log文件
        File file = new File(PATH + FILE_NAME + time + FILE_NAME_SUFFIX);
        try {
            Log.w(TAG, PATH + FILE_NAME);
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)));

            //导出手机信息
            dumpPhoneInfo(pw);

            pw.println();
            //导出异常的调用栈信息
            ex.printStackTrace(pw);

            pw.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "dump crash info failed");
        }
    }

    private void dumpPhoneInfo(PrintWriter pw) throws PackageManager.NameNotFoundException {
        //应用的版本名称和版本号
        PackageManager pm = mContext.getPackageManager();
        PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
        pw.print("App Version: ");
        pw.print(pi.versionName);
        pw.print('_');
        pw.println(pi.versionCode);

        //android版本号
        pw.print("OS Version: ");
        pw.print(Build.VERSION.RELEASE);
        pw.print("_");
        pw.println(Build.VERSION.SDK_INT);

        //手机制造商
        pw.print("Vendor: ");
        pw.println(Build.MANUFACTURER);

        //手机型号
        pw.print("Model: ");
        pw.println(Build.MODEL);

        //cpu架构
        pw.print("CPU ABI: ");
        pw.println(Build.CPU_ABI);
    }

    private void uploadExceptionToServer(Throwable ex) {
        //TODO Upload Exception Message To Your Web Server
        HttpURLConnection httpConn = null;
        PrintWriter pw = null;
        try {

            //建立连接
            URL url = new URL(SERVER);
            httpConn = (HttpURLConnection) url.openConnection();

            ////设置连接属性
            httpConn.setDoOutput(true);//使用 URL 连接进行输出
            httpConn.setDoInput(true);//使用 URL 连接进行输入
            httpConn.setUseCaches(false);//忽略缓存
            httpConn.setRequestMethod("POST");//设置URL请求方法

            httpConn.setRequestProperty("Content-Type", "application/octet-stream");
            httpConn.setRequestProperty("Charset", "UTF-8");
            OutputStream outputStream = null;
//            Log.e(TAG, "BEFORE");
            //建立输出流，并写入数据
            try {
                outputStream = httpConn.getOutputStream();
            } catch (Exception e) {
                Log.e(TAG, "EXCEPTION");
                e.printStackTrace();
            }
//            Log.e(TAG, "AFTER");
            pw = new PrintWriter(outputStream);

            //导出手机信息
            dumpPhoneInfo(pw);

            pw.println();
            //导出异常的调用栈信息
            ex.printStackTrace(pw);
            pw.flush();
            //获得响应状态
            int responseCode = httpConn.getResponseCode();
            if (HttpURLConnection.HTTP_OK == responseCode) {//连接成功
                Log.w(TAG, "send crash log success!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "upload crash to server failed");
        } finally {
            try {
                if (httpConn != null) {
                    httpConn.disconnect();
                }
                if (pw != null) {
                    pw.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 这个是最关键的函数，当程序中有未被捕获的异常，系统将会自动调用#uncaughtException方法
     * thread为出现未捕获异常的线程，ex为未捕获的异常，有了这个ex，我们就可以得到异常信息。
     */
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        if (isNetworkAvailable(mContext)) {
            SendCrashTask instance = new SendCrashTask();
            instance.setEx(ex);
            instance.setThread(thread);
            instance.setContext(mContext);
            instance.execute();
        } else {
            handlerExAndExit(thread, ex);
        }
    }

    //这里主要完成初始化工作
    public void init(Context context) {
        //获取系统默认的异常处理器
        sDefaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler();
        //将当前实例设为系统默认的异常处理器
        Thread.setDefaultUncaughtExceptionHandler(this);
        //获取Context，方便内部使用
        mContext = context.getApplicationContext();
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        handlerExAndExit(thread, ex);
    }

    private void handlerExAndExit(Thread thread, Throwable throwable) {
        //打印出当前调用栈信息
        if (throwable != null) {
            throwable.printStackTrace();
        }

        //如果系统提供了默认的异常处理器，则交给系统去结束我们的程序，否则就由我们自己结束自己
        if (sDefaultCrashHandler != null && thread != null && throwable != null) {
            sDefaultCrashHandler.uncaughtException(thread, throwable);
        } else {
            //TODO 结束自己
            android.os.Process.killProcess(Process.myPid());
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            //如果仅仅是用来判断网络连接
            //则可以使用 cm.getActiveNetworkInfo().isAvailable();
            NetworkInfo[] info = cm.getAllNetworkInfo();
            if (info != null) {
                for (int i = 0; i < info.length; i++) {
                    if (info[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

package com.quantum.crash;

import android.app.Application;

/**
 * Created by Administrator on 2015/7/9 0009.
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SendCrashTask sendCrashTask = SendCrashTask.getInstance();
        sendCrashTask.init(this);
    }
}

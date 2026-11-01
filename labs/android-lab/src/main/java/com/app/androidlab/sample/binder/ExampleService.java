package com.app.androidlab.sample.binder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import com.app.androidlab.sample.runtime.binder.IExampleCallback;
import com.app.androidlab.sample.runtime.binder.IExampleService;

public class ExampleService extends Service {
    private final IExampleService.Stub mBinder = new IExampleService.Stub() {
        private IExampleCallback mCallback;

        @Override
        public void registerCallback(IExampleCallback callback) {
            mCallback = callback;
        }

        @Override
        public void doSomething() {
            // 执行一些操作
            String result = "Operation Result";
            // 调用客户端回调接口
            if (mCallback != null) {
                try {
                    mCallback.onResult(result);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}

package com.app.androidlab.sample.binder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.app.androidlab.sample.runtime.binder.IExampleCallback;
import com.app.androidlab.sample.runtime.binder.IExampleService;

public class ExampleServiceClient {
    private IExampleService mService;
    private boolean mBound = false;
    private final Context mContext;
    private final IExampleCallback mCallback;

    public ExampleServiceClient(Context context, IExampleCallback callback) {
        this.mContext = context;
        this.mCallback = callback;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IExampleService.Stub.asInterface(service);
            try {
                mService.registerCallback(mCallback);
                mBound = true;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    public void bindService() {
        Intent intent = new Intent("com.example.ExampleService");
        intent.setPackage("com.example"); // 需要根据实际情况设置包名
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbindService() {
        if (mBound) {
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }

    public void doSomething() {
        if (mBound && mService != null) {
            try {
                mService.doSomething();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
}

package com.app.androidlab.sample.binder;


import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.app.androidlab.sample.R;
import com.app.androidlab.sample.runtime.binder.IExampleCallback;

public class BinderDemo1Activity extends AppCompatActivity {
    private ExampleServiceClient mServiceClient;

    private final IExampleCallback.Stub mCallback = new IExampleCallback.Stub() {
        @Override
        public void onResult(String result) {
            runOnUiThread(() -> Toast.makeText(BinderDemo1Activity.this, "Result: " + result, Toast.LENGTH_SHORT).show());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detect_activity_default);

        mServiceClient = new ExampleServiceClient(this, mCallback);

    }

    @Override
    protected void onStart() {
        super.onStart();
        mServiceClient.bindService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mServiceClient.unbindService();
    }

    // 例如在某个按钮点击事件中调用服务的方法
    public void onButtonClick() {
        mServiceClient.doSomething();
    }
}

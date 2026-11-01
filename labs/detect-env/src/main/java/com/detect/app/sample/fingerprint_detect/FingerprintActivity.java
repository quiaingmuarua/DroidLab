package com.detect.app.sample.fingerprint_detect;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.detect.app.sample.R;
import com.detect.app.sample.base.DetectBaseActivity;
import com.detect.app.sample.base.DetectItem;
import com.detect.app.sample.base.Status;
import com.detect.app.sample.fingerprint_detect.gather.FingerprintAllInOne;
import com.detect.app.sample.fingerprint_detect.ndk.SystemPropertyInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.AppInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.HardwareInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.SettingPropInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.SystemInfoCollect;

import java.util.ArrayList;

public class FingerprintActivity extends DetectBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_finger_print);
        recyclerView = findViewById(R.id.detect_frida_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        initializeItemList();
        setupResultAdapter();
    }

    private void initializeItemList() {
        detectItemList = new ArrayList<>();
        detectItemList.add(DetectItem.withResult("settings-fingerprint", "Build and settings based fingerprint signals", Status.JAVACheck, () -> SettingPropInfoCollect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("system-properties", "android.os.SystemProperties based fingerprint collection", Status.NDKCHeck, SystemPropertyInfoCollect::getAllInfo));
        detectItemList.add(DetectItem.withResult("hardware-fingerprint", "CPU, sensors, cameras, MAC, and IMEI related signals", Status.JAVACheck, () -> HardwareInfoCollect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("system-fingerprint", "OS version, fonts, timezone, and related system signals", Status.JAVACheck, () -> SystemInfoCollect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("app-info", "signatures, install time, and installed app list", Status.JAVACheck, () -> AppInfoCollect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("aggregate-fingerprint", "combined device uniqueness fingerprint output", Status.JAVACheck, () -> FingerprintAllInOne.getAllInfo(this)));
    }
}

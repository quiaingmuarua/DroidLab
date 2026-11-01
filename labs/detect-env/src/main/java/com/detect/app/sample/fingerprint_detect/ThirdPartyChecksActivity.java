package com.detect.app.sample.fingerprint_detect;

import android.os.Bundle;
import android.content.Intent;
import com.detect.app.sample.duck.DuckSdkActivity;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.detect.app.sample.R;
import com.detect.app.sample.base.DetectBaseActivity;
import com.detect.app.sample.base.DetectItem;
import com.detect.app.sample.base.Status;
import com.detect.app.sample.fingerprint_detect.third_party_detect.AntiFakerAndroidChecker;

import java.util.ArrayList;

public class ThirdPartyChecksActivity extends DetectBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_frida);
        recyclerView = findViewById(R.id.detect_frida_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        initializeDetectItemList();
        setupResultAdapter();
    }

    private void initializeDetectItemList() {
        detectItemList = new ArrayList<>();
        detectItemList.add(DetectItem.withIntent("Duck Detector SDK", "Original SDK scan and complete JSON report",
                Status.TODOCheck, () -> new Intent(this, DuckSdkActivity.class)));
        detectItemList.add(DetectItem.withResult("AntiFakerAndroidChecker", "external emulator and device spoofing check demo", Status.TODOCheck, () -> AntiFakerAndroidChecker.getAllInfo(this)));
    }
}

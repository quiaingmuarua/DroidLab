package com.detect.app.sample.private_info_detect;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.detect.app.sample.R;
import com.detect.app.sample.base.DetectBaseActivity;
import com.detect.app.sample.base.DetectItem;
import com.detect.app.sample.base.Status;

import java.util.ArrayList;

public class PrivacyActivity extends DetectBaseActivity {

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
        detectItemList.add(DetectItem.withResult("network-detection", "proxy, VPN, SIM, and related network environment checks", Status.TODOCheck, () -> NetworkDetect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("network-collection", "Wi-Fi list, local IP, and public IP collection", Status.TODOCheck, () -> NetworkCollect.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("contacts", "contact list collection demo", Status.TODOCheck, () -> ContactCollect.getAllInfo(this)));
    }
}

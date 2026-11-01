package com.detect.app.sample;

import android.content.Intent;
import android.os.Bundle;
import com.detect.app.sample.duck.DuckSdk;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.detect.app.sample.base.DetectBaseActivity;
import com.detect.app.sample.base.DetectItem;
import com.detect.app.sample.base.Status;
import com.detect.app.sample.fingerprint_detect.FingerprintActivity;
import com.detect.app.sample.fingerprint_detect.ThirdPartyChecksActivity;
import com.detect.app.sample.private_info_detect.PrivacyActivity;
import com.detect.app.sample.risk_detect.RiskDetectionActivity;
public class DetectEnvMainActivity extends DetectBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detect_activity_main);
        DuckSdk.captureLaunchEvidence(getIntent());
        DuckSdk.registerHost(this);
        recyclerView = findViewById(R.id.detect_main_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        initializeItemList();
        setupIntentAdapter();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        DuckSdk.captureLaunchEvidence(intent);
    }

    @Override
    protected void onDestroy() {
        DuckSdk.releaseHost(this);
        super.onDestroy();
    }

    private void initializeItemList() {
        detectItemList.add(DetectItem.withIntent(
                "fingerprint",
                "hardware, system, runtime, and network fingerprint collection",
                Status.TODOCheck,
                () -> new Intent(this, FingerprintActivity.class)
        ));
        detectItemList.add(DetectItem.withIntent(
                "risk-detection",
                "root, xposed, frida, and suspicious device state checks",
                Status.TODOCheck,
                () -> new Intent(this, RiskDetectionActivity.class)
        ));
        detectItemList.add(DetectItem.withIntent(
                "privacy",
                "network, SIM, contacts, and related privacy-relevant data demos",
                Status.TODOCheck,
                () -> new Intent(this, PrivacyActivity.class)
        ));
        detectItemList.add(DetectItem.withIntent(
                "third-party-checks",
                "integration demos for external detection libraries",
                Status.TODOCheck,
                () -> new Intent(this, ThirdPartyChecksActivity.class)
        ));
    }
}

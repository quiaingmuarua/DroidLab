package com.detect.app.sample.risk_detect;

import android.os.Bundle;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.detect.app.sample.R;
import com.detect.app.sample.base.DetectBaseActivity;
import com.detect.app.sample.base.DetectItem;
import com.detect.app.sample.base.Status;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.SystemInfoCollect;
import com.detect.app.sample.risk_detect.file_detect.FileContentCheck;
import com.detect.app.sample.risk_detect.file_detect.FileExistCheck;
import com.detect.app.sample.risk_detect.memory_detect.MemoryCheck;
import com.detect.app.sample.risk_detect.shell_detect.ShellCheck;
import com.detect.app.sample.risk_detect.system_detect.ClassLoaderStateCheck;
import com.detect.app.sample.risk_detect.system_detect.DebugStateCheck;
import com.detect.app.sample.risk_detect.system_detect.DevicePropertyCheck;
import com.detect.app.sample.risk_detect.system_detect.PmcCheck;

import java.util.ArrayList;

public class RiskDetectionActivity extends DetectBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_root);
        recyclerView = findViewById(R.id.detect_frida_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        initializeItemList();
        setupResultAdapter();
    }

    private void initializeItemList() {
        detectItemList = new ArrayList<>();
        detectItemList.add(DetectItem.withResult("sensitive-files", "check common root-related files such as su paths", Status.NDKCHeck, FileExistCheck::getAllInfo));
        detectItemList.add(DetectItem.withResult("file-content", "scan proc and related files for suspicious markers", Status.NDKCHeck, FileContentCheck::getAllInfo));
        detectItemList.add(DetectItem.withResult("shell-check", "run shell-based checks from Java", Status.NDKCHeck, ShellCheck::getAllInfo));
        detectItemList.add(DetectItem.withResult("memory-check", "inspect memory state for suspicious signals", Status.NDKCHeck, () -> MemoryCheck.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("device-properties", "inspect suspicious device properties", Status.NDKCHeck, () -> DevicePropertyCheck.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("debug-state", "collect debugger, ADB, developer options, and debug-related build flags", Status.JAVACheck, () -> DebugStateCheck.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("class-loader-state", "collect app, thread, and system class loader chains and dex path state", Status.JAVACheck, () -> ClassLoaderStateCheck.getAllInfo(this)));
        detectItemList.add(DetectItem.withResult("suspicious-apps", "check whether risky apps are installed", Status.NDKCHeck, PmcCheck::getAllInfo));
        detectItemList.add(DetectItem.withResult("system-state", "accessibility mode, boot time, and related system state", Status.NDKCHeck, () -> SystemInfoCollect.getAllInfo(this)));
    }
}

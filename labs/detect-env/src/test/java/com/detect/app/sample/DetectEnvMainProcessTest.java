package com.detect.app.sample;

import static org.junit.Assert.*;
import org.junit.Test;

public class DetectEnvMainProcessTest {
    @Test public void onlyTheExactMainProcessStartsHttpAndWorker() {
        String app = "com.detect.app.sample";
        assertTrue(DetectEnvApplication.isMainProcess(app, app));
        assertFalse(DetectEnvApplication.isMainProcess(app, app + ":helper_probe"));
        assertFalse(DetectEnvApplication.isMainProcess(app, app + ":heap_residue"));
        assertFalse(DetectEnvApplication.isMainProcess(app, "com.android.webview:sandboxed_process0"));
        assertFalse(DetectEnvApplication.isMainProcess(app, null));
    }
}

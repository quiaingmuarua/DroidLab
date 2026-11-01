package com.detect.app.sample.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class DetectEnvDevicePropertiesTest {

    @Test
    public void exposesStableAndroidPropertiesWithoutClientIdentity() {
        Context context = RuntimeEnvironment.getApplication();
        DetectEnvDeviceProperties provider =
                new DetectEnvDeviceProperties(context);

        Map<String, String> properties = provider.getProperties(context);

        assertEquals("android", properties.get("runtime"));
        assertEquals("detect-env", properties.get("capability"));
        assertEquals(context.getPackageName(), properties.get("packageName"));
        assertEquals("33", properties.get("sdkInt"));
        assertNotNull(properties.get("versionName"));
        assertNotNull(properties.get("manufacturer"));
        assertNotNull(properties.get("model"));
        assertFalse(properties.containsKey("clientWorkerKey"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> properties.put("unexpected", "true")
        );
    }

    @Test
    public void rejectsMissingContexts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DetectEnvDeviceProperties(null)
        );
        DetectEnvDeviceProperties provider = new DetectEnvDeviceProperties(
                RuntimeEnvironment.getApplication()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.getProperties(null)
        );
    }
}

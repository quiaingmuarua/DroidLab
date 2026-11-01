package com.detect.app.sample.duck

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.view.ViewGroup
import android.webkit.WebView
import com.detect.app.sample.BuildConfig
import com.eltavine.duckdetector.core.detector.run
import com.eltavine.duckdetector.sdk.DuckDetector
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** The target-side SDK integration. Only the original SDK produces detection results. */
object DuckSdk {
    private val gate = DuckScanGate()
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    @Volatile private var launchCapture: JsonObject = JsonObject()
    @Volatile private var samplerState: JsonObject = JsonObject()
    @Volatile private var samplerReference = WeakReference<WebView>(null)
    @Volatile private var hostActivity = WeakReference<Activity>(null)

    @JvmStatic
    fun registerHost(activity: Activity) {
        hostActivity = WeakReference(activity)
    }

    @JvmStatic
    fun releaseHost(activity: Activity) {
        if (hostActivity.get() === activity) {
            destroyMountSampler(samplerReference.get())
            hostActivity.clear()
        }
    }

    private suspend fun prepareSampler() = withContext(Dispatchers.Main) {
        if (samplerReference.get()?.parent != null) return@withContext
        val activity = hostActivity.get()
        check(activity != null && !activity.isDestroyed) {
            "Open detect-env through android.app.NativeActivity before scanning"
        }
        installMountSampler(activity, activity.findViewById(android.R.id.content))
    }

    @JvmStatic
    fun captureLaunchEvidence(intent: Intent?) {
        DuckDetector.captureLaunchEvidence(intent)
        launchCapture = JsonObject().apply {
            addProperty("captureCalledAtMillis", System.currentTimeMillis())
            addProperty("lifetime", "process-start")
            add("intentExtraKeys", DuckReportJson.strings(intent?.extras?.keySet()?.sorted().orEmpty()))
        }
    }

    @JvmStatic
    fun installMountSampler(activity: Activity, parent: ViewGroup): WebView? {
        val sampler = DuckDetector.createProcMountSampler(activity)
        if (sampler != null) parent.addView(sampler, ViewGroup.LayoutParams(1, 1))
        samplerReference = WeakReference(sampler)
        samplerState = JsonObject().apply {
            addProperty("attemptedAtMillis", System.currentTimeMillis())
            addProperty("attachedAtSetup", sampler != null)
        }
        return sampler
    }

    @JvmStatic
    fun destroyMountSampler(sampler: WebView?) {
        sampler?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
            if (samplerReference.get() === it) samplerReference.clear()
            samplerState = samplerState.deepCopy().apply {
                addProperty("destroyedAtMillis", System.currentTimeMillis())
            }
        }
    }

    @JvmStatic
    fun parseDetectorIds(payload: String): List<String>? =
        parseDetectorIds(payload, DuckDetector.detectors.map { it.id.value })

    internal fun parseDetectorIds(payload: String, available: List<String>): List<String>? {
        val input = JsonParser.parseString(payload).asJsonObject
        if (!input.has("detectorIds")) return null
        val value = input.get("detectorIds")
        require(value.isJsonArray && value.asJsonArray.size() > 0) { "detectorIds must be a nonempty array" }
        val ids = value.asJsonArray.map { item ->
            require(item.isJsonPrimitive && item.asJsonPrimitive.isString) { "detectorIds must contain strings" }
            item.asString
        }
        require(ids.distinct().size == ids.size) { "detectorIds must not repeat" }
        require(ids.all { it in available }) { "Unknown detector ID" }
        return ids
    }

    @JvmStatic
    fun scanBlocking(context: Context, detectorIds: List<String>?): String =
        runBlocking { scanJson(context, detectorIds) }

    suspend fun scanJson(context: Context, detectorIds: List<String>? = null): String =
        withContext(Dispatchers.IO) {
            gate.run {
                // WebView must precede helper binding, but ordinary app startup
                // must not initialize it when the Duck feature is not in use.
                prepareSampler()
                val available = DuckDetector.detectors
                val requested = detectorIds ?: available.map { it.id.value }
                require(requested.isNotEmpty() && requested.distinct().size == requested.size &&
                    requested.all { id -> available.any { it.id.value == id } }) { "Invalid detector selection" }
                val started = System.currentTimeMillis()
                val results = if (detectorIds == null) DuckDetector.scan(context) else coroutineScope {
                    requested.map { id -> async { available.first { it.id.value == id }.run(context.applicationContext) } }.awaitAll()
                }
                check(results.map { it.id.value } == requested) { "SDK returned an incomplete or unexpected result set" }
                val visibility = DuckDetector.packageVisibility(context)
                val samplerSnapshot = withContext(Dispatchers.Main) {
                    samplerState.deepCopy().apply {
                        addProperty("attached", samplerReference.get()?.parent != null)
                    }
                }
                val output = JsonObject().apply {
                    addProperty("proofScope", "sdk-integration-baseline")
                    add("sdk", JsonObject().apply {
                        addProperty("tag", BuildConfig.DUCK_SDK_TAG)
                        addProperty("commit", BuildConfig.DUCK_SDK_COMMIT)
                        addProperty("aarSha256", BuildConfig.DUCK_SDK_SHA256)
                    })
                    addProperty("scanId", UUID.randomUUID().toString())
                    addProperty("pid", Process.myPid())
                    addProperty("startedAtMillis", started)
                    addProperty("finishedAtMillis", System.currentTimeMillis())
                    addProperty("apiLevel", Build.VERSION.SDK_INT)
                    add("abis", DuckReportJson.strings(Build.SUPPORTED_ABIS.asList()))
                    add("requestedDetectorIds", detectorIds?.let(DuckReportJson::strings) ?: JsonNull.INSTANCE)
                    add("executedDetectorIds", DuckReportJson.strings(results.map { it.id.value }))
                    add("results", DuckReportJson.array(results.map(DuckReportJson::result)))
                    add("host", JsonObject().apply {
                        add("app", JsonObject().apply {
                            addProperty("packageName", context.packageName)
                            addProperty("versionName", BuildConfig.VERSION_NAME)
                            addProperty("versionCode", BuildConfig.VERSION_CODE)
                            addProperty("targetSdk", context.applicationInfo.targetSdkVersion)
                        })
                        add("launchCapture", launchCapture.deepCopy())
                        add("mountSampler", samplerSnapshot)
                        add("permissions", JsonObject().apply {
                            listOf(Manifest.permission.QUERY_ALL_PACKAGES, Manifest.permission.USE_BIOMETRIC,
                                Manifest.permission.USE_FINGERPRINT, Manifest.permission.INTERNET,
                                Manifest.permission.ACCESS_NETWORK_STATE).forEach { permission ->
                                addProperty(permission, context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
                            }
                        })
                        add("packageVisibility", JsonObject().apply {
                            addProperty("scope", visibility.scope.name)
                            addProperty("visiblePackageCount", visibility.visiblePackageCount)
                            addProperty("suspiciouslyLow", visibility.suspiciouslyLow)
                        })
                        add("consents", DuckReportJson.array(available.flatMap { it.consents }
                            .distinctBy { it.id.value }.map { consent ->
                                JsonObject().apply {
                                    addProperty("id", consent.id.value)
                                    addProperty("decision", consent.decisions(context).first().name)
                                }
                            }))
                    })
                }
                output.addProperty("reportText", DuckReportText.render(output))
                gson.toJson(output)
            }
        }
}

//
// Created by lxj07 on 2023/12/11.
//

#include "fingerprint.h"
#include <jni.h>
#include <sys/system_properties.h>
extern "C"
JNIEXPORT jstring JNICALL
Java_com_detect_app_sample_fingerprint_1detect_ndk_SystemPropertyInfoCollect_ndk_1device_1info(
        JNIEnv *env, jclass clazz, jstring property) {

    char temp[128] = "0";
    const char* nativeString = env->GetStringUTFChars(property, nullptr);
    __system_property_get(nativeString, temp);

    env->ReleaseStringUTFChars(property, nativeString);

    return env->NewStringUTF(temp);
}



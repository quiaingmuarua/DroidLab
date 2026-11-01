#include <jni.h>
#include "md5.h"


JNIEXPORT jstring JNICALL
Java_com_app_protect_sample_crypto_NdkCryptoUtils_md5FromNative(JNIEnv *env, jobject thiz,
                                                                        jstring input_str) {
//    char * str="hello from c in ndk module";
    char *native_str = (*env)->GetStringUTFChars(env, input_str, NULL);
    uint8_t result[16];
    char resultStr[33];
    md5String(native_str, result);
    uint8_to_hex_str(result, 16, resultStr);

    (*env)->ReleaseStringUTFChars(env, input_str, native_str); // 释放 native_str
//    char *str = "hello from c in ndk module";
    return (*env)->NewStringUTF(env, resultStr);

}

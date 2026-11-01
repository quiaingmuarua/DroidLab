#include <stdio.h>
#include <string.h>
#include "log_utils.h"
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "header/common_detect_c.h"



JNIEXPORT jint JNICALL
Java_com_app_protect_sample_fundamental_SysLibUtils_check_1file_1exist_1c(
        JNIEnv *env, jclass clazz, jstring file_path) {
    return check_files_exist(env,clazz,file_path);
}

JNIEXPORT jstring JNICALL
Java_com_detect_app_sample_tool_ShellHelper_exec_1shell_1ndk_1c(JNIEnv *env, jclass clazz,
                                                                         jstring cmd) {

    const char *cstr = (*env)->GetStringUTFChars(env, cmd, JNI_FALSE);
    char *output =executeCommand(cstr);
    // 将C字符串转换为Java字符串
    jstring javaResult = (*env)->NewStringUTF(env, output);
    // 释放资源
    (*env)->ReleaseStringUTFChars(env, cmd, cstr);
    if (output != NULL) {
        printf("Result: %s\n", output);
        free(output); // 释放动态分配的内存
    }
    return javaResult;


}


JNIEXPORT jint JNICALL
Java_com_detect_app_sample_tool_FileHelper_check_1file_1exist_1c(JNIEnv *env,
                                                                                     jclass clazz,
                                                                                     jstring file_path) {
    return check_files_exist(env,clazz,file_path);
}


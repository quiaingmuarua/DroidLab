#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <malloc.h>
#include <unistd.h>
#include <pthread.h>
#include <fcntl.h>
#include <elf.h>
#include <dirent.h>
#include <ctype.h>

#include <sys/prctl.h>
#include <sys/stat.h>
#include <asm/unistd.h>
#include <android/log.h>
#include "log_utils.h"
#include "syscall_arch.h"
#include "syscalls.h"
#include "mylibc.h"


JNIEXPORT jint JNICALL
Java_com_app_protect_sample_fundamental_SysCallUtils_check_1file_1exist_1syscall(
        JNIEnv *env, jclass clazz, jstring file_path) {

    const char *cstr = (*env)->GetStringUTFChars(env, file_path, JNI_FALSE);

    int fd = my_openat(AT_FDCWD, cstr, O_RDONLY | O_CLOEXEC, 0);
    // 释放资源
    (*env)->ReleaseStringUTFChars(env, file_path, cstr);
    if (fd >= 0) {
        LOGD("su files has been found");
        return 1;
    } else {
        LOGD("su files has not been found");
        return 0;
    }


}



JNIEXPORT jint JNICALL
Java_com_detect_app_sample_tool_FileHelper_check_1file_1exist_1svc(JNIEnv *env,
                                                                                       jclass clazz,
                                                                                       jstring file_path) {
    const char *cstr = (*env)->GetStringUTFChars(env, file_path, JNI_FALSE);

    int fd = my_openat(AT_FDCWD, cstr, O_RDONLY | O_CLOEXEC, 0);
    // 释放资源
    (*env)->ReleaseStringUTFChars(env, file_path, cstr);
    if (fd >= 0) {
        LOGD("su files has been found");
        return 1;
    } else {
        LOGD("su files has not been found");
        return 0;
    }
}

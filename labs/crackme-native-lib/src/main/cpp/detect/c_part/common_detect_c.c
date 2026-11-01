#include <stdio.h>
#include <string.h>
#include "log_utils.h"
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int check_files_exist(JNIEnv *env, jclass clazz,
                      jstring file_path) {
    const char *cstr = (*env)->GetStringUTFChars(env, file_path, JNI_FALSE);
    FILE *fp;
    fp = fopen(cstr, "r");
    // 释放资源
    (*env)->ReleaseStringUTFChars(env, file_path, cstr);
    if (fp) {
        LOGD("risk files has been found");
        return 1;
    } else {
        LOGD("risk files has not been found");
        return 0;
    }

}

char* executeCommand(const char * cmd) {
    FILE *pipe;
    char buffer[128];
    char *result = NULL;
    size_t result_size = 0;

    pipe = popen(cmd, "r");
    if (pipe == NULL) {
        LOGD("popen failed\n");
        return NULL;
    }

    while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
        size_t buffer_len = strlen(buffer);
        result = realloc(result, result_size + buffer_len + 1); // 动态调整result的大小
        if (result == NULL) {
            LOGD("Memory allocation failed\n");
            pclose(pipe);
            return NULL;
        }
        memcpy(result + result_size, buffer, buffer_len); // 将buffer复制到result中
        result_size += buffer_len;
        result[result_size] = '\0'; // 添加字符串结束符
    }

    pclose(pipe);

    return result;
}

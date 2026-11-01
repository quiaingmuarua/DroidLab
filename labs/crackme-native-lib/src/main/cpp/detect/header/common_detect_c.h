//
// Created by lxj07 on 2024/1/10.
//

#ifndef LEARNANDROID_COMMON_DETECT_C_H
#define LEARNANDROID_COMMON_DETECT_C_H
#include <jni.h>
int check_files_exist(JNIEnv *env, jclass clazz,
                      jstring file_path);

char* executeCommand(const char * cmd);

#endif //LEARNANDROID_COMMON_DETECT_C_H

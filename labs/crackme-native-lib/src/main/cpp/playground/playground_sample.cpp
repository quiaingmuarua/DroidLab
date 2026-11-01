#include <jni.h>
#include <stddef.h>
#include <string>
#include "jni.h"

JNIEXPORT jstring JNICALL
DynamicString(JNIEnv *env, jclass thiz) {
    std::string hello = "Hello from dynamic string";
    return env->NewStringUTF(hello.c_str());
}


jstring  nativeString(JNIEnv *env){

    return env->NewStringUTF("Hello from stringFromJNICpp");

}



// com.example.protect_app_env_sample
#define JNI_CLASS "com/app/androidlab/sample/playground/SimpleObjUtils" //定义native方法的java文件


/**
 * 方法数组，JNINativeMethod的第一个参数是Java中的方法名，第二个参数是函数签名，第三个参数是对应的方法指针，
 * java方法的签名一定要与对应的C++方法参数类型一致，否则注册方法可能失败
 * **/
static JNINativeMethod method_table[] = {

        {"stringFromDynamic", "()Ljava/lang/String;", (void *) DynamicString},
};


/**
 * 默认会调用的方法，这里把方法注册好
 **/
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
//OnLoad方法是没有JNIEnv参数的，需要通过vm获取。
    JNIEnv *env = NULL;
    if (vm->AttachCurrentThread(&env, NULL) == JNI_OK) {
        //获取对应声明native方法的Java类
        jclass clazz = env->FindClass(JNI_CLASS);
        if (clazz == NULL) {
            return JNI_FALSE;
        }
        //注册方法，成功返回正确的JNIVERSION。
        if (env->RegisterNatives(clazz, method_table,
                                 sizeof(method_table) / sizeof(method_table[0])) == JNI_OK) {
            return JNI_VERSION_1_4;
        }
    }
    return JNI_FALSE;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_app_androidlab_sample_playground_SimpleObjUtils_stringFromJNICpp(JNIEnv *env,
                                                                            jclass clazz) {
    return nativeString(env);
}






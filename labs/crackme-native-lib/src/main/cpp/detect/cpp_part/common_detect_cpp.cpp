#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <stdio.h>
#include "log_utils.h"
#include <cstring>
#include <cstdlib>
#include "common_detect_cpp.h"
#include <string>

void nativeProcMapDetect(char * path,char * found){

    int fd=open(path,O_RDONLY);
    if(fd==-1){
        LOGE("failed to open file: %s",path);
    }
    FILE *file= fdopen(fd,"r");//将文件描述词转成文件指针
    char* line= nullptr;
    size_t len = 0;
    while(getline(&line,&len,file)!=-1){

            const char* edxp = "edxp;magisk;zygisk;Hook;frida";
           std::string foundStr=checkSubstringsInString(edxp,line);
           found=strdup(foundStr.c_str());

    }

}

//edxp;magisk;zygisk;Hook;frida


bool nativeFileDetect(const char * path, bool usesyscall) {
    if (usesyscall) {
        // 使用系统调用的方式，查找文件是否存在
        long ret = syscall(SYS_faccessat, AT_FDCWD, path, F_OK);
        if (ret == 0) {
            LOGI("JNI syscall: %s", path);
            return true;
        } else {
            // 可以在这里添加错误处理逻辑
            return false;
        }
    } else {
        struct stat buf{};
        // 使用POSIX方法检查文件是否存在
        if (access(path, F_OK) == 0) {
            LOGI("JNI access: %s", path);
            return true;
        }
        if (stat(path, &buf) == 0) {
            LOGI("JNI stat: %s", path);
            return true;
        }
        int fd = open(path, O_PATH);
        if (fd != -1) {
            if (fstat(fd, &buf) == 0) {
                LOGI("JNI fstat: %s", path);
                close(fd);
                return true;
            }
            close(fd);
        }
    }
    return false;
}





std::string checkSubstringsInString(const char* substrings, const char* target) {
    std::string foundSubstrings;

    if (!substrings || !target) {
        return foundSubstrings;
    }

    char* substringsCopy = strdup(substrings); // 复制原字符串，因为 strtok 会修改字符串
    char* token = strtok(substringsCopy, ";");

    while (token != nullptr) {
        if (strstr(target, token) != nullptr) {
            if (!foundSubstrings.empty()) {
                foundSubstrings += ";";
            }
            foundSubstrings += token;
        }
        token = strtok(nullptr, ";");
    }

    free(substringsCopy); // 释放复制的字符串
    return foundSubstrings;
}

const char* customStrStr(const char* haystack, const char* needle) {
    if (!needle || !haystack) {
        return nullptr;
    }

    for (const char* h = haystack; *h != '\0'; ++h) {
        bool found = true;
        for (const char* n = needle, *h2 = h; *n != '\0'; ++n, ++h2) {
            if (*h2 != *n) {
                found = false;
                break;
            }
        }
        if (found) {
            return h;
        }
    }
    return nullptr;
}

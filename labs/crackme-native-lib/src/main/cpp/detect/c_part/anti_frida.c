#include <stdio.h>
#include <string.h>
#include "log_utils.h"
#include <jni.h>

int find_mem_string(unsigned long long start, unsigned long long end, char *bytes, unsigned int len) {

    char *pmem = (char*)start;
    int matched = 0;

    while ((unsigned long long)pmem <(unsigned long long) (end - len)) {

        if(*pmem == bytes[0]) {

            matched = 1;
            char *p = pmem + 1;

            while (*p == bytes[matched] && (unsigned long long)p < end) {
                matched ++;
                p ++;
            }

            if (matched >= len) {
                LOGD("foud frida");
                return 1;
            }
        }

        pmem ++;

    }
    return 0;
}


int scan_executable_segment(char * map) {
    char buf[512];
    unsigned long long start, end;

    sscanf(map, "%llx-%llx %s", &start, &end, buf);

    if (buf[2] == 'x'  && (strstr(map,".apk") == NULL)) {
        //LOGD("map %s",map);
        // LOGD("frida %llx %llx",start,end);
        return ((find_mem_string(start, end, (char *)"libfrida", 8) ));
    } else {
        return 0;
    }
}



int check_frida_maps(){
    char map[512] = {0};
    FILE *fd = NULL;
    int num_found = 0;
    fd = fopen("/proc/self/maps","r");
    if(fd != NULL){
        //   LOGD("okk");
        while (!feof(fd)){
            fgets(map,512,fd);
            //    LOGD("%s",map);
            if (scan_executable_segment(map) == 1) {
                num_found++;

            }

        }
        if(num_found >=1){
            LOGD("found frida by maps");
            fclose(fd);
            return 1;
        }
    }else{
        LOGD("can not open maps");
    }

    return 0;

}

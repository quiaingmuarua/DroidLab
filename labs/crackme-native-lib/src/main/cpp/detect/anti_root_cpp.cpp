


#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <vector>
#include <set>

// 辅助函数：分割字符串
std::vector<std::string> split(const std::string &str, char delim) {
    std::stringstream ss(str);
    std::string token;
    std::vector<std::string> tokens;
    while (std::getline(ss, token, delim)) {
        tokens.push_back(token);
    }
    return tokens;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_detect_app_sample_root_1detect_FileContentCheck_cppCheckNDKFileSensitiveWord(
        JNIEnv *env, jclass clazz, jstring filePath, jstring sensitiveWords) {
    std::set<std::string> foundWords;
    const char *path = env->GetStringUTFChars(filePath, nullptr);
    std::ifstream file(path);
    env->ReleaseStringUTFChars(filePath, path);

    if (!file.is_open()) {
        return env->NewStringUTF(""); // 文件无法打开
    }

    const char *wordsStr = env->GetStringUTFChars(sensitiveWords, nullptr);
    std::vector<std::string> words = split(wordsStr, ';');
    env->ReleaseStringUTFChars(sensitiveWords, wordsStr);

    std::string line;
    while (std::getline(file, line)) {
        for (const auto &word: words) {
            if (line.find(word) != std::string::npos) {
                foundWords.insert(word);
            }
        }
    }

    file.close();

    std::string result;
    for (const auto &word: foundWords) {
        if (!result.empty()) {
            result += ",";
        }
        result += word;
    }

    return env->NewStringUTF(result.c_str());
}



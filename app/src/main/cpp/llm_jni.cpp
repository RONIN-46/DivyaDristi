#include <jni.h>
#include <android/log.h>
#include <string>
#include "llama.h"
#include "llm/InferenceEngine.h"

#define TAG "LLM"

static InferenceEngine engine;
static llama_model * model = nullptr;
static llama_context * ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL

Java_com_krushna_divyadrishti_llm_LLMNative_loadModel(
        JNIEnv *env,
        jobject,
        jstring modelPath) {
    __android_log_print(
            ANDROID_LOG_INFO,
            TAG,
            "Native loadModel called");

    const char *path =
            env->GetStringUTFChars(modelPath, nullptr);

    bool ok =
            engine.loadModel(path);

    env->ReleaseStringUTFChars(
            modelPath,
            path);

    return ok;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_generate(
        JNIEnv *env,
        jobject,
        jstring prompt) {

    const char *text =
            env->GetStringUTFChars(prompt, nullptr);

    std::string response =
            engine.generate(text);

    env->ReleaseStringUTFChars(
            prompt,
            text);

    return env->NewStringUTF(
            response.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_release(
        JNIEnv *,
        jobject) {

    engine.release();
}
#include <jni.h>
#include <android/log.h>
#include <string>
#include "llama.h"

#define TAG "LLM"

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_loadModel(
        JNIEnv *env,
        jobject,
        jstring modelPath) {

    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    __android_log_print(ANDROID_LOG_INFO, TAG,
                        "Loading model: %s", path);

    llama_backend_init();

    llama_model_params model_params =
            llama_model_default_params();

    model = llama_model_load_from_file(path, model_params);

    if (model == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR,
                            TAG,
                            "Model loading failed");

        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    llama_context_params ctx_params =
            llama_context_default_params();

    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = 4;

    ctx = llama_init_from_model(model, ctx_params);

    if (ctx == nullptr) {

        __android_log_print(ANDROID_LOG_ERROR,
                            TAG,
                            "Context creation failed");

        llama_model_free(model);
        model = nullptr;

        env->ReleaseStringUTFChars(modelPath, path);

        return JNI_FALSE;
    }

    __android_log_print(ANDROID_LOG_INFO,
                        TAG,
                        "Model loaded successfully");

    env->ReleaseStringUTFChars(modelPath, path);

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_generate(
        JNIEnv *env,
        jobject,
        jstring prompt) {

    return env->NewStringUTF("Model Loaded");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_release(
        JNIEnv *,
        jobject) {

    if (ctx) {
        llama_free(ctx);
        ctx = nullptr;
    }

    if (model) {
        llama_model_free(model);
        model = nullptr;
    }

    llama_backend_free();
}
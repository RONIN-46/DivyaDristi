#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_krushna_divyadrishti_llm_LLMNative_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {

    return env->NewStringUTF("Native library loaded!");
}
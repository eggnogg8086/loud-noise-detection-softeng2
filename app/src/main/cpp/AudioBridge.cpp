#include <jni.h>
#include <memory>
#include "AudioEngine.h"

static std::unique_ptr<AudioEngine> gEngine;
static JavaVM* gJvm = nullptr;
static jobject gCallback = nullptr;
static jmethodID gOnSpectrumMethod = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeInit(
        JNIEnv* env, jobject thiz, jobject callback)
{
env->GetJavaVM(&gJvm);
gCallback = env->NewGlobalRef(callback);
jclass clazz = env->GetObjectClass(callback);
gOnSpectrumMethod = env->GetMethodID(clazz, "onSpectrum", "([FF)V");

gEngine = std::make_unique<AudioEngine>([](const float* data, int size, float db) {
    JNIEnv* env2;
    bool attached = false;
    if (gJvm->GetEnv((void**)&env2, JNI_VERSION_1_6) != JNI_OK) {
        gJvm->AttachCurrentThread(&env2, nullptr);
        attached = true;
    }
    jfloatArray arr = env2->NewFloatArray(size);
    env2->SetFloatArrayRegion(arr, 0, size, data);
    env2->CallVoidMethod(gCallback, gOnSpectrumMethod, arr, (jfloat)db);
    env2->DeleteLocalRef(arr);
    if (attached) gJvm->DetachCurrentThread();
});
}

JNIEXPORT jboolean JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeStart(JNIEnv*, jobject, jint deviceId, jint inputPreset) {
    return gEngine && gEngine->start(deviceId, inputPreset) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeStop(JNIEnv*, jobject) {
if (gEngine) gEngine->stop();
}

JNIEXPORT void JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeDestroy(JNIEnv* env, jobject) {
gEngine.reset();
if (gCallback) {
env->DeleteGlobalRef(gCallback);
gCallback = nullptr;
}
}

} // extern "C"
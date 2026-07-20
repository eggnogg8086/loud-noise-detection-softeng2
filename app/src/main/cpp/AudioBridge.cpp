#include <jni.h>
#include <memory>
#include <fstream>
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
gOnSpectrumMethod = env->GetMethodID(clazz, "onSpectrum", "([FFF)V");

gEngine = std::make_unique<AudioEngine>([](const float* data, int size, float db, float balance) {
    JNIEnv* env2;
    bool attached = false;
    if (gJvm->GetEnv((void**)&env2, JNI_VERSION_1_6) != JNI_OK) {
        gJvm->AttachCurrentThread(&env2, nullptr);
        attached = true;
    }
    jfloatArray arr = env2->NewFloatArray(size);
    env2->SetFloatArrayRegion(arr, 0, size, data);
    env2->CallVoidMethod(gCallback, gOnSpectrumMethod, arr, (jfloat)db, (jfloat)balance);
    env2->DeleteLocalRef(arr);
    if (attached) gJvm->DetachCurrentThread();
});
}

JNIEXPORT jboolean JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeStart(
        JNIEnv* env, jobject, jint deviceId, jint inputPreset,
        jfloat sensitivity, jfloatArray freqs, jfloatArray gains,
        jboolean useAWeighting) {

    std::vector<float> vFreqs;
    std::vector<float> vGains;

    if (freqs != nullptr && gains != nullptr) {
        jsize len = env->GetArrayLength(freqs);
        vFreqs.resize(len);
        vGains.resize(len);
        env->GetFloatArrayRegion(freqs, 0, len, vFreqs.data());
        env->GetFloatArrayRegion(gains, 0, len, vGains.data());
    }

    return gEngine && gEngine->start(deviceId, inputPreset, sensitivity, vFreqs, vGains, useAWeighting) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeStop(JNIEnv*, jobject) {
if (gEngine) gEngine->stop();
}

JNIEXPORT jint JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeGetSessionId(JNIEnv*, jobject) {
    return gEngine ? gEngine->getSessionId() : -1;
}

JNIEXPORT void JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeStartRecording(JNIEnv*, jobject) {
    if (gEngine) gEngine->startRecordingSnippet();
}

void writeWavHeader(std::ofstream& file, int dataSize, int sampleRate) {
    file.write("RIFF", 4);
    int fileSize = 36 + dataSize;
    file.write(reinterpret_cast<const char*>(&fileSize), 4);
    file.write("WAVE", 4);
    file.write("fmt ", 4);
    int fmtSize = 16;
    file.write(reinterpret_cast<const char*>(&fmtSize), 4);
    short format = 1; // PCM
    file.write(reinterpret_cast<const char*>(&format), 2);
    short channels = 1;
    file.write(reinterpret_cast<const char*>(&channels), 2);
    file.write(reinterpret_cast<const char*>(&sampleRate), 4);
    int byteRate = sampleRate * 2; // 16-bit mono
    file.write(reinterpret_cast<const char*>(&byteRate), 4);
    short blockAlign = 2;
    file.write(reinterpret_cast<const char*>(&blockAlign), 2);
    short bps = 16;
    file.write(reinterpret_cast<const char*>(&bps), 2);
    file.write("data", 4);
    file.write(reinterpret_cast<const char*>(&dataSize), 4);
}

JNIEXPORT jboolean JNICALL
Java_com_loudnoisedetectionapp_AudioBridge_nativeGetSnippet(JNIEnv* env, jobject, jstring path) {
    if (!gEngine) return JNI_FALSE;

    std::vector<float> data = gEngine->stopRecordingSnippet();
    if (data.empty()) return JNI_FALSE;

    const char* cPath = env->GetStringUTFChars(path, nullptr);
    std::ofstream file(cPath, std::ios::binary);

    if (!file.is_open()) {
        env->ReleaseStringUTFChars(path, cPath);
        return JNI_FALSE;
    }

    int dataSize = data.size() * 2; // 16-bit PCM
    writeWavHeader(file, dataSize, AudioEngine::kSampleRate);

    for (float s : data) {
        // Clamp and convert to 16-bit PCM
        float clamped = std::max(-1.0f, std::min(1.0f, s));
        short pcm = static_cast<short>(clamped * 32767.0f);
        file.write(reinterpret_cast<const char*>(&pcm), 2);
    }

    file.close();
    env->ReleaseStringUTFChars(path, cPath);
    return JNI_TRUE;
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
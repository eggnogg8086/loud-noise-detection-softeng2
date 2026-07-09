#include "AudioEngine.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine(SpectrumCallback cb)
        : mCallback(std::move(cb)),
          mInputBuffer(kFftSize, 0.f),
          mFftOutput(kSpectrumSize + 1)
{
    mFftCfg = kiss_fftr_alloc(kFftSize, 0, nullptr, nullptr);

    // Hann window
    mWindow.resize(kFftSize);
    for (int i = 0; i < kFftSize; i++) {
        mWindow[i] = 0.5f * (1.f - cosf(2.f * M_PI * i / (kFftSize - 1)));
    }
}

AudioEngine::~AudioEngine() {
    stop();
    kiss_fftr_free(mFftCfg);
}

bool AudioEngine::start(int deviceId, int inputPreset) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Mono)
            ->setSampleRate(kSampleRate)
            ->setInputPreset(static_cast<oboe::InputPreset>(inputPreset))
            ->setDataCallback(this);

    if (deviceId != -1) {
        builder.setDeviceId(deviceId);
    }

    oboe::Result result = builder.openStream(mStream);

    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return false;
    }

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Stream started, sample rate: %d", mStream->getSampleRate());
    return true;
}
void AudioEngine::stop() {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames)
{
    auto* samples = static_cast<float*>(audioData);

    for (int i = 0; i < numFrames; i++) {
        mInputBuffer[mBufferPos++] = samples[i];

        if (mBufferPos >= kFftSize) {
            mBufferPos = 0;

            // Apply Hann window
            std::vector<float> windowed(kFftSize);
            for (int j = 0; j < kFftSize; j++) {
                windowed[j] = mInputBuffer[j] * mWindow[j];
            }

            // Run FFT
            kiss_fftr(mFftCfg, windowed.data(), mFftOutput.data());

            // Calculate overall RMS for dB monitoring
            float sumSq = 0;
            for (float s : mInputBuffer) sumSq += s * s;
            float rms = sqrtf(sumSq / kFftSize);
            // Reference adjusted: 0.00002 is standard 0dB SPL, 1.0 (full scale) is approx 94-100dB SPL on many mics.
            // We use +120 as a baseline for 130dB max scale.
            float dbSPL = 20.f * log10f(fmaxf(rms, 1e-10f)) + 120.f;

            // Convert to dB spectrum for visualization
            std::vector<float> spectrum(kSpectrumSize);
            for (int j = 0; j < kSpectrumSize; j++) {
                float re = mFftOutput[j].r;
                float im = mFftOutput[j].i;
                float magnitude = sqrtf(re * re + im * im) / kFftSize;
                float db = 20.f * log10f(fmaxf(magnitude, 1e-10f));
                spectrum[j] = fmaxf(0.f, fminf(70.f, db + 90.f));
            }

            mCallback(spectrum.data(), kSpectrumSize, dbSPL);
        }
    }
    return oboe::DataCallbackResult::Continue;
}
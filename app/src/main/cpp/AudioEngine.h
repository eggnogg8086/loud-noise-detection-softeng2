#pragma once
#include <oboe/Oboe.h>
#include <jni.h>
#include <functional>
#include <vector>
#include "kissfft/kiss_fftr.h"

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    static constexpr int kSampleRate   = 44100;
    static constexpr int kFftSize      = 8192;
    static constexpr int kSpectrumSize = kFftSize / 2;

    using SpectrumCallback = std::function<void(const float*, int, float)>;

    explicit AudioEngine(SpectrumCallback cb);
    ~AudioEngine();

    bool start(int deviceId = -1, int inputPreset = 9);
    void stop();

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames) override;

private:
    SpectrumCallback mCallback;
    std::shared_ptr<oboe::AudioStream> mStream;

    kiss_fftr_cfg mFftCfg;
    std::vector<float> mWindow;
    std::vector<float> mInputBuffer;
    std::vector<kiss_fft_cpx> mFftOutput;
    int mBufferPos = 0;
};
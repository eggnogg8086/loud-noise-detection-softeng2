#pragma once
#include <oboe/Oboe.h>
#include <jni.h>
#include <functional>
#include <vector>
#include "kissfft/kiss_fftr.h"

class AudioEngine : public oboe::AudioStreamDataCallback, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kSampleRate   = 44100;
    static constexpr int kFftSize      = 8192;
    static constexpr int kSpectrumSize = kFftSize / 2;

    using SpectrumCallback = std::function<void(const float*, int, float, float, float, float, float, float, bool, int)>;

    explicit AudioEngine(SpectrumCallback cb);
    ~AudioEngine();

    bool start(int deviceId = -1, int inputPreset = 9, float sensitivity = -999.0f,
               const std::vector<float>& freqs = {}, const std::vector<float>& gains = {},
               bool useAWeighting = true, bool useNoiseRejection = true,
               float calibrationOffset = 0.0f, int integrationTime = 0);
    void stop();
    int getSessionId() const;
    void setMovementIntensity(float intensity);

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

    void startRecordingSnippet();
    std::vector<float> stopRecordingSnippet();

private:
    SpectrumCallback mCallback;
    std::shared_ptr<oboe::AudioStream> mStream;

    kiss_fftr_cfg mFftCfg;
    std::vector<float> mWindow;
    std::vector<float> mInputBuffer;
    std::vector<kiss_fft_cpx> mFftOutput;
    std::vector<float> mFreqCompensation;
    std::vector<float> mAWeighting;
    bool mUseAWeighting = true;
    bool mUseNoiseRejection = true;
    bool mRejectionActive = false;
    float mSensitivity = -999.0f;
    float mMovementIntensity = 0.0f;
    float mCalibrationOffset = 0.0f;
    int mIntegrationTime = 0;
    int mBufferPos = 0;

    float mLeftSumSq = 0;
    float mRightSumSq = 0;
    int mEnergyCount = 0;

    // Smoothed values for Fast/Slow integration
    float mSmoothedDb = 0.0f;
    bool mIsFirstBuffer = true;

    // Circular buffer for snippets (last 5 seconds)
    static constexpr int kCircularBufferSize = kSampleRate * 5;
    std::vector<float> mCircularBuffer;
    int mCircularBufferPos = 0;

    // Episode recording state
    bool mIsRecordingEpisode = false;
    std::vector<float> mEpisodeBuffer;
};

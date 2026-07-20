#include "AudioEngine.h"
#include <cmath>
#include <android/log.h>

#define LOG_TAG "AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

AudioEngine::AudioEngine(SpectrumCallback cb)
        : mCallback(std::move(cb)),
          mInputBuffer(kFftSize, 0.f),
          mFftOutput(kSpectrumSize + 1),
          mCircularBuffer(kCircularBufferSize, 0.f)
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

bool AudioEngine::start(int deviceId, int inputPreset, float sensitivity,
                         const std::vector<float>& freqs, const std::vector<float>& gains,
                         bool useAWeighting) {
    mSensitivity = sensitivity;
    mUseAWeighting = useAWeighting;

    float binWidth = (float)kSampleRate / kFftSize;

    // Pre-calculate A-weighting gains
    mAWeighting.assign(kSpectrumSize, 1.0f);
    for (int i = 0; i < kSpectrumSize; i++) {
        float f = i * binWidth;
        if (f < 1e-3f) f = 1e-3f; // Avoid division by zero

        float f2 = f * f;
        float ra = (powf(12194.f, 2.f) * powf(f, 4.f)) /
                   ((f2 + powf(20.6f, 2.f)) * sqrtf((f2 + powf(107.7f, 2.f)) * (f2 + powf(737.9f, 2.f))) * (f2 + powf(12194.f, 2.f)));
        float aDb = 20.f * log10f(ra) + 2.00f;
        mAWeighting[i] = powf(10.f, aDb / 20.f);
    }

    // Pre-calculate frequency compensation gains
    mFreqCompensation.assign(kSpectrumSize, 1.0f);
    if (!freqs.empty() && freqs.size() == gains.size()) {
        for (int i = 0; i < kSpectrumSize; i++) {
            float centerFreq = i * binWidth;

            // Simple linear interpolation
            if (centerFreq <= freqs.front()) {
                mFreqCompensation[i] = powf(10.f, -gains.front() / 20.f);
            } else if (centerFreq >= freqs.back()) {
                mFreqCompensation[i] = powf(10.f, -gains.back() / 20.f);
            } else {
                for (size_t j = 0; j < freqs.size() - 1; j++) {
                    if (centerFreq >= freqs[j] && centerFreq <= freqs[j+1]) {
                        float t = (centerFreq - freqs[j]) / (freqs[j+1] - freqs[j]);
                        float gainDb = gains[j] + t * (gains[j+1] - gains[j]);
                        mFreqCompensation[i] = powf(10.f, -gainDb / 20.f);
                        break;
                    }
                }
            }
        }
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setSampleRate(kSampleRate)
            ->setInputPreset(static_cast<oboe::InputPreset>(inputPreset))
            ->setSessionId(oboe::SessionId::Allocate)
            ->setDataCallback(this)
            ->setErrorCallback(this);

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

int AudioEngine::getSessionId() const {
    return mStream ? mStream->getSessionId() : -1;
}

void AudioEngine::startRecordingSnippet() {
    mEpisodeBuffer.clear();
    mIsRecordingEpisode = true;
}

std::vector<float> AudioEngine::stopRecordingSnippet() {
    mIsRecordingEpisode = false;

    // Combine circular buffer (pre-trigger) and episode buffer
    std::vector<float> combined;
    combined.reserve(kCircularBufferSize + mEpisodeBuffer.size());

    // Add circular buffer in correct order
    for (int i = 0; i < kCircularBufferSize; i++) {
        combined.push_back(mCircularBuffer[(mCircularBufferPos + i) % kCircularBufferSize]);
    }

    // Add episode buffer
    combined.insert(combined.end(), mEpisodeBuffer.begin(), mEpisodeBuffer.end());

    mEpisodeBuffer.clear();
    return combined;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream* stream,
        void* audioData,
        int32_t numFrames)
{
    auto* samples = static_cast<float*>(audioData);
    int channelCount = stream->getChannelCount();

    for (int i = 0; i < numFrames; i++) {
        float left, right;
        if (channelCount == 2) {
            left = samples[i * 2];
            right = samples[i * 2 + 1];
        } else {
            left = samples[i];
            right = left;
        }

        mLeftSumSq += left * left;
        mRightSumSq += right * right;
        mEnergyCount++;

        float monoMix = (left + right) * 0.5f;

        // Fill circular buffer (mono for snippets)
        mCircularBuffer[mCircularBufferPos] = monoMix;
        mCircularBufferPos = (mCircularBufferPos + 1) % kCircularBufferSize;

        // Fill episode buffer if recording
        if (mIsRecordingEpisode) {
            mEpisodeBuffer.push_back(monoMix);
            if (mEpisodeBuffer.size() > kSampleRate * 30) {
                mIsRecordingEpisode = false;
            }
        }

        mInputBuffer[mBufferPos++] = monoMix;

        if (mBufferPos >= kFftSize) {
            mBufferPos = 0;

            // Apply Hann window
            std::vector<float> windowed(kFftSize);
            for (int j = 0; j < kFftSize; j++) {
                windowed[j] = mInputBuffer[j] * mWindow[j];
            }

            // Run FFT
            kiss_fftr(mFftCfg, windowed.data(), mFftOutput.data());

            // Convert to dB spectrum for visualization AND calculate dBA SPL
            std::vector<float> spectrum(kSpectrumSize);
            float sumWeightedSq = 0;

            for (int j = 0; j < kSpectrumSize; j++) {
                float re = mFftOutput[j].r;
                float im = mFftOutput[j].i;
                float magnitude = sqrtf(re * re + im * im) / kFftSize;

                // Apply frequency compensation (hardware calibration)
                magnitude *= mFreqCompensation[j];

                // Apply A-Weighting if enabled
                if (mUseAWeighting) {
                    magnitude *= mAWeighting[j];
                }

                // Sum energy for SPL calculation
                sumWeightedSq += magnitude * magnitude;

                float db = 20.f * log10f(fmaxf(magnitude, 1e-10f));
                spectrum[j] = fmaxf(0.f, fminf(70.f, db + 90.f));
            }

            // Calculate overall SPL
            // We multiply by 2 because we only sum half the spectrum (real FFT)
            float rmsWeighted = sqrtf(sumWeightedSq * 2.0f);

            // Reference adjusted: MicrophoneInfo.getSensitivity() is dBFS for 94dB SPL.
            float effectiveSensitivity = (mSensitivity < -900.f) ? -37.0f : mSensitivity;
            float dbSPL = 20.f * log10f(fmaxf(rmsWeighted, 1e-10f)) + 94.f - effectiveSensitivity;

            // Calculate Stereo Balance: -1.0 (Left) to 1.0 (Right)
            float rmsL = sqrtf(mLeftSumSq / mEnergyCount);
            float rmsR = sqrtf(mRightSumSq / mEnergyCount);
            float balance = 0.0f;
            if (rmsL + rmsR > 1e-6f) {
                balance = (rmsR - rmsL) / (rmsL + rmsR);
            }

            // Reset energy accumulators
            mLeftSumSq = 0;
            mRightSumSq = 0;
            mEnergyCount = 0;

            mCallback(spectrum.data(), kSpectrumSize, dbSPL, balance);
        }
    }
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) {
    if (error == oboe::Result::ErrorDisconnected) {
        LOGI("Audio stream disconnected, attempting to restart...");
        // In a real scenario, you might want to notify the Java layer to restart
        // with the correct parameters, but for now, we'll log it.
        // The Service's AudioFocus listener or a Watchdog can also handle this.
    } else {
        LOGE("Audio stream error: %s", oboe::convertToText(error));
    }
}
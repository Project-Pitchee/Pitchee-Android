#include "pitchee/pitchee.h"
#include "pitchee/spectrum.h"

#include <jni.h>
#include <iterator>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

void throw_illegal_state(JNIEnv* env, const char* message) {
    jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message && *message ? message : "PitcheeCore error");
}

struct ProgressBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

struct F0FrameBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

struct VadBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

struct SpectrumBridge {
    JNIEnv* env;
    jobject callback;
    jmethodID method;
};

void report_progress(const pitchee_progress_t* progress, void* user_data) {
    auto* bridge = static_cast<ProgressBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || progress == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jint>(progress->stage),
        static_cast<jlong>(progress->completed),
        static_cast<jlong>(progress->total),
        static_cast<jfloat>(progress->fraction)
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

void report_f0_frame(const pitchee_f0_frame_t* frame, void* user_data) {
    auto* bridge = static_cast<F0FrameBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || frame == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jdouble>(frame->timestamp_seconds),
        static_cast<jfloat>(frame->f0_hz),
        static_cast<jfloat>(frame->confidence),
        frame->voiced != 0
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

void report_vad_segment(const pitchee_vad_segment_t* segment, void* user_data) {
    auto* bridge = static_cast<VadBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || segment == nullptr) {
        return;
    }
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jdouble>(segment->start_seconds),
        static_cast<jdouble>(segment->end_seconds)
    );
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

void report_spectrum_frame(
    const pitchee_spectrum_frame_t* frame,
    void* user_data
) {
    auto* bridge = static_cast<SpectrumBridge*>(user_data);
    if (bridge == nullptr || bridge->callback == nullptr || bridge->method == nullptr
        || frame == nullptr) {
        return;
    }

    jfloatArray magnitudes = bridge->env->NewFloatArray(
        static_cast<jsize>(frame->bin_count)
    );
    if (magnitudes == nullptr) return;
    bridge->env->SetFloatArrayRegion(
        magnitudes,
        0,
        static_cast<jsize>(frame->bin_count),
        frame->magnitudes
    );
    bridge->env->CallVoidMethod(
        bridge->callback,
        bridge->method,
        static_cast<jdouble>(frame->timestamp_seconds),
        magnitudes,
        static_cast<jlong>(frame->first_bin_index),
        static_cast<jfloat>(frame->bin_hz),
        static_cast<jfloat>(frame->peak_hz),
        static_cast<jfloat>(frame->centroid_hz),
        static_cast<jfloat>(frame->rolloff_hz),
        static_cast<jfloat>(frame->flatness)
    );
    bridge->env->DeleteLocalRef(magnitudes);
    if (bridge->env->ExceptionCheck()) bridge->env->ExceptionClear();
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeVersion(JNIEnv* env, jclass) {
    return env->NewStringUTF(pitchee_version());
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeCreate(
    JNIEnv* env,
    jclass,
    jstring model_directory,
    jint threads,
    jint use_coreml,
    jfloat min_f0_hz,
    jfloat max_f0_hz,
    jfloat min_confidence
) {
    if (model_directory == nullptr) {
        throw_illegal_state(env, "model directory is null");
        return 0;
    }

    const char* model_chars = env->GetStringUTFChars(model_directory, nullptr);
    pitchee_analyzer_t* analyzer = nullptr;
    char error[1024] = {};
    const auto status = pitchee_analyzer_create(
        model_chars,
        threads,
        use_coreml,
        min_f0_hz,
        max_f0_hz,
        min_confidence,
        &analyzer,
        error,
        sizeof(error)
    );
    env->ReleaseStringUTFChars(model_directory, model_chars);

    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(analyzer);
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_analyzer_destroy(reinterpret_cast<pitchee_analyzer_t*>(handle));
}

extern "C" JNIEXPORT jstring JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeAnalyze(
    JNIEnv* env,
    jobject,
    jlong handle_value,
    jfloatArray samples,
    jint sample_rate,
    jint channels,
    jint score_profile,
    jobject progress_callback
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(handle_value);
    if (analyzer == nullptr || samples == nullptr) {
        throw_illegal_state(env, "analyzer or PCM buffer is null");
        return nullptr;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jmethodID progress_method = nullptr;
    if (progress_callback != nullptr) {
        jclass callback_class = env->GetObjectClass(progress_callback);
        progress_method = env->GetMethodID(
            callback_class,
            "onProgress",
            "(IJJF)V"
        );
        env->DeleteLocalRef(callback_class);
        if (progress_method == nullptr) return nullptr;
    }
    ProgressBridge progress_bridge{env, progress_callback, progress_method};

    char* json = nullptr;
    char error[1024] = {};
    const auto status = pitchee_analyze_pcm(
        analyzer,
        pcm.data(),
        pcm.size(),
        sample_rate,
        channels,
        static_cast<pitchee_score_profile_t>(score_profile),
        progress_callback == nullptr ? nullptr : report_progress,
        progress_callback == nullptr ? nullptr : &progress_bridge,
        &json,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    jstring result = env->NewStringUTF(json);
    pitchee_string_free(json);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeDetectVad(
    JNIEnv* env,
    jobject,
    jlong handle_value,
    jfloatArray samples,
    jint sample_rate,
    jint channels,
    jobject segment_callback
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(handle_value);
    if (analyzer == nullptr || samples == nullptr) {
        throw_illegal_state(env, "analyzer or PCM buffer is null");
        return;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jmethodID segment_method = nullptr;
    if (segment_callback != nullptr) {
        jclass callback_class = env->GetObjectClass(segment_callback);
        segment_method = env->GetMethodID(callback_class, "onSegment", "(DD)V");
        env->DeleteLocalRef(callback_class);
        if (segment_method == nullptr) return;
    }
    VadBridge vad_bridge{env, segment_callback, segment_method};

    char error[1024] = {};
    const auto status = pitchee_analyzer_detect_vad(
        analyzer,
        pcm.data(),
        pcm.size(),
        sample_rate,
        channels,
        segment_callback == nullptr ? nullptr : report_vad_segment,
        segment_callback == nullptr ? nullptr : &vad_bridge,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeRunVoiceHealth(
    JNIEnv* env,
    jobject,
    jlong handle_value,
    jfloatArray samples,
    jint sample_rate,
    jint channels,
    jobject progress_callback
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(handle_value);
    if (analyzer == nullptr || samples == nullptr) {
        throw_illegal_state(env, "analyzer or PCM buffer is null");
        return nullptr;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jmethodID progress_method = nullptr;
    if (progress_callback != nullptr) {
        jclass callback_class = env->GetObjectClass(progress_callback);
        progress_method = env->GetMethodID(callback_class, "onProgress", "(IJJF)V");
        env->DeleteLocalRef(callback_class);
        if (progress_method == nullptr) return nullptr;
    }
    ProgressBridge progress_bridge{env, progress_callback, progress_method};

    pitchee_voice_health_result_t result{};
    char error[1024] = {};
    const auto status = pitchee_analysis_run_voice_health(
        analyzer,
        pcm.data(),
        pcm.size(),
        sample_rate,
        channels,
        progress_callback == nullptr ? nullptr : report_progress,
        progress_callback == nullptr ? nullptr : &progress_bridge,
        &result,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    const auto& features = result.features;
    const double values[] = {
        result.health_score,
        result.disorder_probability,
        result.decision_threshold,
        result.is_disordered != 0 ? 1.0 : 0.0,
        features.hnr_db,
        features.cpps_db,
        features.jitter_local,
        features.jitter_rap,
        features.jitter_ppq5,
        features.shimmer_local,
        features.shimmer_apq3,
        features.shimmer_apq5,
        features.f0_median,
        features.f0_iqr,
        features.f0_cv,
        features.voiced_fraction,
        features.intensity_median,
        features.intensity_iqr,
    };
    jdoubleArray output = env->NewDoubleArray(static_cast<jsize>(std::size(values)));
    if (output != nullptr) {
        env->SetDoubleArrayRegion(
            output,
            0,
            static_cast<jsize>(std::size(values)),
            values
        );
    }
    return output;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeScore(
    JNIEnv*,
    jclass,
    jint score_profile,
    jdouble vfp_standard_score,
    jdouble naturalness_score,
    jdouble f0_hz
) {
    pitchee_score_result_t result{};
    const auto status = pitchee_score(
        static_cast<pitchee_score_profile_t>(score_profile),
        vfp_standard_score,
        naturalness_score,
        f0_hz,
        &result
    );
    if (status != PITCHEE_SUCCESS) return -1.0;
    return static_cast<jdouble>(result.final_score);
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeAnalyzer_nativeRealtimeF0Create(
    JNIEnv* env,
    jobject,
    jlong analyzer_handle,
    jint context_samples,
    jint hop_samples,
    jdouble output_rate_hz,
    jdouble start_timestamp_seconds
) {
    auto* analyzer = reinterpret_cast<pitchee_analyzer_t*>(analyzer_handle);
    if (analyzer == nullptr) {
        throw_illegal_state(env, "analyzer is null");
        return 0;
    }

    pitchee_realtime_f0_t* stream = nullptr;
    char error[1024] = {};
    pitchee_realtime_f0_options_t options{};
    options.struct_size = sizeof(options);
    options.version = PITCHEE_REALTIME_F0_OPTIONS_VERSION;
    options.context_samples = context_samples;
    options.inference_hop_samples = hop_samples;
    options.output_rate_hz = output_rate_hz;
    options.start_timestamp_seconds = start_timestamp_seconds;
    const auto status = pitchee_realtime_f0_create_with_options(
        analyzer,
        &options,
        &stream,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(stream);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeMetadata(
    JNIEnv* env,
    jobject,
    jlong stream_handle
) {
    auto* stream = reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle);
    if (stream == nullptr) {
        throw_illegal_state(env, "realtime F0 stream is null");
        return nullptr;
    }

    pitchee_realtime_f0_metadata_t metadata{};
    metadata.struct_size = sizeof(metadata);
    metadata.version = PITCHEE_REALTIME_F0_METADATA_VERSION;
    char error[1024] = {};
    const auto status = pitchee_realtime_f0_get_metadata(
        stream,
        &metadata,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    const double values[] = {
        static_cast<double>(metadata.sample_rate),
        static_cast<double>(metadata.frame_hop_samples),
        metadata.frame_interval_seconds,
        static_cast<double>(metadata.min_context_samples),
        metadata.model_output_rate_hz,
        metadata.effective_output_rate_hz,
    };
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(std::size(values)));
    if (result != nullptr) {
        env->SetDoubleArrayRegion(
            result,
            0,
            static_cast<jsize>(std::size(values)),
            values
        );
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeWarmup(
    JNIEnv* env,
    jobject,
    jlong stream_handle
) {
    auto* stream = reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle);
    if (stream == nullptr) {
        throw_illegal_state(env, "realtime F0 stream is null");
        return;
    }

    char error[1024] = {};
    const auto status = pitchee_realtime_f0_warmup(stream, error, sizeof(error));
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
    }
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeStats(
    JNIEnv* env,
    jobject,
    jlong stream_handle
) {
    auto* stream = reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle);
    if (stream == nullptr) {
        throw_illegal_state(env, "realtime F0 stream is null");
        return nullptr;
    }

    pitchee_realtime_f0_stats_t stats{};
    stats.struct_size = sizeof(stats);
    stats.version = PITCHEE_REALTIME_F0_STATS_VERSION;
    char error[1024] = {};
    const auto status = pitchee_realtime_f0_get_stats(
        stream,
        &stats,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return nullptr;
    }

    const double values[] = {
        static_cast<double>(stats.process_calls),
        static_cast<double>(stats.inference_calls),
        static_cast<double>(stats.emitted_frames),
        stats.total_ms,
        stats.last_total_ms,
        stats.average_total_ms,
        stats.last_preprocess_ms,
        stats.average_preprocess_ms,
        stats.last_model_ms,
        stats.average_model_ms,
        stats.last_postprocess_ms,
        stats.average_postprocess_ms,
        stats.measured_output_hz,
    };
    jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(std::size(values)));
    if (result != nullptr) {
        env->SetDoubleArrayRegion(
            result,
            0,
            static_cast<jsize>(std::size(values)),
            values
        );
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeProcess(
    JNIEnv* env,
    jobject,
    jlong stream_handle,
    jfloatArray samples,
    jobject frame_callback
) {
    auto* stream = reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle);
    if (stream == nullptr || samples == nullptr || frame_callback == nullptr) {
        throw_illegal_state(env, "realtime F0 stream or PCM buffer is null");
        return;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jclass callback_class = env->GetObjectClass(frame_callback);
    jmethodID frame_method = env->GetMethodID(
        callback_class,
        "onFrame",
        "(DFFZ)V"
    );
    env->DeleteLocalRef(callback_class);
    if (frame_method == nullptr) return;

    F0FrameBridge frame_bridge{env, frame_callback, frame_method};
    char error[1024] = {};
    const auto status = pitchee_realtime_f0_process(
        stream,
        pcm.data(),
        pcm.size(),
        report_f0_frame,
        &frame_bridge,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeReset(
    JNIEnv*,
    jobject,
    jlong stream_handle
) {
    pitchee_realtime_f0_reset(reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle));
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeRealtimeF0_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong stream_handle
) {
    pitchee_realtime_f0_destroy(reinterpret_cast<pitchee_realtime_f0_t*>(stream_handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeCreate(
    JNIEnv* env,
    jclass,
    jint fft_size,
    jint hop_samples,
    jint min_hz,
    jint max_hz,
    jint value_type,
    jfloat smoothing
) {
    pitchee_spectrum_options_t options{};
    options.fft_size = fft_size;
    options.hop_samples = hop_samples;
    options.min_hz = min_hz;
    options.max_hz = max_hz;
    options.value_type = static_cast<pitchee_spectrum_value_t>(value_type);
    options.smoothing = smoothing;

    pitchee_spectrum_t* spectrum = nullptr;
    char error[1024] = {};
    const auto status = pitchee_spectrum_create(
        &options,
        &spectrum,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
        return 0;
    }
    return reinterpret_cast<jlong>(spectrum);
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeProcess(
    JNIEnv* env,
    jobject,
    jlong handle,
    jfloatArray samples,
    jobject frame_callback
) {
    auto* spectrum = reinterpret_cast<pitchee_spectrum_t*>(handle);
    if (spectrum == nullptr || samples == nullptr || frame_callback == nullptr) {
        throw_illegal_state(env, "spectrum or PCM buffer is null");
        return;
    }

    const jsize count = env->GetArrayLength(samples);
    std::vector<float> pcm(static_cast<size_t>(count));
    env->GetFloatArrayRegion(samples, 0, count, pcm.data());

    jclass callback_class = env->GetObjectClass(frame_callback);
    jmethodID frame_method = env->GetMethodID(
        callback_class,
        "onFrame",
        "(D[FJFFFFF)V"
    );
    env->DeleteLocalRef(callback_class);
    if (frame_method == nullptr) return;

    SpectrumBridge bridge{env, frame_callback, frame_method};
    char error[1024] = {};
    const auto status = pitchee_spectrum_process(
        spectrum,
        pcm.data(),
        pcm.size(),
        report_spectrum_frame,
        &bridge,
        error,
        sizeof(error)
    );
    if (status != PITCHEE_SUCCESS) {
        throw_illegal_state(env, error);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeReset(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_spectrum_reset(reinterpret_cast<pitchee_spectrum_t*>(handle));
}

extern "C" JNIEXPORT void JNICALL
Java_space_pitchee_core_PitcheeSpectrum_nativeDestroy(
    JNIEnv*,
    jobject,
    jlong handle
) {
    pitchee_spectrum_destroy(reinterpret_cast<pitchee_spectrum_t*>(handle));
}

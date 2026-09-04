// Detector-only adaptation of PineappleTwilight/houri-engine ncnn_jni.cpp
// 85351aa3822fe2611f68cfd092972e6ac573f203, GPL-3.0.
// Yomotsu changes: validated buffers/layouts, opaque handles, serialized lifetime,
// explicit return codes and dimensions for BOTH outputs. See dbnet-licenses.
#include <jni.h>
#include <memory>
#include <mutex>
#include <map>
#include <new>
#include "net.h"
#include "buffer_contract.h"

namespace {
std::mutex runtime_mutex;
std::map<jlong, std::unique_ptr<ncnn::Net>> models;
jlong next_handle = 1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_eu_kanade_translation_detection_DbnetNativeBackend_createNative(
        JNIEnv* env, jobject, jstring param, jstring weights) {
    std::lock_guard<std::mutex> guard(runtime_mutex);
    if (!param || !weights) return 0;
    const char* p = env->GetStringUTFChars(param, nullptr);
    if (!p) return 0;
    const char* b = env->GetStringUTFChars(weights, nullptr);
    if (!b) { env->ReleaseStringUTFChars(param, p); return 0; }
    std::unique_ptr<ncnn::Net> net(new (std::nothrow) ncnn::Net());
    int result = -1;
    if (net) {
        net->opt.use_vulkan_compute = false;
        net->opt.num_threads = 2;
        result = net->load_param(p);
        if (result == 0) result = net->load_model(b);
    }
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(weights, b);
    if (result != 0 || !net) return 0;
    const jlong handle = next_handle++;
    models.emplace(handle, std::move(net));
    return handle;
}

extern "C" JNIEXPORT void JNICALL
Java_eu_kanade_translation_detection_DbnetNativeBackend_releaseNative(
        JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> guard(runtime_mutex);
    models.erase(handle);
}

extern "C" JNIEXPORT jint JNICALL
Java_eu_kanade_translation_detection_DbnetNativeBackend_inferNative(
        JNIEnv* env, jobject, jlong handle, jfloatArray input, jint w, jint h,
        jfloatArray db_out, jfloatArray mask_out, jintArray dimensions) {
    std::lock_guard<std::mutex> guard(runtime_mutex);
    auto model = models.find(handle);
    if (model == models.end()) return -1;
    if (!input || !db_out || !mask_out || !dimensions) return -2;
    if (!dbnet::valid_input(w, h, env->GetArrayLength(input)) ||
        env->GetArrayLength(dimensions) != 6) return -2;
    const std::size_t area = static_cast<std::size_t>(w) * h;
    ncnn::Mat image(w, h, 3);
    if (image.empty()) return -3;
    for (int c = 0; c < 3; ++c) {
        env->GetFloatArrayRegion(input, static_cast<jsize>(c * area), static_cast<jsize>(area), image.channel(c));
        if (env->ExceptionCheck()) return -4;
    }
    ncnn::Extractor extractor = model->second->create_extractor();
    if (extractor.input("in0", image) != 0) return -5;
    ncnn::Mat db, mask;
    if (extractor.extract("out0", db) != 0 || extractor.extract("out1", mask) != 0) return -5;
    if (db.empty() || mask.empty() || db.c != 2 || mask.c != 1 ||
        !dbnet::valid_output(db.dims, db.w, db.h, db.d, db.c, db.elemsize, db.elempack,
                            env->GetArrayLength(db_out), area) ||
        !dbnet::valid_output(mask.dims, mask.w, mask.h, mask.d, mask.c, mask.elemsize, mask.elempack,
                            env->GetArrayLength(mask_out), area)) return -6;
    const int db_area = db.w * db.h;
    for (int c = 0; c < db.c; ++c) {
        env->SetFloatArrayRegion(db_out, c * db_area, db_area, db.channel(c));
        if (env->ExceptionCheck()) return -4;
    }
    env->SetFloatArrayRegion(mask_out, 0, mask.w * mask.h, mask.channel(0));
    if (env->ExceptionCheck()) return -4;
    const jint shape[6] = {db.w, db.h, db.c, mask.w, mask.h, mask.c};
    env->SetIntArrayRegion(dimensions, 0, 6, shape);
    return env->ExceptionCheck() ? -4 : 0;
}

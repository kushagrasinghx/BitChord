// Runs one Automix model through a given libonnxruntime.so and writes every
// output tensor to a file, so the reduced runtime can be compared with the
// official one on a real phone: same model, same input, same thread count.
//
//   parity <libonnxruntime.so> <model.onnx> <beat|vocals> <all|basic> <out.bin>
//
// Built and run by parity.sh beside this file. The input is a fixed
// pseudo-random tensor of the model's shape, identical on every run.
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "onnxruntime_c_api.h"

static const OrtApi* api;

#define CHECK(expr)                                                     \
    do {                                                                \
        OrtStatus* _s = (expr);                                         \
        if (_s) {                                                       \
            fprintf(stderr, "%s: %s\n", #expr, api->GetErrorMessage(_s)); \
            exit(2);                                                    \
        }                                                               \
    } while (0)

static double now_ms(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1e3 + t.tv_nsec / 1e6;
}

int main(int argc, char** argv) {
    if (argc != 6) {
        fprintf(stderr, "usage: parity <lib> <model> <beat|vocals> <all|basic> <out>\n");
        return 1;
    }
    void* lib = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!lib) { fprintf(stderr, "dlopen: %s\n", dlerror()); return 1; }
    const OrtApiBase* (*get_base)(void) = dlsym(lib, "OrtGetApiBase");
    api = get_base()->GetApi(ORT_API_VERSION);
    if (!api) { fprintf(stderr, "runtime does not offer API %d\n", ORT_API_VERSION); return 1; }

    int beat = strcmp(argv[3], "beat") == 0;
    int64_t shape_beat[] = {1, 1500, 128};
    int64_t shape_vocals[] = {1, 2, 2049, 960};
    int64_t* shape = beat ? shape_beat : shape_vocals;
    size_t rank = beat ? 3 : 4;
    size_t count = 1;
    for (size_t i = 0; i < rank; i++) count *= (size_t)shape[i];

    // A log-mel-like range for the beat model, a magnitude-like one for vocals.
    float* input = malloc(count * sizeof(float));
    uint32_t state = 12345;
    for (size_t i = 0; i < count; i++) {
        state = state * 1664525u + 1013904223u;
        float u = (state >> 8) / 16777216.0f;
        input[i] = beat ? u * 6.0f - 1.0f : u * u * 2.0f;
    }

    OrtEnv* env;
    CHECK(api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "parity", &env));
    OrtSessionOptions* options;
    CHECK(api->CreateSessionOptions(&options));
    CHECK(api->SetIntraOpNumThreads(options, 1));
    CHECK(api->SetSessionGraphOptimizationLevel(
        options, strcmp(argv[4], "basic") == 0 ? ORT_ENABLE_BASIC : ORT_ENABLE_ALL));
    CHECK(api->DisableCpuMemArena(options));
    CHECK(api->DisableMemPattern(options));

    double t0 = now_ms();
    OrtSession* session;
    CHECK(api->CreateSession(env, argv[2], options, &session));
    double load_ms = now_ms() - t0;

    OrtMemoryInfo* memory;
    CHECK(api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memory));
    OrtValue* tensor = NULL;
    CHECK(api->CreateTensorWithDataAsOrtValue(memory, input, count * sizeof(float), shape, rank,
                                              ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &tensor));

    const char* in_names[] = {beat ? "input_spectrogram" : "mix_magnitude"};
    const char* out_beat[] = {"beat", "downbeat"};
    const char* out_vocals[] = {"target_magnitude"};
    const char** out_names = beat ? out_beat : out_vocals;
    size_t outputs = beat ? 2 : 1;
    OrtValue* results[2] = {NULL, NULL};

    // One warm-up, then the timed run whose outputs are written.
    CHECK(api->Run(session, NULL, in_names, (const OrtValue* const*)&tensor, 1, out_names, outputs, results));
    for (size_t i = 0; i < outputs; i++) { api->ReleaseValue(results[i]); results[i] = NULL; }
    t0 = now_ms();
    CHECK(api->Run(session, NULL, in_names, (const OrtValue* const*)&tensor, 1, out_names, outputs, results));
    double run_ms = now_ms() - t0;

    FILE* out = fopen(argv[5], "wb");
    for (size_t i = 0; i < outputs; i++) {
        OrtTensorTypeAndShapeInfo* info;
        CHECK(api->GetTensorTypeAndShape(results[i], &info));
        size_t n;
        CHECK(api->GetTensorShapeElementCount(info, &n));
        float* data;
        CHECK(api->GetTensorMutableData(results[i], (void**)&data));
        fwrite(data, sizeof(float), n, out);
        api->ReleaseTensorTypeAndShapeInfo(info);
    }
    fclose(out);
    printf("%s %s %s load=%.0fms run=%.0fms\n", argv[3], argv[4], argv[1], load_ms, run_ms);
    return 0;
}

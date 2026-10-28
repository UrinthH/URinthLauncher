/**
 * Makes OpenSL ES recordings that ask for the "generic" preset use the "voice communication"
 * preset instead, so the phone applies echo cancelling and noise reduction to them.
 * See opensl_preset.h for the reason, and VoiceChatAudio.java for when it is switched on.
 *
 * How: libOpenSLES keeps one shared table of functions for every "Android configuration"
 * interface. We make a throw-away recorder (never started) to find that table, and replace its
 * SetConfiguration entry with a function that changes the one value and calls the original.
 * It affects only the process this runs in, and only when the launcher asks for it.
 */
#include <jni.h>
#include <dlfcn.h>
#include <string.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "opensl_preset.h"

#define TAG "opensl_preset"
#include <log.h>

typedef SLresult (*set_configuration_fn)(SLAndroidConfigurationItf, const SLchar *, const void *, SLuint32);

static set_configuration_fn original_set_configuration;

static SLresult hooked_set_configuration(SLAndroidConfigurationItf self, const SLchar *key,
                                         const void *value, SLuint32 size) {
    uint32_t replacement;
    if (urinth_preset_rewrite((const char *) key, value, size,
                              (const char *) SL_ANDROID_KEY_RECORDING_PRESET,
                              SL_ANDROID_RECORDING_PRESET_GENERIC,
                              SL_ANDROID_RECORDING_PRESET_VOICE_COMMUNICATION, &replacement)) {
        SLuint32 preset = replacement;
        return original_set_configuration(self, key, &preset, sizeof(preset));
    }
    return original_set_configuration(self, key, value, size);
}

/* Result codes handed to Java: 0 done, 1 already done, negative: what went wrong. */
#define RESULT_NO_LIBRARY (-10)
#define RESULT_NO_SYMBOL (-11)
#define RESULT_NO_ENGINE (-12)
#define RESULT_NO_RECORDER (-13)
#define RESULT_NO_CONFIG (-14)

static int install(void) {
    void *lib = dlopen("libOpenSLES.so", RTLD_NOW);
    if (lib == NULL) return RESULT_NO_LIBRARY;

    typedef SLresult (*create_engine_fn)(SLObjectItf *, SLuint32, const SLEngineOption *, SLuint32,
                                         const SLInterfaceID *, const SLboolean *);
    create_engine_fn create_engine = (create_engine_fn) dlsym(lib, "slCreateEngine");
    const SLInterfaceID *iid_engine = (const SLInterfaceID *) dlsym(lib, "SL_IID_ENGINE");
    const SLInterfaceID *iid_queue = (const SLInterfaceID *) dlsym(lib, "SL_IID_ANDROIDSIMPLEBUFFERQUEUE");
    const SLInterfaceID *iid_config = (const SLInterfaceID *) dlsym(lib, "SL_IID_ANDROIDCONFIGURATION");
    if (!create_engine || !iid_engine || !iid_queue || !iid_config) return RESULT_NO_SYMBOL;

    SLObjectItf engine_object = NULL;
    SLEngineItf engine = NULL;
    if (create_engine(&engine_object, 0, NULL, 0, NULL, NULL) != SL_RESULT_SUCCESS) return RESULT_NO_ENGINE;
    if ((*engine_object)->Realize(engine_object, SL_BOOLEAN_FALSE) != SL_RESULT_SUCCESS
            || (*engine_object)->GetInterface(engine_object, *iid_engine, &engine) != SL_RESULT_SUCCESS) {
        (*engine_object)->Destroy(engine_object);
        return RESULT_NO_ENGINE;
    }

    // A recorder that is only created, never realized or started: all we want from it is the
    // address of the configuration interface's function table.
    SLDataLocator_IODevice device = {SL_DATALOCATOR_IODEVICE, SL_IODEVICE_AUDIOINPUT,
                                     SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
    SLDataSource source = {&device, NULL};
    SLDataLocator_AndroidSimpleBufferQueue queue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
    SLDataFormat_PCM format = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                               SL_PCMSAMPLEFORMAT_FIXED_16, SL_SPEAKER_FRONT_CENTER,
                               SL_BYTEORDER_LITTLEENDIAN};
    SLDataSink sink = {&queue, &format};
    const SLInterfaceID ids[2] = {*iid_queue, *iid_config};
    const SLboolean required[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    SLObjectItf recorder = NULL;
    if ((*engine)->CreateAudioRecorder(engine, &recorder, &source, &sink, 2, ids, required) != SL_RESULT_SUCCESS) {
        (*engine_object)->Destroy(engine_object);
        return RESULT_NO_RECORDER;
    }

    int result = RESULT_NO_CONFIG;
    SLAndroidConfigurationItf config = NULL;
    if ((*recorder)->GetInterface(recorder, *iid_config, &config) == SL_RESULT_SUCCESS && config != NULL) {
        const struct SLAndroidConfigurationItf_ *table = *config;
        void **slot = (void **) &table->SetConfiguration;
        void *old = NULL;
        int patched = urinth_patch_slot(slot, (void *) hooked_set_configuration, &old);
        if (patched == URINTH_PATCH_OK) {
            original_set_configuration = (set_configuration_fn) old;
            result = 0;
        } else if (patched == URINTH_PATCH_ALREADY) {
            result = 1;
        } else {
            result = patched; // -1 .. -3
        }
    }
    (*recorder)->Destroy(recorder);
    (*engine_object)->Destroy(engine_object);
    return result;
}

JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_installOpenSLMicPreset(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    int result = install();
    LOGI("OpenSL microphone preset hook: result %d", result);
    return result;
}

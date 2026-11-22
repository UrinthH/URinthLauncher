/*
 * Record-able Lite: a tiny native shim.
 *
 * Java cannot call C functions on its own. This library gives it exactly three entry points:
 *   call()       - calls any C function with up to six integer or pointer arguments,
 *   dlopenAddr() - the address of the system's dlopen,
 *   dlsymAddr()  - the address of the system's dlsym.
 * With those the mod loads Android's own media codec library (libmediandk.so) and drives the phone's hardware H.264 encoder.
 *
 * The shim has no dependencies at all (no C library, no JNI header), so it loads in any process that can load a JNI library.
 * Build: see build.sh.
 */

typedef long long jlong;
typedef unsigned long long u64;

extern void *dlopen(const char *file, int mode) __attribute__((weak));
extern void *dlsym(void *handle, const char *name) __attribute__((weak));

typedef u64 (*call6_t)(u64, u64, u64, u64, u64, u64);

#define EXPORT __attribute__((visibility("default")))

EXPORT jlong Java_dev_urinth_recordable_nat_Shim_call(void *env, void *cls, jlong fn,
                                                      jlong a0, jlong a1, jlong a2, jlong a3, jlong a4, jlong a5)
{
    (void) env;
    (void) cls;
    if (fn == 0) {
        return 0;
    }
    return (jlong) ((call6_t) (u64) fn)((u64) a0, (u64) a1, (u64) a2, (u64) a3, (u64) a4, (u64) a5);
}

EXPORT jlong Java_dev_urinth_recordable_nat_Shim_dlopenAddr(void *env, void *cls)
{
    (void) env;
    (void) cls;
    return (jlong) (u64) &dlopen;
}

EXPORT jlong Java_dev_urinth_recordable_nat_Shim_dlsymAddr(void *env, void *cls)
{
    (void) env;
    (void) cls;
    return (jlong) (u64) &dlsym;
}

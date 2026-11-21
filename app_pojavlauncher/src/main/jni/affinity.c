//
// Created by maks on 19.06.2023.
//

#define _GNU_SOURCE // we are GNU GPLv3

#include <linux/limits.h>
#include <stdbool.h>
#include <unistd.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sched.h>
#include <string.h>
#include <errno.h>
#include <jni.h>

static _Thread_local bool big_core_affine = false;

static cpu_set_t bigcore_affinity_set;
static bool has_affinity_set;

#define FREQ_MAX 256
void bigcore_format_cpu_path(char* buffer, unsigned int cpu_core) {
    snprintf(buffer, PATH_MAX, "/sys/devices/system/cpu/cpu%i/cpufreq/cpuinfo_max_freq", cpu_core);
}

static void create_affinity_set() {
    char path_buffer[PATH_MAX];
    char freq_buffer[FREQ_MAX];
    char* discard;
    unsigned long core_freq;
    unsigned long max_freq = 0;
    unsigned int corecnt = 0;
    unsigned int big_core_id = 0;
    while(1) {
        bigcore_format_cpu_path(path_buffer, corecnt);
        int corefreqfd = open(path_buffer, O_RDONLY);
        if(corefreqfd != -1) {
            ssize_t read_count = read(corefreqfd, freq_buffer, FREQ_MAX);
            close(corefreqfd);
            freq_buffer[read_count] = 0;
            core_freq = strtoul(freq_buffer, &discard, 10);
            if(core_freq >= max_freq) {
                max_freq = core_freq;
                big_core_id = corecnt;
            }
        }else{
            break;
        }
        corecnt++;
    }
    printf("bigcore: big CPU number is %u, frequency %lu Hz\n", big_core_id, max_freq);
    CPU_ZERO(&bigcore_affinity_set);
    CPU_SET_S(big_core_id, CPU_SETSIZE, &bigcore_affinity_set);
    has_affinity_set = true;
}

void make_big_core_affine() {
    if(big_core_affine) return;
    if(!has_affinity_set) create_affinity_set();
    int result = sched_setaffinity(0, CPU_SETSIZE, &bigcore_affinity_set);
    if(result != 0) {
        printf("bigcore: setting affinity failed: %s\n", strerror(result));
    }else{
        printf("bigcore: forced current thread onto big core\n");
        big_core_affine = true;
    }
}

/**
 * Moves one thread (a kernel thread id, from /proc/self/task) onto the cores in a bit mask (bit n = core n), which is how the
 * "keep the render thread on the fast cores" switch works. Returns 0 when it worked, otherwise the error number the kernel gave.
 * Only that one thread is touched; every other thread of the game keeps running wherever the system puts it.
 */
JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_game_CorePinner_nativePinThread(__attribute((unused)) JNIEnv *env, __attribute((unused)) jclass clazz,
                                                         jint tid, jlong mask) {
    cpu_set_t set;
    CPU_ZERO(&set);
    int wanted = 0;
    for(int cpu = 0; cpu < 63 && cpu < CPU_SETSIZE; cpu++) {
        if((((unsigned long long) mask) >> cpu) & 1ULL) {
            CPU_SET(cpu, &set);
            wanted++;
        }
    }
    if(wanted == 0 || tid <= 0) return EINVAL;
    if(sched_setaffinity((pid_t) tid, sizeof(set), &set) != 0) return errno;
    return 0;
}

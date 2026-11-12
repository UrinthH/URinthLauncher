#include <jni.h>
#include <sys/types.h>
#include <stdbool.h>
#include <unistd.h>
#include <pthread.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>
#include <stdlib.h>
#include <time.h>

//
// Created by maks on 17.02.21.
//

static int pfd[2];
static pthread_t logger;
static jmethodID logger_onEventLogged;
static volatile jobject logListener = NULL;
static int latestlog_fd = -1;
// When the log was last flushed to the storage chip. Mostly the logger thread touches it; appendToLog() may race
// with it, which at worst flushes one extra time.
static struct timespec last_log_sync;

// Once per second at most. A flush after every chunk made this thread wait for the flash chip each time; when the
// game prints a lot (servers do) the pipe then fills up and the game itself waits on every line it prints. Text that
// was written is kept by the system even when the app dies, so only a power loss in the last second can lose lines.
static void sync_log_if_due(void) {
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    long long ms = (long long)(now.tv_sec - last_log_sync.tv_sec) * 1000LL
            + (now.tv_nsec - last_log_sync.tv_nsec) / 1000000LL;
    if(ms >= 1000) {
        fdatasync(latestlog_fd);
        last_log_sync = now;
    }
}

static bool recordBuffer(char* buf, ssize_t len) {
    if(strstr(buf, "Session ID is")) return false;
    if(latestlog_fd != -1) {
        write(latestlog_fd, buf, len);
        sync_log_if_due();
    }
    return true;
}

static void *logger_thread(void* param) {
    JNIEnv *env;
    jstring writeString;
    JavaVM* dvm = (JavaVM*) param;
    (*dvm)->AttachCurrentThread(dvm, &env, NULL);
    ssize_t  rsize;
    char buf[2050];
    while((rsize = read(pfd[0], buf, sizeof(buf)-1)) > 0) {
        bool shouldRecordString = recordBuffer(buf, rsize); //record with newline int latestlog
        if(buf[rsize-1]=='\n') {
            rsize=rsize-1; //truncate
        }
        buf[rsize]=0x00;
        if(shouldRecordString && logListener != NULL) {
            writeString = (*env)->NewStringUTF(env, buf); //send to app without newline
            (*env)->CallVoidMethod(env, logListener, logger_onEventLogged, writeString);
            (*env)->DeleteLocalRef(env, writeString);
        }
    }
    if(latestlog_fd != -1) fdatasync(latestlog_fd);
    (*dvm)->DetachCurrentThread(dvm);
    return NULL;
}


JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_begin(JNIEnv *env, __attribute((unused)) jclass clazz, jstring logPath) {
    if(latestlog_fd != -1) {
        int localfd = latestlog_fd;
        latestlog_fd = -1;
        close(localfd);
    }
    if(logger_onEventLogged == NULL) {
        jclass eventLogListener = (*env)->FindClass(env, "net/kdt/pojavlaunch/Logger$eventLogListener");
        logger_onEventLogged = (*env)->GetMethodID(env, eventLogListener, "onEventLogged", "(Ljava/lang/String;)V");
    }
    jclass ioeClass = (*env)->FindClass(env, "java/io/IOException");


    setvbuf(stdout, 0, _IOLBF, 0); // make stdout line-buffered
    setvbuf(stderr, 0, _IONBF, 0); // make stderr unbuffered

    /* create the pipe and redirect stdout and stderr */
    pipe(pfd);
    // A bigger pipe (the default is 64 KB) gives the logger thread more room when the storage chip is slow for a
    // moment, so the game's own println() does not have to wait for it. Pages are only used when filled. If the
    // system refuses the size, the default stays.
#ifdef F_SETPIPE_SZ
    fcntl(pfd[1], F_SETPIPE_SZ, 1024 * 1024);
#endif
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);

    /* open latestlog.txt for writing */
    const char* logFilePath = (*env)->GetStringUTFChars(env, logPath, NULL);
    latestlog_fd = open(logFilePath, O_WRONLY | O_TRUNC);
    if(latestlog_fd == -1) {
        latestlog_fd = 0;
        (*env)->ThrowNew(env, ioeClass, strerror(errno));
        return;
    }
    (*env)->ReleaseStringUTFChars(env, logPath, logFilePath);

    JavaVM* vm = NULL;
    (*env)->GetJavaVM(env, &vm);

    /* spawn the logging thread */
    int result = pthread_create(&logger, 0, logger_thread, vm);
    if(result != 0) {
        close(latestlog_fd);
        (*env)->ThrowNew(env, ioeClass, strerror(result));
    }
    pthread_detach(logger);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_Logger_appendToLog(JNIEnv *env, __attribute((unused)) jclass clazz, jstring text) {
    jsize appendStringLength = (*env)->GetStringUTFLength(env, text);
    char newChars[appendStringLength+2];
    (*env)->GetStringUTFRegion(env, text, 0, (*env)->GetStringLength(env, text), newChars);
    newChars[appendStringLength] = '\n';
    newChars[appendStringLength+1] = 0;
    if(recordBuffer(newChars, appendStringLength+1) && logListener != NULL) {
        (*env)->CallVoidMethod(env, logListener, logger_onEventLogged, text);
    }
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_Logger_setLogListener(JNIEnv *env, __attribute((unused)) jclass clazz, jobject log_listener) {
    jobject logListenerLocal = logListener;
    if(log_listener == NULL) {
        logListener = NULL;
    }else{
        logListener = (*env)->NewGlobalRef(env, log_listener);
    }
    if(logListenerLocal != NULL) (*env)->DeleteGlobalRef(env, logListenerLocal);
}
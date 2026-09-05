#define _POSIX_C_SOURCE 200809L

#include <jni.h>

#include <errno.h>
#include <fcntl.h>
#include <pty.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

static void throw_io_exception(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/io/IOException");
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
    }
}

static char *copy_jstring(JNIEnv *env, jstring value) {
    if (value == NULL) {
        return NULL;
    }

    const char *utf = (*env)->GetStringUTFChars(env, value, NULL);
    if (utf == NULL) {
        return NULL;
    }

    char *copy = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, value, utf);
    return copy;
}

static void free_string_vector(char **items, size_t count) {
    if (items == NULL) {
        return;
    }

    for (size_t i = 0; i < count; ++i) {
        free(items[i]);
    }

    free(items);
}

static char **copy_string_array(
    JNIEnv *env,
    jobjectArray values,
    size_t extra_prefix,
    const char *prefix
) {
    const jsize length = values == NULL
        ? 0
        : (*env)->GetArrayLength(env, values);

    const size_t total = (size_t) length + extra_prefix;
    char **result = calloc(total + 1U, sizeof(char *));
    if (result == NULL) {
        return NULL;
    }

    size_t index = 0U;
    if (extra_prefix > 0U) {
        result[index] = strdup(prefix == NULL ? "" : prefix);
        if (result[index] == NULL) {
            free_string_vector(result, total);
            return NULL;
        }
        ++index;
    }

    for (jsize i = 0; i < length; ++i) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, values, i);
        if (item == NULL) {
            free_string_vector(result, total);
            return NULL;
        }

        result[index] = copy_jstring(env, item);
        (*env)->DeleteLocalRef(env, item);

        if (result[index] == NULL) {
            free_string_vector(result, total);
            return NULL;
        }

        ++index;
    }

    result[total] = NULL;
    return result;
}

static int set_close_on_exec(int fd) {
    const int flags = fcntl(fd, F_GETFD);
    if (flags == -1) {
        return -1;
    }

    return fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
}

static void terminate_and_reap_child(pid_t pid) {
    if (pid <= 0) {
        return;
    }

    /*
     * nativeSpawn failure paths have no waiter coroutine because the
     * process was never returned to Kotlin. Kill and reap it here so
     * the AppForge process cannot accumulate zombie PTY children.
     */
    (void) kill(-pid, SIGKILL);
    (void) kill(pid, SIGKILL);

    int status = 0;
    pid_t result;

    do {
        result = waitpid(pid, &status, 0);
    } while (result == -1 && errno == EINTR);
}

JNIEXPORT jintArray JNICALL
Java_com_appforge_studio_terminal_AppForgePtyBridge_nativeSpawn(
    JNIEnv *env,
    jobject thiz,
    jstring executable,
    jobjectArray arguments,
    jobjectArray environment,
    jstring working_directory,
    jint rows,
    jint columns
) {
    (void) thiz;

    if (rows < 2 || rows > 1000 || columns < 10 || columns > 2000) {
        throw_io_exception(env, "PTY boyutu geçersiz.");
        return NULL;
    }

    if (arguments == NULL || environment == NULL) {
        throw_io_exception(env, "PTY argümanları eksik.");
        return NULL;
    }

    char *exec_path = copy_jstring(env, executable);
    char *cwd = copy_jstring(env, working_directory);
    if (exec_path == NULL || cwd == NULL || exec_path[0] == '\0' || cwd[0] == '\0') {
        free(exec_path);
        free(cwd);
        throw_io_exception(env, "PTY çalıştırma yolu hazırlanamadı.");
        return NULL;
    }

    char **argv = copy_string_array(env, arguments, 1U, exec_path);
    char **envp = copy_string_array(env, environment, 0U, NULL);
    if (argv == NULL || envp == NULL) {
        free(exec_path);
        free(cwd);
        if (argv != NULL) {
            size_t argc = 0U;
            while (argv[argc] != NULL) {
                ++argc;
            }
            free_string_vector(argv, argc);
        }
        if (envp != NULL) {
            size_t envc = 0U;
            while (envp[envc] != NULL) {
                ++envc;
            }
            free_string_vector(envp, envc);
        }
        throw_io_exception(env, "PTY argümanları hazırlanamadı.");
        return NULL;
    }

    const size_t argc = (size_t) (*env)->GetArrayLength(env, arguments) + 1U;
    const size_t envc = (size_t) (*env)->GetArrayLength(env, environment);

    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short) rows;
    window_size.ws_col = (unsigned short) columns;

    int master_fd = -1;
    const pid_t pid = forkpty(
        &master_fd,
        NULL,
        NULL,
        &window_size
    );

    if (pid == -1) {
        const int saved_errno = errno;
        free(exec_path);
        free(cwd);
        free_string_vector(argv, argc);
        free_string_vector(envp, envc);
        errno = saved_errno;
        throw_io_exception(env, "PTY oluşturulamadı.");
        return NULL;
    }

    if (pid == 0) {
        if (chdir(cwd) != 0) {
            _exit(126);
        }

        execve(exec_path, argv, envp);
        _exit(errno == ENOENT ? 127 : 126);
    }

    free(exec_path);
    free(cwd);
    free_string_vector(argv, argc);
    free_string_vector(envp, envc);

    const int input_fd = dup(master_fd);
    const int output_fd = dup(master_fd);
    const int control_fd = dup(master_fd);
    close(master_fd);

    if (input_fd == -1 || output_fd == -1 || control_fd == -1) {
        if (input_fd != -1) {
            close(input_fd);
        }
        if (output_fd != -1) {
            close(output_fd);
        }
        if (control_fd != -1) {
            close(control_fd);
        }
        terminate_and_reap_child(pid);
        throw_io_exception(env, "PTY dosya tanımlayıcıları hazırlanamadı.");
        return NULL;
    }

    if (
        set_close_on_exec(input_fd) != 0 ||
        set_close_on_exec(output_fd) != 0 ||
        set_close_on_exec(control_fd) != 0
    ) {
        close(input_fd);
        close(output_fd);
        close(control_fd);
        terminate_and_reap_child(pid);
        throw_io_exception(env, "PTY güvenlik bayrakları ayarlanamadı.");
        return NULL;
    }

    jint values[4];
    values[0] = (jint) pid;
    values[1] = (jint) input_fd;
    values[2] = (jint) output_fd;
    values[3] = (jint) control_fd;

    jintArray result = (*env)->NewIntArray(env, 4);
    if (result == NULL) {
        close(input_fd);
        close(output_fd);
        close(control_fd);
        terminate_and_reap_child(pid);
        return NULL;
    }

    (*env)->SetIntArrayRegion(env, result, 0, 4, values);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_appforge_studio_terminal_AppForgePtyBridge_nativeResize(
    JNIEnv *env,
    jobject thiz,
    jint control_fd,
    jint rows,
    jint columns
) {
    (void) env;
    (void) thiz;

    if (control_fd < 0 || rows < 2 || rows > 1000 || columns < 10 || columns > 2000) {
        return JNI_FALSE;
    }

    struct winsize window_size;
    memset(&window_size, 0, sizeof(window_size));
    window_size.ws_row = (unsigned short) rows;
    window_size.ws_col = (unsigned short) columns;

    return ioctl(control_fd, TIOCSWINSZ, &window_size) == 0
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_appforge_studio_terminal_AppForgePtyBridge_nativeWait(
    JNIEnv *env,
    jobject thiz,
    jint process_id
) {
    (void) env;
    (void) thiz;

    if (process_id <= 0) {
        return 255;
    }

    int status = 0;
    pid_t result;
    do {
        result = waitpid((pid_t) process_id, &status, 0);
    } while (result == -1 && errno == EINTR);

    if (result == -1) {
        return 255;
    }

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }

    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }

    return 255;
}

JNIEXPORT void JNICALL
Java_com_appforge_studio_terminal_AppForgePtyBridge_nativeTerminate(
    JNIEnv *env,
    jobject thiz,
    jint process_id
) {
    (void) env;
    (void) thiz;

    if (process_id <= 0) {
        return;
    }

    if (kill(-(pid_t) process_id, SIGHUP) != 0 && errno != ESRCH) {
        return;
    }

    (void) kill(-(pid_t) process_id, SIGTERM);
    (void) kill((pid_t) process_id, SIGTERM);
}

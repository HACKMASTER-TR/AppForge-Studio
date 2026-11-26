#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <linux/fs.h>

JNIEXPORT jboolean JNICALL Java_com_appforge_studio_build_RuntimeFileClone_cloneFile(JNIEnv *env, jobject self, jstring source, jstring target) {
    (void)self;
    const char *src = (*env)->GetStringUTFChars(env, source, 0);
    if (!src) return JNI_FALSE;
    const char *dst = (*env)->GetStringUTFChars(env, target, 0);
    if (!dst) { (*env)->ReleaseStringUTFChars(env, source, src); return JNI_FALSE; }
    int in = open(src, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    int out = -1, ok = 0;
    struct stat st;
    if (in >= 0 && fstat(in, &st) == 0 && S_ISREG(st.st_mode)) {
        out = open(dst, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC | O_NOFOLLOW, 0600);
        if (out >= 0) ok = ioctl(out, FICLONE, in) == 0 && fchmod(out, st.st_mode & 0777) == 0 && fsync(out) == 0;
    }
    if (out >= 0) { close(out); if (!ok) unlink(dst); }
    if (in >= 0) close(in);
    (*env)->ReleaseStringUTFChars(env, target, dst);
    (*env)->ReleaseStringUTFChars(env, source, src);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jlong JNICALL Java_com_appforge_studio_build_RuntimeFileClone_allocated(JNIEnv *env, jobject self, jstring path) {
    (void)self;
    const char *p = (*env)->GetStringUTFChars(env, path, 0);
    if (!p) return -1;
    struct stat st;
    jlong result = lstat(p, &st) == 0 ? (jlong)st.st_blocks * 512 : -1;
    (*env)->ReleaseStringUTFChars(env, path, p);
    return result;
}

JNIEXPORT jboolean JNICALL Java_com_appforge_studio_build_RuntimeFileClone_syncPath(JNIEnv *env, jobject self, jstring path, jboolean directory) {
    (void)self;
    const char *p = (*env)->GetStringUTFChars(env, path, 0);
    if (!p) return JNI_FALSE;
    int fd = open(p, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | (directory ? O_DIRECTORY : 0));
    int ok = fd >= 0 && fsync(fd) == 0;
    if (fd >= 0) close(fd);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return ok ? JNI_TRUE : JNI_FALSE;
}

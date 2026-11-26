package com.appforge.studio.build

import java.io.File
import java.nio.file.Files

/** Validate the caller's original path before resolving a private recovery namespace.
 * Missing leaves are legal during a rename; existing symlink ancestors never are.
 * This is an app-private namespace check, not a hostile concurrent-filesystem sandbox. */
internal object RecoveryPathAnchor {
    /** Only Android's system-owned legacy app-data alias is recognized. Never
     * canonicalize arbitrary workspace links: those remain rejected by check(). */
    fun androidPlatformPath(file:File):File =
        knownPlatformAlias(file,File("/data/data"),File("/data/user/0"))

    /** Narrow known-system-alias normalization. Arbitrary symlinks remain rejected. */
    internal fun knownPlatformAlias(file:File,legacyRoot:File,userZeroRoot:File):File {
        val path=file.absoluteFile.toPath()
        require(path.normalize()==path) { "Recovery yolu normalize edilmemiş" }
        val legacy=legacyRoot.absoluteFile.toPath()
        val userZero=userZeroRoot.absoluteFile.toPath()
        for((alias,expected) in listOf(legacy to userZero,userZero to legacy)) {
            if(!path.startsWith(alias) || !Files.isSymbolicLink(alias))continue
            val resolved=alias.parent.resolve(Files.readSymbolicLink(alias)).normalize()
            require(resolved==expected) { "Android veri takma yolu doğrulanamadı" }
            val actual=resolved.resolve(alias.relativize(path)).toFile()
            check(actual) // Includes target ancestors; alias cycles/secondary links are rejected.
            return actual
        }
        return file
    }
    fun check(file:File) {
        val absolute=file.absoluteFile
        require(absolute.toPath().normalize()==absolute.toPath()) { "Recovery yolu normalize edilmemiş" }
        var current:File?=absolute
        while(current!=null) {
            require(!Files.isSymbolicLink(current.toPath())) { "Recovery yolu symlink içeremez" }
            current=current.parentFile
        }
        require(absolute==file.canonicalFile) { "Recovery namespace doğrulanamadı" }
    }
    fun siblings(root:File,vararg controls:File) {
        check(root)
        val parent=requireNotNull(root.absoluteFile.parentFile)
        check(parent)
        controls.forEach { check(it);require(it.absoluteFile.parentFile==parent) }
    }
}

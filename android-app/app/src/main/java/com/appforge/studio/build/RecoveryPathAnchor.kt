package com.appforge.studio.build

import java.io.File
import java.nio.file.Files

/** Validate the caller's original path before resolving a private recovery namespace.
 * Missing leaves are legal during a rename; existing symlink ancestors never are.
 * This is an app-private namespace check, not a hostile concurrent-filesystem sandbox. */
internal object RecoveryPathAnchor {
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

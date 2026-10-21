package com.appforge.studio.build

/** Cargo may populate only disposable caches. Source code cannot alter the
 * trusted compiler, stdlib, verifier or rootfs configuration and still publish. */
internal object RustPreparationIntegrity {
    fun requireUnchangedRuntime(before:Map<String,RuntimeGenerationStore.Entry>,after:Map<String,RuntimeGenerationStore.Entry>) {
        val mutable=listOf("root/.cargo","root/.cache","tmp","run")
        val changed=(before.keys+after.keys).filter{key->
            mutable.none{key==it || key.startsWith("$it/")} && before[key]!=after[key]
        }
        require(changed.isEmpty()){"Rust proje kodu korunan runtime dosyalarını değiştirdi; çıktı kabul edilmedi."}
    }
}

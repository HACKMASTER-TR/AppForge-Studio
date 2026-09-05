package com.appforge.studio.terminal

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

internal enum class LinuxDistribution(
    val id: String,
    val title: String
) {
    DEBIAN("debian", "Debian"),
    UBUNTU("ubuntu", "Ubuntu")
}

internal enum class LinuxArchitecture(
    val id: String,
    val distroArchitecture: String,
    val androidAbis: Set<String>
) {
    ARM64(
        id = "arm64",
        distroArchitecture = "arm64",
        androidAbis = setOf("arm64-v8a")
    ),
    ARM32(
        id = "arm",
        distroArchitecture = "armhf",
        androidAbis = setOf("armeabi-v7a", "armeabi")
    ),
    X86_64(
        id = "x86_64",
        distroArchitecture = "amd64",
        androidAbis = setOf("x86_64")
    ),
    X86(
        id = "x86",
        distroArchitecture = "i386",
        androidAbis = setOf("x86")
    );

    companion object {
        fun fromAndroidAbis(
            abis: List<String>
        ): LinuxArchitecture? =
            abis
                .asSequence()
                .map { it.lowercase() }
                .mapNotNull { abi ->
                    entries.firstOrNull {
                        abi in it.androidAbis
                    }
                }
                .firstOrNull()
    }
}

internal enum class LinuxRuntimeState {
    UNSUPPORTED_ABI,
    ENGINE_NOT_BUNDLED,
    ROOTFS_NOT_INSTALLED,
    ROOTFS_UNTRUSTED,
    READY
}

internal data class LinuxRuntimeStatus(
    val distribution: LinuxDistribution,
    val architecture: LinuxArchitecture?,
    val state: LinuxRuntimeState,
    val engineBundled: Boolean,
    val rootfsInstalled: Boolean,
    val rootfsTrusted: Boolean,
    val detail: String
) {
    val ready: Boolean
        get() =
            state == LinuxRuntimeState.READY &&
                engineBundled &&
                rootfsInstalled &&
                rootfsTrusted
}

internal data class LinuxRuntimeLayout(
    val baseDirectory: File,
    val distribution: LinuxDistribution,
    val architecture: LinuxArchitecture
) {
    val runtimeDirectory: File
        get() =
            File(
                File(
                    baseDirectory,
                    distribution.id
                ),
                architecture.id
            )

    val rootfsDirectory: File
        get() = File(runtimeDirectory, "rootfs")

    val stagingDirectory: File
        get() = File(runtimeDirectory, "staging")

    val metadataFile: File
        get() = File(runtimeDirectory, ".appforge-rootfs.properties")

    val restoreDirectory: File
        get() = File(runtimeDirectory, "restore-points")
}

internal data class LinuxRootfsManifest(
    val distribution: LinuxDistribution,
    val architecture: LinuxArchitecture,
    val release: String,
    val archiveUrl: String,
    val archiveFileName: String,
    val archiveSha256: String,
    val maxArchiveBytes: Long
) {
    val sourceUri: URI =
        URI(archiveUrl)

    init {
        require(release.matches(RELEASE_PATTERN)) {
            "Linux release etiketi geçersiz."
        }

        require(
            ARCHIVE_NAME_PATTERN.matches(
                archiveFileName
            )
        ) {
            "Rootfs arşiv adı geçersiz."
        }

        require(
            sourceUri.scheme.equals(
                "https",
                ignoreCase = true
            ) &&
                sourceUri.host
                    ?.lowercase() in
                    TRUSTED_ROOTFS_HOSTS &&
                sourceUri.userInfo == null &&
                sourceUri.query == null &&
                sourceUri.fragment == null
        ) {
            "Rootfs yalnız güvenilir HTTPS kaynağından alınabilir."
        }

        require(
            sourceUri.path
                ?.endsWith(
                    "/$archiveFileName"
                ) == true
        ) {
            "Rootfs URL ve arşiv adı eşleşmiyor."
        }

        require(
            LinuxRuntimeIntegrity.isValidSha256(
                archiveSha256
            )
        ) {
            "Rootfs SHA-256 değeri geçersiz."
        }

        require(
            maxArchiveBytes in
                MIN_ARCHIVE_BYTES..MAX_ARCHIVE_BYTES
        ) {
            "Rootfs boyut sınırı geçersiz."
        }
    }

    companion object {
        private val RELEASE_PATTERN =
            Regex("^[A-Za-z0-9._-]{1,64}$")

        private val ARCHIVE_NAME_PATTERN =
            Regex("^[A-Za-z0-9._-]{1,128}\\.tar\\.gz$")

        private const val MIN_ARCHIVE_BYTES =
            1L * 1024L * 1024L

        private const val MAX_ARCHIVE_BYTES =
            512L * 1024L * 1024L

        val TRUSTED_ROOTFS_HOSTS =
            setOf(
                "cdimage.ubuntu.com"
            )
    }
}

/**
 * Rootfs artifacts are pinned to exact vendor-published SHA-256 values. A new
 * distro release must be reviewed and added here before AppForge can install
 * it. Debian remains selectable, but intentionally has no installable image
 * until an equally pinned official rootfs artifact is reviewed.
 */
internal object LinuxRuntimeManifestRegistry {
    private const val UBUNTU_RELEASE =
        "24.04.4"

    private const val UBUNTU_BASE_URL =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release"

    private const val ROOTFS_LIMIT =
        96L * 1024L * 1024L

    private val manifests =
        listOf(
            ubuntu(
                LinuxArchitecture.ARM64,
                "ubuntu-base-24.04.4-base-arm64.tar.gz",
                "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
            ),
            ubuntu(
                LinuxArchitecture.ARM32,
                "ubuntu-base-24.04.4-base-armhf.tar.gz",
                "991520b47f6586f38a78505cf016e300b6191bb8ff86a0723481ec23a37ab7f4"
            ),
            ubuntu(
                LinuxArchitecture.X86_64,
                "ubuntu-base-24.04.4-base-amd64.tar.gz",
                "c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58"
            )
        )

    fun find(
        distribution: LinuxDistribution,
        architecture: LinuxArchitecture
    ): LinuxRootfsManifest? =
        manifests.firstOrNull {
            it.distribution == distribution &&
                it.architecture == architecture
        }

    fun available(
        architecture: LinuxArchitecture
    ): List<LinuxRootfsManifest> =
        manifests.filter {
            it.architecture == architecture
        }

    private fun ubuntu(
        architecture: LinuxArchitecture,
        fileName: String,
        sha256: String
    ) =
        LinuxRootfsManifest(
            distribution = LinuxDistribution.UBUNTU,
            architecture = architecture,
            release = UBUNTU_RELEASE,
            archiveUrl = "$UBUNTU_BASE_URL/$fileName",
            archiveFileName = fileName,
            archiveSha256 = sha256,
            maxArchiveBytes = ROOTFS_LIMIT
        )
}

internal object LinuxRuntimeIntegrity {
    private val SHA256_PATTERN =
        Regex("^[0-9a-fA-F]{64}$")

    fun isValidSha256(value: String): Boolean =
        SHA256_PATTERN.matches(
            value.trim()
        )

    fun sha256(file: File): String {
        require(file.isFile && file.canRead()) {
            "Doğrulanacak Linux arşivi okunamıyor."
        }

        val digest =
            MessageDigest.getInstance("SHA-256")

        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)

            while (true) {
                val count = input.read(buffer)

                if (count < 0) {
                    break
                }

                if (count > 0) {
                    digest.update(
                        buffer,
                        0,
                        count
                    )
                }
            }
        }

        return digest
            .digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }

    fun matches(
        file: File,
        expectedSha256: String
    ): Boolean {
        if (!isValidSha256(expectedSha256)) {
            return false
        }

        val actual =
            runCatching {
                sha256(file)
            }.getOrNull()
                ?: return false

        return MessageDigest.isEqual(
            actual.lowercase().toByteArray(Charsets.US_ASCII),
            expectedSha256
                .trim()
                .lowercase()
                .toByteArray(Charsets.US_ASCII)
        )
    }
}

internal data class LinuxRootfsMetadata(
    val distribution: LinuxDistribution,
    val architecture: LinuxArchitecture,
    val release: String,
    val archiveSha256: String
)

internal object LinuxRootfsMetadataCodec {
    fun write(
        file: File,
        metadata: LinuxRootfsMetadata
    ) {
        require(
            LinuxRuntimeIntegrity.isValidSha256(
                metadata.archiveSha256
            )
        ) {
            "Rootfs metadata SHA-256 değeri geçersiz."
        }

        file.parentFile?.mkdirs()

        val properties =
            Properties().apply {
                setProperty(
                    "distribution",
                    metadata.distribution.id
                )
                setProperty(
                    "architecture",
                    metadata.architecture.id
                )
                setProperty(
                    "release",
                    metadata.release
                )
                setProperty(
                    "archiveSha256",
                    metadata.archiveSha256.lowercase()
                )
            }

        file.outputStream().buffered().use {
            properties.store(
                it,
                "AppForge verified rootfs"
            )
        }
    }

    fun read(file: File): LinuxRootfsMetadata? {
        if (!file.isFile || !file.canRead()) {
            return null
        }

        val properties = Properties()

        return runCatching {
            file.inputStream().buffered().use {
                properties.load(it)
            }

            val distributionId =
                properties
                    .getProperty("distribution")
                    ?.trim()
                    .orEmpty()

            val architectureId =
                properties
                    .getProperty("architecture")
                    ?.trim()
                    .orEmpty()

            val release =
                properties
                    .getProperty("release")
                    ?.trim()
                    .orEmpty()

            val archiveSha256 =
                properties
                    .getProperty("archiveSha256")
                    ?.trim()
                    .orEmpty()

            val distribution =
                LinuxDistribution.entries
                    .firstOrNull {
                        it.id == distributionId
                    }
                    ?: return null

            val architecture =
                LinuxArchitecture.entries
                    .firstOrNull {
                        it.id == architectureId
                    }
                    ?: return null

            if (
                release.isBlank() ||
                !LinuxRuntimeIntegrity.isValidSha256(
                    archiveSha256
                )
            ) {
                return null
            }

            LinuxRootfsMetadata(
                distribution = distribution,
                architecture = architecture,
                release = release,
                archiveSha256 = archiveSha256.lowercase()
            )
        }.getOrNull()
    }
}

internal enum class LinuxToolchainId {
    BASE,
    PYTHON,
    NODE,
    JAVA,
    PHP,
    GO,
    RUST,
    C_CPP,
    ANDROID
}

internal data class LinuxToolchainSpec(
    val id: LinuxToolchainId,
    val title: String,
    val description: String,
    val packages: List<String>
)

internal object LinuxToolchainCatalog {
    val specs: List<LinuxToolchainSpec> =
        listOf(
            spec(
                LinuxToolchainId.BASE,
                "Temel araçlar",
                "Git, sertifikalar, SSH istemcisi ve genel build yardımcıları.",
                "ca-certificates",
                "git",
                "openssh-client",
                "curl",
                "wget",
                "rsync",
                "zip",
                "unzip",
                "xz-utils",
                "bzip2",
                "gzip",
                "tar",
                "jq",
                "sqlite3",
                "openssl",
                "bash",
                "zsh",
                "less",
                "nano",
                "file",
                "procps"
            ),
            spec(
                LinuxToolchainId.PYTHON,
                "Python",
                "Python 3, pip ve sanal ortam desteği.",
                "python3",
                "python3-pip",
                "python3-venv",
                "python-is-python3",
                "pipx"
            ),
            spec(
                LinuxToolchainId.NODE,
                "Node.js",
                "Node.js ve npm.",
                "nodejs",
                "npm"
            ),
            spec(
                LinuxToolchainId.JAVA,
                "Java",
                "Dağıtımın varsayılan JDK ve Maven araç zinciri.",
                "default-jdk",
                "maven",
                "gradle"
            ),
            spec(
                LinuxToolchainId.PHP,
                "PHP",
                "PHP CLI ve Composer.",
                "php-cli",
                "composer"
            ),
            spec(
                LinuxToolchainId.GO,
                "Go",
                "Go derleyicisi ve araçları.",
                "golang-go"
            ),
            spec(
                LinuxToolchainId.RUST,
                "Rust",
                "Rust derleyicisi ve Cargo.",
                "rustc",
                "cargo"
            ),
            spec(
                LinuxToolchainId.C_CPP,
                "C / C++",
                "GCC/G++, make, CMake ve pkg-config.",
                "build-essential",
                "clang",
                "lld",
                "cmake",
                "ninja-build",
                "pkg-config",
                "gdb"
            ),
            spec(
                LinuxToolchainId.ANDROID,
                "Android CLI",
                "ADB ve Fastboot istemci araçları. USB erişimi Android izinlerine bağlıdır.",
                "adb",
                "fastboot"
            )
        )

    fun spec(id: LinuxToolchainId): LinuxToolchainSpec =
        specs.first {
            it.id == id
        }

    val developmentProfileIds:
        List<LinuxToolchainId> =
        listOf(
            LinuxToolchainId.BASE,
            LinuxToolchainId.PYTHON,
            LinuxToolchainId.NODE,
            LinuxToolchainId.JAVA,
            LinuxToolchainId.C_CPP,
            LinuxToolchainId.ANDROID
        )

    fun developmentProfileCommand(): String =
        installCommand(
            developmentProfileIds
        )

    /*
     * Android adb/fastboot are useful but must not prevent the core
     * workstation from recovering. This profile contains everything needed
     * for AppForge source development, tests and Git work.
     */
    val standaloneRecoveryIds:
        List<LinuxToolchainId> =
        listOf(
            LinuxToolchainId.BASE,
            LinuxToolchainId.PYTHON,
            LinuxToolchainId.NODE,
            LinuxToolchainId.JAVA,
            LinuxToolchainId.C_CPP
        )

    fun standaloneRecoveryCommand(): String =
        installCommand(
            standaloneRecoveryIds
        )

    fun packagesFor(
        ids: Collection<LinuxToolchainId>
    ): List<String> =
        ids
            .flatMap {
                spec(it).packages
            }
            .distinct()
            .also { packages ->
                require(
                    packages.all {
                        PACKAGE_PATTERN.matches(it)
                    }
                ) {
                    "Linux paket kataloğu geçersiz paket adı içeriyor."
                }
            }

    fun installCommand(
        ids: Collection<LinuxToolchainId>
    ): String {
        val packages =
            packagesFor(ids)

        require(packages.isNotEmpty()) {
            "En az bir Linux araç zinciri seçilmeli."
        }

        return buildString {
            append("apt-get update")
            append(" && ")
            append("DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ")
            append(packages.joinToString(" "))
        }
    }

    private fun spec(
        id: LinuxToolchainId,
        title: String,
        description: String,
        vararg packages: String
    ) =
        LinuxToolchainSpec(
            id = id,
            title = title,
            description = description,
            packages = packages.toList()
        )

    private val PACKAGE_PATTERN =
        Regex("^[a-z0-9][a-z0-9+.-]{0,63}$")
}

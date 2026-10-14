# Fast Web 535 V12 baseline / V14 external source candidate

V12 baseline: **PARTIAL_FAIL_CLOSED**, unchanged in the original phone worktree.
V14: candidate code prepared outside the worktree after local Codex sandbox could
not initialize (`cannot establish app-server socket mount isolation`). The source
candidate has **not** been applied to the actual feature worktree. Never report
V14 production PASS based on these external JVM checks.

## Authoritative binary input

- GitHub Actions run: `38181578106`, artifact `11718620864`, HEAD
  `d98fc9b3e56f3588ace6581d02d9171b46a0f171`.
- CI APK SHA-256: `ea1ccdacb8f5352f9c223dedec3da7a7d12fa2d89885f834eb52afe657319c9f`.
- Embedded AAB template SHA-256:
  `17ef1e5eb5d5a54edd03b62afe9acc61d34b7380cc7f0b512b130b593d94efad`.
- `base/manifest/AndroidManifest.xml`: 1,988 bytes, SHA-256
  `42ce28b60c895c315870ce6fb3de01c176fbbf29b805efc2e09e7b363aea2256`.
- 17 ZIP entries and Activity class `com.appforge.htmlstub.TinyHtmlActivity`.

Android AAPT2 Resources.proto schema: `XmlNode.element=1`, `XmlElement.name=3`,
`XmlElement.attribute=4`, `XmlElement.child=5`, `XmlAttribute.namespace_uri=1`,
`XmlAttribute.name=2`, `XmlAttribute.value=3`, `XmlAttribute.compiled_item=6`,
`Item.prim=7`, `Primitive.int_decimal_value=6`.

The proposed manifest editor is bound to the **exact SHA of this manifest**,
not just a string marker. On known bytes, it changes the package, label,
version name and version code, including compiled decimal metadata. It retains
SDK 26/37 and the existing fully qualified Activity class. Unknown other wire
fields remain byte-identical; unexpected compiled identity shapes are rejected.

## Candidate packager and validation

The proposed `FastHtmlAabUnsignedPackager` still requires LOCAL static sources,
explicit AAB/BOTH and the pre-existing APK permissions/source policy. It uses
only the exact embedded template SHA-256, adds `base/assets/site/**` and the
LOCAL config, writes an internal unsigned `.aab` in an existing workspace with
a collision-safe temporary name and checks the resulting ZIP central directory,
CRC-decoded entry bytes and exact additions. The caller remains **uncalled**;
no signing or user-visible routing is enabled. The existing APK packager,
engine, Gradle build files and 534 installed application remain unchanged.

External isolated Kotlin/JVM build with stub Android Context/JSONObject, but **real**
`ProjectDraft`, `FastHtmlApkPolicy`, `DeviceBuildCapabilities` and the exact
`FastHtmlApkPackager.readHtmlProject` implementation extracted unchanged:
source compilation succeeded and returned one 681,220-byte unsigned AAB candidate
from the verified CI shell. Independent Python protobuf decoding checked package,
label, versionCode string and compiled primitive, versionName, SDK and Activity;
ZIP CRC and the exact HTML/CSS/JavaScript bytes were checked, with all other
original template entries unchanged. This is not an Android compilation.

The manifest JUnit source now embeds an SHA-256-pinned copy of the 1,988-byte
CI protobuf XML manifest; acceptance is **not skipped** when no external fixture
property exists. Seven test methods completed using JVM JUnit-equivalent stubs.
Those stubs are not repository Android/JUnit evidence; actual Gradle must run
the repository tests. This embeds only the small provenance-verified manifest,
not the unsigned APK/AAB or any signing key.

**NOT_RUN / NOT_VERIFIED:** repository Android/JUnit, bundletool validate,
bundletool build-apks, signed AAB, physical-device acceptance, CI or Google Play.
This candidate must remain external and unapproved until those gates succeed.
No commit, push, main merge, Play deployment or APK installation is authorized.

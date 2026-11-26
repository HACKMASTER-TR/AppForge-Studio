package com.appforge.studio.build

import com.appforge.studio.security.V9ReleaseSignatureVerifier
import org.json.JSONObject

/**
 * Read-only candidate gate; does NOT alter AppForge download URLs or enable production.
 * Call verify() before trusting any candidate manifest, object or release sequence.
 * A persisted local sequence alone is not an independent anti-rollback authority.
 */
internal object V9ReleaseCandidatePolicy {
    const val PRODUCTION_DISTRIBUTION_ENABLED = false
    private const val SOURCE_COMMIT = "721cfcbaf121631e8b531d8361b6ec7a079aed3f"
    private const val EXPECTED_RELEASE_ID = "appforge-v9-20261018-001"
    private const val EXPECTED_SEQ = 2026101801L
    private const val EXPECTED_SNAPSHOT = "20261018T000000Z"
    private const val EXPECTED_BUCKET = "appforge-packages-staging"
    data class ObjectRecord(val sha256:String, val bytes:Long, val kind:String)
    data class VerifiedCandidate(val sequence:Long, val objects:Map<String,ObjectRecord>)

    fun verify(manifest:ByteArray, signature:ByteArray, persistedFloor:Long):VerifiedCandidate {
        // Verify the original bytes before decoding JSON or reading its contents.
        V9ReleaseSignatureVerifier.verifyPinned(manifest,signature)
        val text=manifest.toString(Charsets.UTF_8)
        require(text.toByteArray(Charsets.UTF_8).contentEquals(manifest)) { "V9 manifest must be strict UTF-8" }
        val json=JSONObject(text)
        require(json.getString("schema")=="appforge.release.v1")
        require(json.getString("channel")=="candidate")
        require(json.getString("release_id")==EXPECTED_RELEASE_ID)
        require(json.getString("snapshot")==EXPECTED_SNAPSHOT)
        require(json.getString("origin_repo_commit")==SOURCE_COMMIT)
        require(json.getString("catalog_sha256")==AptModuleLocks.CATALOG)
        require(json.getString("profile_sha256")==AptModuleLocks.PROFILE)
        require(json.getString("bucket")==EXPECTED_BUCKET)
        require(json.get("sequence") is Number)
        val sequence=json.getLong("sequence")
        require(sequence==EXPECTED_SEQ)
        V9ReleaseSignatureVerifier.requireNoRollback(persistedFloor,sequence)
        require(!json.getBoolean("metadata_expired")) { "Expired Ubuntu metadata cannot be installed" }
        require(json.getInt("package_count")==214 && json.getInt("total_objects")==218)
        val remote=json.getJSONObject("remote_verified")
        require(remote.getInt("objects")==218 && remote.getBoolean("sha256_verified"))
        val releases=json.getJSONArray("ubuntu_inrelease")
        require(releases.length()==4)
        val expectedInRelease=mapOf(
            "noble" to "cdb2f31d809f589719a53c6ad15f255b27569c4059542ada282aaa21b8e164b0",
            "noble-updates" to "f8db4261891aad2963b51de65b6f2a7f52250df02109b8a16fcfe5692d2187c3",
            "noble-security" to "f444994ac2ed9f6a64365d5baee6f9540f2cad3320d7f574b0efd4c0d514c32c",
            "noble-backports" to "9bb3c4bace14bf88014e5a66c8d431e65da66fd055dc7fdb45423305cf132cce"
        )
        val seenReleases=mutableSetOf<String>()
        for(i in 0 until releases.length()) {
            val item=releases.getJSONObject(i)
            val suite=item.getString("suite")
            require(suite in expectedInRelease && seenReleases.add(suite))
            require(item.getString("sha256")==expectedInRelease[suite])
            require(item.getString("signer")=="F6ECB3762474EDA9D21B7022871920D1991BC93C")
            require(!item.optBoolean("expired",true))
        }
        require(seenReleases==expectedInRelease.keys)
        val objects=json.getJSONArray("objects")
        require(objects.length()==218)
        val verified=linkedMapOf<String,ObjectRecord>()
        var debCount=0
        var inReleaseCount=0
        for(i in 0 until objects.length()) {
            val item=objects.getJSONObject(i)
            val sha=item.getString("sha256")
            val kind=item.getString("kind")
            val size=item.getLong("size")
            val key=item.getString("key")
            require(sha.matches(Regex("[0-9a-f]{64}")) && size in 1L..300_000_000L)
            require(key=="objects/sha256/${sha.substring(0,2)}/$sha")
            require(kind=="deb" || kind=="InRelease")
            require(verified.put(key,ObjectRecord(sha,size,kind))==null) { "Duplicate V9 object" }
            if(kind=="deb") debCount++ else {
                inReleaseCount++
                require(sha in expectedInRelease.values)
            }
        }
        require(debCount==214 && inReleaseCount==4)
        return VerifiedCandidate(sequence,verified)
    }

    fun requireProductionDisabled() {
        check(!PRODUCTION_DISTRIBUTION_ENABLED) { "V9 production release requires separate authorization and acceptance" }
    }
}

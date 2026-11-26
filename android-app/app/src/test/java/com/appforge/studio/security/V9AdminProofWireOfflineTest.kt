package com.appforge.studio.security

/** Pure JVM source acceptance. No Android SDK, private keys, Google tokens or endpoints. */
private val S = V9AdminProofWire
private val P = "a".repeat(32)
private val BODY = "{}".toByteArray()
private const val ORIGIN="https://v9.invalid"
private const val NOW = 1_800_000_000_000L
private fun rejects(block: () -> Unit) {
    var rejected = false
    try { block() } catch (_: IllegalArgumentException) { rejected = true }
    check(rejected) { "V9 expected rejection" }
}
fun main() {
    var passed = 0
    fun checkCase(run: () -> Unit) { run(); passed++; println("S5E4_KOTLIN_CASE_$passed=PASS") }
    val path = "/v9/private/admin/session/begin"
    checkCase { check(String(S.canonical("POST",ORIGIN,path,BODY,P,NOW)).startsWith("APPFORGE-V9-STAGE5E4-HTTP\u0000POST\n")) }
    checkCase { check(S.canonical("POST",ORIGIN,path,BODY,P,NOW).contentEquals(S.canonical("POST",ORIGIN,path,BODY,P,NOW))) }
    checkCase { check(!S.canonical("POST",ORIGIN,path,BODY,P,NOW).contentEquals(S.canonical("POST",ORIGIN,path,"{ }".toByteArray(),P,NOW))) }
    checkCase { check(!S.canonical("POST",ORIGIN,path,BODY,P,NOW).contentEquals(S.canonical("POST",ORIGIN,path,BODY,"b".repeat(32),NOW))) }
    checkCase { check(!S.canonical("POST",ORIGIN,path,BODY,P,NOW).contentEquals(S.canonical("POST",ORIGIN,"/v9/private/admin/device/decide",BODY,P,NOW))) }
    checkCase { rejects { S.canonical("GET",ORIGIN,path,BODY,P,NOW) } }
    checkCase { rejects { S.canonical("POST",ORIGIN,"/admin/debug",BODY,P,NOW) } }
    checkCase { rejects { S.canonical("POST",ORIGIN,path,BODY,"bad",NOW) } }
    checkCase { rejects { S.canonical("POST",ORIGIN,path,ByteArray(12001),P,NOW) } }
    checkCase { rejects { S.canonical("POST",ORIGIN,path,BODY,P,0) } }
    checkCase { rejects { S.canonical("POST","http://bad.invalid",path,BODY,P,NOW) } }
    val sig = "A".repeat(100)
    fun proof(t:Long=NOW,id:String=P,principal:String="c".repeat(64),signature:String=sig)=
        V9AdminProofWire.SignedProof(principal,id,t,signature)
    checkCase { check(proof().validate(NOW).proofId==P) }
    checkCase { rejects { proof(t=NOW-30_001).validate(NOW) } }
    checkCase { rejects { proof(t=NOW+30_001).validate(NOW) } }
    checkCase { rejects { proof(id="BAD").validate(NOW) } }
    checkCase { rejects { proof(principal="bad").validate(NOW) } }
    checkCase { rejects { proof(signature="BAD").validate(NOW) } }
    checkCase { check(proof(t=NOW-30_000).validate(NOW).issuedAtMs==NOW-30_000) }
    check(passed == 18)
    println("S5E4_KOTLIN_JVM=PASS;TESTS=$passed;FAIL=0")
}

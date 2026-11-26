package com.appforge.studio.security

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Source-only JVM checks using ephemeral in-memory test keys. No AndroidKeyStore or admin grants. */
private const val NOW5 = 1_800_000_000_000L
private const val ORIGIN5 = "https://v9.invalid"
private const val BEGIN5 = "/v9/private/admin/session/begin"
private const val DECIDE5 = "/v9/private/admin/device/decide"
private val BODY5 = "{}".toByteArray(Charsets.UTF_8)
private fun key(curve: String = "secp256r1") = KeyPairGenerator.getInstance("EC").apply {
    initialize(ECGenParameterSpec(curve))
}.generateKeyPair()
private fun makeSigner() = key().let { pair ->
    Pair(pair, V9AdminP256SigningCore(pair.private, pair.public.encoded, { NOW5 }))
}
private fun digest(bytes:ByteArray)= MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
private fun fail(block:() -> Unit){ var rejected=false;try{block()}catch(_:Exception){rejected=true};check(rejected)}
fun main() {
    var checks=0
    fun test(block:()->Unit){block(); checks++; println("S5E5_KOTLIN_CASE_$checks=PASS")}
    val (kp, signer)=makeSigner()
    val signed=signer.sign("POST",ORIGIN5,BEGIN5,BODY5)
    test { check(signed.validate(NOW5) == signed) }
    test { check(signed.principalId == digest(kp.public.encoded)) }
    test { check(signed.principalId.matches(Regex("^[0-9a-f]{64}$"))) }
    test { check(signed.proofId.matches(Regex("^[0-9a-f]{32}$"))) }
    test { check(signed.issuedAtMs == NOW5) }
    test { check(signed.signatureDerB64Url.matches(Regex("^[A-Za-z0-9_-]+$"))) }
    test {
        val decoded=Base64.getUrlDecoder().decode(signed.signatureDerB64Url)
        val bytes=V9AdminProofWire.canonical("POST",ORIGIN5,BEGIN5,BODY5,signed.proofId,signed.issuedAtMs)
        val verify=Signature.getInstance("SHA256withECDSA").apply { initVerify(kp.public);update(bytes) }
        check(verify.verify(decoded))
    }
    test {
        val signed2=signer.sign("POST",ORIGIN5,BEGIN5,BODY5)
        check(signed2.proofId != signed.proofId)
    }
    test { check(signer.sign("POST",ORIGIN5,DECIDE5,BODY5).principalId==signed.principalId) }
    test { check(signer.sign("POST",ORIGIN5,DECIDE5,BODY5).signatureDerB64Url.isNotBlank()) }
    test { fail { signer.sign("GET",ORIGIN5,BEGIN5,BODY5) } }
    test { fail { signer.sign("POST","http://v9.invalid",BEGIN5,BODY5) } }
    test { fail { signer.sign("POST",ORIGIN5,"/unknown",BODY5) } }
    test { fail { signer.sign("POST",ORIGIN5,BEGIN5,ByteArray(12001)) } }
    test { fail { V9AdminP256SigningCore(key("secp384r1").private, key("secp384r1").public.encoded,{NOW5}) } }
    test { fail { V9AdminP256SigningCore(kp.private, ByteArray(0),{NOW5}) } }
    test {
        val c=V9AdminProofWire.canonical("POST",ORIGIN5,BEGIN5,BODY5,"a".repeat(32),NOW5)
        check(digest(c)=="3e94e6a7b120f6d7776b9b79e9490b6f3f358d7b4f2a13d43a8c4f0a9731b51b")
    }
    test {
        val modified=V9AdminProofWire.canonical("POST",ORIGIN5,BEGIN5,"{ }".toByteArray(),signed.proofId,NOW5)
        val decoded=Base64.getUrlDecoder().decode(signed.signatureDerB64Url)
        val verify=Signature.getInstance("SHA256withECDSA").apply { initVerify(kp.public);update(modified) }
        check(!verify.verify(decoded))
    }
    test {
        val other=key()
        val decoded=Base64.getUrlDecoder().decode(signed.signatureDerB64Url)
        val bytes=V9AdminProofWire.canonical("POST",ORIGIN5,BEGIN5,BODY5,signed.proofId,signed.issuedAtMs)
        val verify=Signature.getInstance("SHA256withECDSA").apply { initVerify(other.public);update(bytes) }
        check(!verify.verify(decoded))
    }
    test { val s=V9AdminP256SigningCore(kp.private,kp.public.encoded,{ NOW5+1 });check(s.sign("POST",ORIGIN5,BEGIN5,BODY5).issuedAtMs==NOW5+1) }
    check(checks==20)
    println("S5E5_KOTLIN_JVM=PASS;TESTS=$checks;FAIL=0")
}

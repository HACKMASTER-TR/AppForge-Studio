package com.appforge.studio.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Arrays;
import java.util.Base64;
import org.junit.Before;
import org.junit.Test;

/** JVM-only trust regression tests. No APK, network, or production credentials. */
public final class V9ReleaseTrustDeepUnitTest {
    private byte[] body;
    private byte[] signature;
    private byte[] publicKey;
    private KeyPair signerKey;

    @Before public void setUp() throws Exception {
        body = "{\"schema\":\"test\"}\n".getBytes(StandardCharsets.UTF_8);
        signerKey = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        publicKey = signerKey.getPublic().getEncoded();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(signerKey.getPrivate());
        signer.update("APPFORGE_V9_RELEASE_V1\n".getBytes(StandardCharsets.US_ASCII));
        signer.update(body);
        signature = signer.sign();
    }

    private static String sha256(byte[] content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
        StringBuilder out = new StringBuilder();
        for (byte value : digest) out.append(String.format("%02x", value & 255));
        return out.toString();
    }

    private void verify(byte[] content, byte[] sig, byte[] key, String pin) throws Exception {
        V9ReleaseSignatureVerifier.verifyWithAnchor(content, sig, key, pin);
    }

    @Test public void trustedSpkiHashMatchesPin() throws Exception {
        byte[] trusted = Base64.getDecoder().decode(V9ReleaseSignatureVerifier.TRUSTED_SPKI_B64);
        assertEquals(V9ReleaseSignatureVerifier.TRUSTED_SPKI_SHA256, sha256(trusted));
    }

    @Test public void acceptsValidEphemeralSignature() throws Exception {
        verify(body, signature, publicKey, sha256(publicKey));
    }

    @Test public void rejectsModifiedManifest() throws Exception {
        byte[] modified = body.clone(); modified[1] ^= 1;
        assertThrows(SecurityException.class, () -> verify(modified, signature, publicKey, sha256(publicKey)));
    }

    @Test public void rejectsModifiedSignature() throws Exception {
        byte[] corrupt = signature.clone(); corrupt[0] ^= 1;
        assertThrows(SecurityException.class, () -> verify(body, corrupt, publicKey, sha256(publicKey)));
    }

    @Test public void rejectsWrongPublicKeyPin() {
        assertThrows(SecurityException.class, () -> verify(body, signature, publicKey, "00".repeat(32)));
    }

    @Test public void rejectsWrongSigningKey() throws Exception {
        byte[] other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic().getEncoded();
        assertThrows(SecurityException.class, () -> verify(body, signature, other, sha256(other)));
    }

    @Test public void enforcesSignatureDomainSeparation() throws Exception {
        Signature withoutDomain = Signature.getInstance("Ed25519");
        withoutDomain.initSign(signerKey.getPrivate());
        withoutDomain.update(body);
        byte[] wrongDomain = withoutDomain.sign();
        assertThrows(SecurityException.class, () -> verify(body, wrongDomain, publicKey, sha256(publicKey)));
    }

    @Test public void rejectsShortSignature() throws Exception {
        assertThrows(SecurityException.class, () -> verify(body, Arrays.copyOf(signature, 63), publicKey, sha256(publicKey)));
    }

    @Test public void rejectsForgedProductionSignature() {
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.verifyPinned(body, signature));
    }

    @Test public void acceptsValidObjectDigest() throws Exception {
        V9ReleaseSignatureVerifier.verifyObject(new ByteArrayInputStream(body), body.length, sha256(body));
    }

    @Test public void rejectsTamperedObject() throws Exception {
        byte[] modified = body.clone(); modified[1] ^= 1;
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.verifyObject(new ByteArrayInputStream(modified), body.length, sha256(body)));
    }

    @Test public void rejectsTruncatedObject() throws Exception {
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.verifyObject(new ByteArrayInputStream(body), body.length + 1L, sha256(body)));
    }

    @Test public void rejectsIncorrectObjectDigest() {
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.verifyObject(new ByteArrayInputStream(body), body.length, "00".repeat(32)));
    }

    @Test public void allowsSameSequence() {
        V9ReleaseSignatureVerifier.requireNoRollback(10L, 10L);
    }

    @Test public void allowsForwardSequence() {
        V9ReleaseSignatureVerifier.requireNoRollback(10L, 11L);
    }

    @Test public void rejectsRollback() {
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.requireNoRollback(10L, 9L));
    }

    @Test public void rejectsNegativeRollbackFloor() {
        assertThrows(SecurityException.class, () -> V9ReleaseSignatureVerifier.requireNoRollback(-1L, 11L));
    }
}

package com.appforge.studio.security;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.lang.reflect.Constructor;
import java.util.Base64;

/**
 * AppForge V9 staged release verifier. No network, no private keys, no production enabling.
 * Compiled SPKI trust anchor is independent of untrusted signature.json / download metadata.
 * The signed bytes are exactly: ASCII("APPFORGE_V9_RELEASE_V1\\n") + release-v1.json bytes.
 */
public final class V9ReleaseSignatureVerifier {
    private V9ReleaseSignatureVerifier() {}

    public static final String TRUSTED_SPKI_B64 =
            "MCowBQYDK2VwAyEA0JMW5UgobzPUxqVshfG7INhMNGFCM8gPq6ZhsD/KDqg=";
    public static final String TRUSTED_SPKI_SHA256 =
            "c612b3a0cbfc6d5478a23eba046ba1d62b32bd9b59fb5e54a7eaa66b86e3018e";
    private static final byte[] DOMAIN = "APPFORGE_V9_RELEASE_V1\n".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_MANIFEST_BYTES = 12L * 1024 * 1024;
    private static final long MAX_OBJECT_BYTES = 300_000_000L;

    public static void verifyPinned(byte[] manifest, byte[] signature) throws GeneralSecurityException {
        final byte[] spki = Base64.getDecoder().decode(TRUSTED_SPKI_B64);
        verifyWithAnchor(manifest, signature, spki, TRUSTED_SPKI_SHA256);
    }

    /** Testable primitive: production call sites MUST use verifyPinned, never an untrusted key. */
    static void verifyWithAnchor(byte[] manifest, byte[] signature, byte[] spki,
                                 String expectedPin) throws GeneralSecurityException {
        if (manifest == null || manifest.length == 0 || manifest.length > MAX_MANIFEST_BYTES ||
                signature == null || signature.length != 64 || spki == null || spki.length != 44 ||
                expectedPin == null || !expectedPin.matches("[a-f0-9]{64}")) {
            throw new SecurityException("Invalid V9 signature inputs");
        }
        final byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
        if (!MessageDigest.isEqual(digest, decodeSha(expectedPin))) {
            throw new SecurityException("Compiled V9 release SPKI pin mismatch");
        }
        PublicKey key;
        Signature verifier;
        try {
            key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
            verifier = Signature.getInstance("Ed25519");
        } catch (GeneralSecurityException unavailable) {
            // App minSdk=26. Its pre-existing net.i2p.crypto:eddsa dependency supports
            // older Android JCA providers without broadening accepted algorithms/keys.
            try {
                Class<?> pubClass = Class.forName("net.i2p.crypto.eddsa.EdDSAPublicKey");
                Constructor<?> pubCtor = pubClass.getConstructor(X509EncodedKeySpec.class);
                key = (PublicKey) pubCtor.newInstance(new X509EncodedKeySpec(spki));
                Class<?> engineClass = Class.forName("net.i2p.crypto.eddsa.EdDSAEngine");
                Constructor<?> engineCtor = engineClass.getConstructor(MessageDigest.class);
                verifier = (Signature) engineCtor.newInstance(MessageDigest.getInstance("SHA-512"));
            } catch (ReflectiveOperationException | ClassCastException err) {
                GeneralSecurityException reason = new GeneralSecurityException("Ed25519 verifier unavailable", err);
                reason.addSuppressed(unavailable);
                throw reason;
            }
        }
        verifier.initVerify(key);
        verifier.update(DOMAIN);
        verifier.update(manifest);
        if (!verifier.verify(signature)) throw new SecurityException("V9 signature invalid");
    }

    /** Verify the exact body of a content-addressed object before installation. */
    public static void verifyObject(InputStream source, long signedSize,
                                    String signedSha256) throws Exception {
        if (source == null || signedSize < 1 || signedSize > MAX_OBJECT_BYTES ||
                signedSha256 == null || !signedSha256.matches("[a-f0-9]{64}")) {
            throw new SecurityException("Invalid V9 object metadata");
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] chunk = new byte[64 * 1024];
        long total = 0;
        int stalls = 0;
        for (int n; (n = source.read(chunk)) >= 0;) {
            if (n == 0) {
                if (++stalls > 32) throw new SecurityException("Stalled V9 stream");
                continue;
            }
            stalls = 0;
            total += n;
            if (total > signedSize) throw new SecurityException("V9 object oversized");
            md.update(chunk, 0, n);
        }
        if (total != signedSize || !MessageDigest.isEqual(md.digest(), decodeSha(signedSha256))) {
            throw new SecurityException("V9 object length or digest mismatch");
        }
    }

    /** Note: a device-local floor is not a server-authoritative anti-rollback witness. */
    public static void requireNoRollback(long persistedFloor, long incomingSequence) {
        if (persistedFloor < 0 || incomingSequence < 1 || incomingSequence < persistedFloor) {
            throw new SecurityException("V9 release rollback rejected");
        }
    }

    private static byte[] decodeSha(String hex) {
        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16);
            int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new SecurityException("Invalid SHA256 hex");
            result[i] = (byte) ((hi << 4) | lo);
        }
        return result;
    }
}

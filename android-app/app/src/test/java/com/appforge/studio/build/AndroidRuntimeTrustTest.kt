package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.junit.Assert.*
import org.junit.Test
class AndroidRuntimeTrustTest {
 @Test fun systemRootsBootstrapVerifiedHttpsAndMutationsInvalidateReadiness() {
  val root=Files.createTempDirectory("runtime-tls").toFile()
  try {
   val sources=File(root,"etc/apt/sources.list.d/ubuntu.sources").apply{parentFile.mkdirs();writeText("URIs: http://ports.ubuntu.com/ubuntu-ports\nSuites: noble\nSigned-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg\n")}
   val certificate=requireNotNull(javaClass.getResourceAsStream("/runtime-trust-public-root.pem")).use{CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate}
   AndroidRuntimeTrust.install(root,listOf(certificate));assertTrue(AndroidRuntimeTrust.ready(root))
   assertTrue(sources.readText().contains("https://ports.ubuntu.com/ubuntu-ports"));assertTrue(sources.readText().contains("Signed-By:"))
   val config=File(root,"etc/apt/apt.conf.d/99appforge-system-tls");assertTrue(config.readText().contains("Verify-Peer \"true\""))
   val javaTrust=File(root,"etc/ssl/certs/appforge-system-roots-v5.p12")
   val store=java.security.KeyStore.getInstance("PKCS12");javaTrust.inputStream().use{store.load(it,"changeit".toCharArray())}
   assertEquals(1,store.size());assertArrayEquals(certificate.encoded,store.getCertificate("system-0").encoded)
   javaTrust.appendText("tampered");assertFalse(AndroidRuntimeTrust.ready(root))
   AndroidRuntimeTrust.install(root,listOf(certificate));assertTrue(AndroidRuntimeTrust.ready(root))
   config.appendText("tampered");assertFalse(AndroidRuntimeTrust.ready(root))
   AndroidRuntimeTrust.install(root,listOf(certificate));assertTrue(AndroidRuntimeTrust.ready(root))
  }finally{root.deleteRecursively()}
 }
}

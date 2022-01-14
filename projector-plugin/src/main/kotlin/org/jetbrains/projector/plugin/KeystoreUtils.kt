/*
 * Copyright (c) 2019-2021, JetBrains s.r.o. and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. JetBrains designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact JetBrains, Na Hrebenech II 1718/10, Prague, 14000, Czech Republic
 * if you need additional information or have any questions.
 */
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package org.jetbrains.projector.plugin


import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.util.EntityUtils
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.*
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509ExtensionUtils
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.jetbrains.projector.server.core.util.*
import org.jetbrains.projector.server.util.Host
import org.jetbrains.projector.server.util.getHostsList
import org.jetbrains.projector.server.util.isIp4String
import org.jetbrains.projector.server.util.isIp6String
import sun.security.pkcs10.PKCS10
import sun.security.tools.KeyStoreUtil.isSelfSigned
import sun.security.tools.KeyStoreUtil.signedBy
import java.io.*
import java.math.BigInteger
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.security.*
import java.security.cert.*
import java.security.cert.Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

enum class SupportedStorageTypes {
  JKS,
  PKCS12
}


enum class CertificateSource {
  PROJECTOR_CA,
  USER_IMPORTED
}

private val KEYSTORE_TYPE = SupportedStorageTypes.JKS

fun storageTypeToExtension(storageType: SupportedStorageTypes) = when (storageType) {
  SupportedStorageTypes.JKS -> "jks"
  SupportedStorageTypes.PKCS12 -> "p12"
}

private const val SSL_PROPERTIES_FILE = "ssl.properties"
private const val USER_SSL_PROPERTIES_FILE = "user.ssl.properties"
private val PROJECTOR_KEYSTORE_FILE_NAME = "projector.${storageTypeToExtension(KEYSTORE_TYPE)}"
private val USER_KEYSTORE_FILE_NAME = "user-imported.${storageTypeToExtension(KEYSTORE_TYPE)}"

private const val CERTIFICATE_ALIAS = "PROJECTOR-PLUGIN"
private const val KEY_ALGORITHM_NAME = "RSA"
private const val SIGNING_ALGORITHM_NAME = "SHA256withRSA"
private const val KEY_SIZE = 2048
private const val DN = "CN=idea-projector-plugin, OU=Development, O=Idea, L=SPB, C=RU"
private const val YEARS_VALID = 2
private const val DAYS_VALID = YEARS_VALID * 365
private const val SECONDS_VALID = DAYS_VALID * 24L * 60L * 60L

private const val CA_CRT_FILE_NAME = "projector-ca.crt"
private const val CA_ALIAS = "projector-ca"
private const val CA_DN = "CN=PROJECTOR-CA, OU=Development, O=Projector, L= SPB, C=RU"

fun isKeystoreExist() = isPluginSSLDirExist()
                        &&
                        isSSLPropertiesFileExist(CertificateSource.PROJECTOR_CA)
                        &&
                        isKeystoreFileExist()

fun recreateKeystoreFiles() {
  removeKeystoreFiles()
  createKeystoreFiles()
}

fun getPathToPluginSSLDir() = Paths.get(PathManager.getOptionsPath(), "ssl").toString().replace('\\', '/')

fun getPathToSSLPropertiesFile(source: CertificateSource): String {
  val name = when (source) {
    CertificateSource.PROJECTOR_CA -> SSL_PROPERTIES_FILE
    CertificateSource.USER_IMPORTED -> USER_SSL_PROPERTIES_FILE
  }

  return Paths.get(getPathToPluginSSLDir(), name).toString().replace('\\', '/')
}

fun isUserKeystoreFileExist() = File(getPathToUserKeystoreFile()).exists()

private fun getPathToCACertificateFile() = Paths.get(getPathToPluginSSLDir(), CA_CRT_FILE_NAME).toString().replace('\\', '/')

private fun getPathToUserKeystoreFile() = Paths.get(getPathToPluginSSLDir(), USER_KEYSTORE_FILE_NAME).toString().replace('\\', '/')

private fun isPluginSSLDirExist() = File(getPathToPluginSSLDir()).exists()

private fun isSSLPropertiesFileExist(source: CertificateSource) = File(getPathToSSLPropertiesFile(source)).exists()

private fun isKeystoreFileExist() = File(getPathToKeystoreFile()).exists()

private fun getPathToKeystoreFile() = Paths.get(getPathToPluginSSLDir(), PROJECTOR_KEYSTORE_FILE_NAME).toString().replace('\\', '/')

private fun createKeystoreFiles() {
  File(getPathToPluginSSLDir()).mkdirs()
  val props = createProjectorKeystore()
  createSSLPropertiesFile(getPathToSSLPropertiesFile(CertificateSource.PROJECTOR_CA), props)
}

private fun createProjectorKeystore(): SSLProperties {
  val password = generatePassword()
  val sslProps = SSLProperties(
    storeType = KEYSTORE_TYPE.toString(),
    filePath = "${getPathToPluginSSLDir()}/${PROJECTOR_KEYSTORE_FILE_NAME}",
    storePassword = password,
    keyPassword = password
  )

  val keyStore = KeyStore.getInstance(KEYSTORE_TYPE.toString()).apply {
    load(null, password.toCharArray())
  }

  val (caCertificate, caPrivateKey) = createCA()
  keyStore.setKeyEntry(CA_ALIAS, caPrivateKey, password.toCharArray(), arrayOf(caCertificate))
  createCACrtFile(caCertificate)

  val keyPair = generateKeypair()
  val csr = generateCSR(keyPair)
  val signedCert = signCertificate(csr, caPrivateKey, caCertificate)
  keyStore.setKeyEntry(CERTIFICATE_ALIAS, keyPair.private, password.toCharArray(), arrayOf(signedCert))

  FileOutputStream(sslProps.filePath).use { fos -> keyStore.store(fos, password.toCharArray()) }

  return sslProps
}

private fun signCertificate(csr: ByteArray, caPrivateKey: PrivateKey, signerCert: Certificate): X509Certificate {
  val pk10Holder = PKCS10CertificationRequest(csr)
  val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(SIGNING_ALGORITHM_NAME)
  val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
  val key = PrivateKeyFactory.createKey(caPrivateKey.encoded)
  val keyInfo = SubjectPublicKeyInfo.getInstance(pk10Holder.subjectPublicKeyInfo.encoded)

  val now = System.currentTimeMillis()

  val certBuilder = X509v3CertificateBuilder(
    org.bouncycastle.asn1.x500.X500Name(CA_DN),
    BigInteger(now.toString()),
    Date(now),
    Date(now + SECONDS_VALID * 1000),
    pk10Holder.subject,
    keyInfo)

  val digCalc = BcDigestCalculatorProvider().get(digAlgId)
  val x509ExtensionUtils = X509ExtensionUtils(digCalc)
  certBuilder.addExtension(Extension.subjectKeyIdentifier,
                           false,
                           x509ExtensionUtils.createSubjectKeyIdentifier(keyInfo))

  certBuilder.addExtension(Extension.authorityKeyIdentifier, false, AuthorityKeyIdentifier(signerCert.encoded))


  val altNames = getAltNames()
  val subjectAltNames = GeneralNames.getInstance(DERSequence(altNames))
  certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames)


  val sigGen = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(key)
  val holder = certBuilder.build(sigGen)
  val eeX509CertificateStructure = holder.toASN1Structure()
  val cf = CertificateFactory.getInstance("X.509", "BC")

  val stream = ByteArrayInputStream(eeX509CertificateStructure.encoded)
  val signed = cf.generateCertificate(stream) as X509Certificate
  stream.close()
  return signed
}

private fun getAltNames(): Array<GeneralName> {
  val result = arrayListOf<GeneralName>()
  val ips = getHostsList { Host(it, "") }.map { it.address }

  for (ip in ips) {
    try {
      if (isIp4String(ip) || isIp6String(ip)) {
        val gn = GeneralName(GeneralName.iPAddress, ip)
        result.add(gn)
      }
    }
    catch (e: UnknownHostException) {
      continue
    }
  }

  return result.toTypedArray()
}


private fun generateCSR(keyPair: KeyPair): ByteArray {
  val signature = Signature.getInstance(SIGNING_ALGORITHM_NAME).apply {
    initSign(keyPair.private)
  }

  with(PKCS10(keyPair.public)) {
    encodeAndSign(sun.security.x509.X500Name(DN), signature)
    return encoded
  }
}


private fun createCA(): Pair<X509Certificate, PrivateKey> {
  val keyPair = generateKeypair()
  val bcProvider = BouncyCastleProvider()
  Security.addProvider(bcProvider)
  val now = System.currentTimeMillis()
  val startDate = Date(now)
  val dnName = org.bouncycastle.asn1.x500.X500Name(CA_DN)
  val calendar = Calendar.getInstance()
  calendar.time = startDate
  calendar.add(Calendar.YEAR, 1)
  val endDate = calendar.time

  val contentSigner = JcaContentSignerBuilder(SIGNING_ALGORITHM_NAME).build(keyPair.private)

  val certBuilder = JcaX509v3CertificateBuilder(dnName,
                                                BigInteger(now.toString()),
                                                startDate,
                                                endDate,
                                                dnName,
                                                keyPair.public)


  val basicConstraints = BasicConstraints(true)
  certBuilder.addExtension(ASN1ObjectIdentifier("2.5.29.19"), true, basicConstraints)

  return Pair(JcaX509CertificateConverter().setProvider(bcProvider).getCertificate(certBuilder.build(contentSigner)), keyPair.private)
}

private fun createCACrtFile(cert: X509Certificate) {
  val base64Encoder = Base64.getMimeEncoder()
  File(getPathToCACertificateFile()).printWriter().use { out ->
    out.println("-----BEGIN CERTIFICATE-----")
    out.println(String(base64Encoder.encode(cert.encoded)))
    out.println("-----END CERTIFICATE-----")
  }
}

private fun generateKeypair(): KeyPair {
  val kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM_NAME).apply {
    initialize(KEY_SIZE)
  }

  return kpg.generateKeyPair()
}

private fun createSSLPropertiesFile(path: String, sslProperties: SSLProperties) {
  File(path).printWriter().use { out ->
    out.println("${SSL_STORE_TYPE}=${sslProperties.storeType}")
    out.println("${SSL_FILE_PATH}=${sslProperties.filePath}")
    out.println("${SSL_STORE_PASSWORD}=${sslProperties.storePassword}")
    out.println("${SSL_KEY_PASSWORD}=${sslProperties.keyPassword}")
  }
}

private fun removeKeystoreFiles() {
  removeFileIfExist(getPathToCACertificateFile())
  removeFileIfExist(getPathToKeystoreFile())
  removeFileIfExist(getPathToSSLPropertiesFile(CertificateSource.PROJECTOR_CA))
  removeFileIfExist(getPathToPluginSSLDir())
}

private fun removeFileIfExist(path: String) {
  with(File(path)) {
    if (exists())
      delete()
  }
}

private fun createUserKeystore(certificatePath: String, keyPath: String): SSLProperties {
  val password = generatePassword()
  val sslProps = SSLProperties(
    storeType = KEYSTORE_TYPE.toString(),
    filePath = "${getPathToPluginSSLDir()}/${USER_KEYSTORE_FILE_NAME}",
    storePassword = password,
    keyPassword = password
  )

  val keyStore = KeyStore.getInstance(KEYSTORE_TYPE.toString()).apply {
    load(null, password.toCharArray())
  }

  val certs = loadCertificateChain(certificatePath)
  val key = loadPrivateKey(keyPath)

  keyStore.setKeyEntry(CERTIFICATE_ALIAS, key, password.toCharArray(), certs)

  FileOutputStream(sslProps.filePath).use { fos -> keyStore.store(fos, password.toCharArray()) }

  return sslProps
}

private fun loadCertificateChain(certificatePath: String): Array<Certificate> {
  val res = FileInputStream(certificatePath).use {
    CertificateFactory.getInstance("X.509").generateCertificates(it).toTypedArray()
  }

  if (res.isNotEmpty() && !isFullChain(res) || res.size == 1) {
    return loadFullChainFor(res[0] as X509Certificate)
  }

  return res
}

private fun getAccessLocation(certificate: X509Certificate): String {
  val aiaExtensionValue = certificate.getExtensionValue(Extension.authorityInfoAccess.id) ?: return ""
  val oct = ASN1InputStream(ByteArrayInputStream(aiaExtensionValue)).readObject() as DEROctetString
  val authorityInformationAccess = AuthorityInformationAccess.getInstance(ASN1InputStream(oct.octets).readObject())

  for (ad in authorityInformationAccess.accessDescriptions) {

    if (ad.accessMethod != X509ObjectIdentifiers.id_ad_caIssuers)
      continue

    val gn = ad.accessLocation

    if (gn.tagNo != GeneralName.uniformResourceIdentifier)
      continue

    return DERIA5String.getInstance(gn.name).string
  }

  return ""
}

private fun loadFullChainFor(certificate: X509Certificate): Array<Certificate> {
  var lastCert = certificate
  val res = arrayListOf<Certificate>(lastCert)

  while (lastCert.issuerX500Principal != lastCert.subjectX500Principal) {
    val url = getAccessLocation(lastCert)

    if (url.isBlank()) {
      throw CertificateException("Can't get AccessLocation for certificate")
    }

    lastCert = getCertificateByUrl(url)
    res.add(lastCert)
  }

  return res.toTypedArray()
}

fun getCertificateByUrl(url: String): X509Certificate {
  val client = HttpClientBuilder.create().build()
  val get = HttpGet(url)
  val response = client.execute(get)
  val content = EntityUtils.toByteArray(response.entity)
  val certFactory = CertificateFactory.getInstance("X.509")
  val cert = certFactory.generateCertificate(ByteArrayInputStream(content)) as X509Certificate
  client.close()

  return cert
}

private fun isFullChain(chain: Array<Certificate>): Boolean {
  var prevCert: X509Certificate? = null

  for (cert in chain) {
    val certX509 = cert as X509Certificate

    if (isSelfSigned(certX509)) {
      return true
    }

    if (prevCert != null && !signedBy(prevCert, certX509)) {
      return false
    }

    prevCert = certX509
  }

  return true
}

private fun loadPrivateKey(keyPath: String): Key {
  val key = String(Files.readAllBytes(Paths.get(keyPath)), Charset.defaultCharset())

  val privateKeyPEM = key
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace(System.lineSeparator().toRegex(), "")
    .replace("-----END PRIVATE KEY-----", "")

  val encoded: ByteArray = Base64.getDecoder().decode(privateKeyPEM)
  val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM_NAME)
  val keySpec = PKCS8EncodedKeySpec(encoded)
  return keyFactory.generatePrivate(keySpec) as RSAPrivateKey
}

fun importUserCertificate(certificatePath: String, keyPath: String): Boolean {
  try {
    File(getPathToPluginSSLDir()).mkdirs()
    val props = createUserKeystore(certificatePath, keyPath)
    createSSLPropertiesFile(getPathToSSLPropertiesFile(CertificateSource.USER_IMPORTED), props)
    return true
  }
  catch (e: CertificateException) {
    Logger.getInstance("Projector import.certificate")
      .error("Error parsing importing certificate from file: $certificatePath error: ${e.message}")
  }
  catch (e: InvalidKeySpecException) {
    Logger.getInstance("Projector import.certificate")
      .error("Error parsing importing certificate from file: $certificatePath error: ${e.message}")
  }
  catch (e: IOError) {
    Logger.getInstance("Projector import.certificate")
      .error("Error parsing importing certificate from file: $certificatePath error: ${e.message}")
  }

  return false
}

fun getSubjectAlternativeNames(certificate: X509Certificate): Array<String> {
  val names = ArrayList<String>()

  try {
    val altNames = certificate.subjectAlternativeNames ?: return emptyArray()

    for (item in altNames) {
      val type = item[0] as Int

      if (type != 2 && type != 7)
        continue

      when (val data = item.toTypedArray()[1]) {
        is String -> names.add(data)
      }
    }
  }
  catch (e: CertificateParsingException) {
    Logger.getInstance("Projector parse.certificate")
      .error("""
           Error parsing SubjectAltName in certificate: $certificate
           error: ${e.message}
           """.trimIndent())
  }

  return names.toTypedArray()
}

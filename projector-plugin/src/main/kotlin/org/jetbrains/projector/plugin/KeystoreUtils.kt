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
import org.jetbrains.projector.server.core.util.*
import org.jetbrains.projector.server.util.Host
import org.jetbrains.projector.server.util.getHostsList
import org.jetbrains.projector.server.util.isIp4String
import org.jetbrains.projector.server.util.isIp6String
import sun.security.pkcs10.PKCS10
import sun.security.tools.keytool.CertAndKeyGen
import sun.security.util.SignatureUtil
import sun.security.x509.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
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
private const val USER_CERTIFICATE_ALIAS = "USERS-CERTIFICATE"
private const val KEY_ALGORITHM_NAME = "RSA"
private const val SIGNING_ALGORITHM_NAME = "SHA256withRSA"
private const val KEY_SIZE = 2048
private const val DN = "CN=idea-projector-plugin, OU=Development, O=Idea, L=SPB, S=SPB, C=RU"
private const val DAYS_VALID = 10000
private const val SECONDS_VALID = DAYS_VALID * 24L * 60L * 60L

private const val CA_CRT_FILE_NAME = "projector-ca.crt"
private const val CA_ALIAS = "projector-ca"
private const val CA_DN = "CN=PROJECTOR-CA, OU=Development, O=Projector, L=SPB, S=SPB, C=RU"

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

fun getPathToSSLPropertiesFile(source: CertificateSource ) : String {
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

private fun CertificateExtensions.add(ext: Extension) = set(ext.id, ext)

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

// black magic from keytool utility

private const val KEY_USAGE_BITMASK_SIZE = 9
private const val KEY_CERT_SIGN_EXT_INDEX = 5
private val KEY_CERT_SIGN_BITMASK = BooleanArray(KEY_USAGE_BITMASK_SIZE).apply { set(KEY_CERT_SIGN_EXT_INDEX, true) }
private val KEY_CERT_SIGN_EXTENSION = KeyUsageExtension(KEY_CERT_SIGN_BITMASK)

private fun signCertificate(csr: ByteArray, caPrivateKey: PrivateKey, signerCert: Certificate): X509Certificate {
  val signerCertInfo = X509CertImpl(signerCert.encoded)["${X509CertImpl.NAME}.${X509CertImpl.INFO}"] as X509CertInfo
  val params = AlgorithmId.getDefaultAlgorithmParameterSpec(SIGNING_ALGORITHM_NAME, caPrivateKey)
  SignatureUtil.initSignWithParam(Signature.getInstance(SIGNING_ALGORITHM_NAME), caPrivateKey, params, null)

  val info = X509CertInfo().apply {
    this[X509CertInfo.VALIDITY] = CertificateValidity(Date(), Date().apply {
      time += SECONDS_VALID * 1000L
    })
    this[X509CertInfo.SERIAL_NUMBER] = CertificateSerialNumber(Random().nextInt() and 0x7fffffff)
    this[X509CertInfo.VERSION] = CertificateVersion(CertificateVersion.V3)

    val algID = AlgorithmId.getWithParameterSpec(SIGNING_ALGORITHM_NAME, params)
    this[X509CertInfo.ALGORITHM_ID] = CertificateAlgorithmId(algID)
    this[X509CertInfo.ISSUER] = signerCertInfo["${X509CertInfo.SUBJECT}.${X509CertInfo.DN_NAME}"] as X500Name

    val req = PKCS10(csr)
    this[X509CertInfo.KEY] = CertificateX509Key(req.subjectPublicKeyInfo)
    this[X509CertInfo.SUBJECT] = req.subjectName

    val ext = CertificateExtensions().apply {
      add(SubjectKeyIdentifierExtension(KeyIdentifier(req.subjectPublicKeyInfo).identifier))
      add(AuthorityKeyIdentifierExtension(KeyIdentifier(signerCert.publicKey), null, null))
      add(SubjectAlternativeNameExtension(false, getGeneralNames()))
    }
    this[X509CertInfo.EXTENSIONS] = ext
  }

  return X509CertImpl(info).apply {
    sign(caPrivateKey, params, SIGNING_ALGORITHM_NAME, null)
  }
}

private fun getGeneralNames(): GeneralNames {
  val result = GeneralNames()
  val ips = getHostsList { Host(it, "") }.map { it.address }

  for (ip in ips) {
    try {
      if (isIp4String(ip) || isIp6String(ip)) {
        val gn = GeneralName(IPAddressName(ip))
        result.add(gn)
      }
    }
    catch (e: UnknownHostException) {
      continue
    }
  }

  return result
}

private fun generateCSR(keyPair: KeyPair): ByteArray {
  val signature = Signature.getInstance(SIGNING_ALGORITHM_NAME).apply {
    initSign(keyPair.private)
  }

  with(PKCS10(keyPair.public)) {
    encodeAndSign(X500Name(DN), signature)
    return encoded
  }
}


private fun createCA(): Pair<X509Certificate, PrivateKey> {
  val keyPair = CertAndKeyGen(KEY_ALGORITHM_NAME, SIGNING_ALGORITHM_NAME, CA_ALIAS).apply {
    generate(KEY_SIZE)
  }

  val extensions = CertificateExtensions().apply {
    add(BasicConstraintsExtension(true, true, -1))
    add(Extension.newExtension(KEY_CERT_SIGN_EXTENSION.extensionId, true, KEY_CERT_SIGN_EXTENSION.extensionValue))
  }

  val cert = keyPair.getSelfCertificate(X500Name(CA_DN), Date(), SECONDS_VALID, extensions)

  return Pair(cert, keyPair.privateKey)
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

fun createUserKeystore(certificatePath: String, keyPath: String): SSLProperties {
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

  keyStore.setKeyEntry(USER_CERTIFICATE_ALIAS, key, password.toCharArray(), certs)

  FileOutputStream(sslProps.filePath).use { fos -> keyStore.store(fos, password.toCharArray()) }

  return sslProps
}

fun loadCertificateChain(certificatePath: String): Array<Certificate> {
  return FileInputStream(certificatePath).use {
    CertificateFactory.getInstance("X.509").generateCertificates(it).toTypedArray()
  }
}

fun loadPrivateKey(keyPath: String): Key {
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

fun importUserCertificate(certificatePath: String, keyPath: String) : Boolean {
  try {
    File(getPathToPluginSSLDir()).mkdirs()
    val props = createUserKeystore(certificatePath, keyPath)
    createSSLPropertiesFile(getPathToSSLPropertiesFile(CertificateSource.USER_IMPORTED), props)
    return true
  }
  catch (_: CertificateException) {

  }
  catch (_: InvalidKeySpecException) {

  }

  return false
}

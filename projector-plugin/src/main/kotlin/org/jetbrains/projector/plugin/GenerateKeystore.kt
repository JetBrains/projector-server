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
import java.nio.file.Path
import java.nio.file.Paths
import java.security.*
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.*


private const val SSL_PROPERTIES_FILE = "ssl.properties"
private const val KEYSTORE_FILE_NAME = "projector.jks"

private const val KEYSTORE_TYPE = "jks"
private const val CERTIFICATE_ALIAS = "PROJECTOR-PLUGIN"
private const val KEY_ALGORITHM_NAME = "RSA"
private const val SIGNING_ALGORITHM_NAME = "SHA256withRSA"
private const val KEY_SIZE = 2048
private const val DN = "CN=idea-projector-plugin, OU=Development, O=Idea, L=SPB, S=SPB, C=RU"
private const val DAYS_VALID = 10000
private const val SECONDS_VALID = DAYS_VALID * 24L * 60L * 60L

private const val CA_CRT_FILE_NAME = "ca.crt"
private const val CA_ALIAS = "projector-ca"
private const val CA_DN = "CN=PROJECTOR-CA, OU=Development, O=Projector, L=SPB, S=SPB, C=RU"

fun isKeystoreExist() = isPluginSSLDirExist()
                        &&
                        isSSLPropertiesFileExist()
                        &&
                        isKeystoreFileExist()

fun recreateKeystoreFiles() {
  removeKeystoreFiles()
  createKeystoreFiles()
}

fun getPathToPluginSSLDir() = Paths.get(File(getPathToPluginDir()).parentFile.path, "ssl").toString().replace('\\', '/')

fun getPathToSSLPropertiesFile() = Paths.get(getPathToPluginSSLDir(), SSL_PROPERTIES_FILE).toString().replace('\\', '/')

private fun getPathToCACertificateFile() = Paths.get(getPathToPluginSSLDir(), CA_CRT_FILE_NAME).toString().replace('\\', '/')

private fun isPluginSSLDirExist() = File(getPathToPluginSSLDir()).exists()

private fun isSSLPropertiesFileExist() = File(getPathToSSLPropertiesFile()).exists()

private fun isKeystoreFileExist() = File(getPathToKeystoreFile()).exists()

private fun getPathToKeystoreFile() = if (isSSLPropertiesFileExist()) loadSSLProperties().file_path else ""

private fun createKeystoreFiles() {
  File(getPathToPluginSSLDir()).mkdirs()
  val props = createKeystore()
  createSSLPropertiesFile(props)
}

private data class SSLProperties(
  val store_type: String,
  val file_path: String,
  val store_password: String,
  val key_password: String,
)

private fun CertificateExtensions.add(ext: Extension) = set(ext.id, ext)

private fun createKeystore(): SSLProperties {
  val password = generatePassword()
  val sslProps = SSLProperties(
    store_type = KEYSTORE_TYPE,
    file_path = "${getPathToPluginSSLDir()}/${KEYSTORE_FILE_NAME}",
    store_password = password,
    key_password = password
  )

  val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply {
    load(null, password.toCharArray())
  }

  val (caCertificate, caPrivateKey) = createCA()
  keyStore.setKeyEntry(CA_ALIAS, caPrivateKey, password.toCharArray(), arrayOf(caCertificate))
  createCACrtFile(caCertificate)

  val keyPair = generateKeypair()
  val csr = generateCSR(keyPair)
  val signedCert = signCertificate(csr, caPrivateKey, caCertificate)

  keyStore.setKeyEntry(CERTIFICATE_ALIAS, keyPair.private, password.toCharArray(), arrayOf(signedCert))

  FileOutputStream(sslProps.file_path).use { fos -> keyStore.store(fos, password.toCharArray()) }

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
      if ( isIp4String(ip) || isIp6String(ip)) {
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

private fun createSSLPropertiesFile(sslProperties: SSLProperties) {
  File(getPathToSSLPropertiesFile()).printWriter().use { out ->
    out.println("${SSL_STORE_TYPE}=${sslProperties.store_type}")
    out.println("${SSL_FILE_PATH}=${sslProperties.file_path}")
    out.println("${SSL_STORE_PASSWORD}=${sslProperties.store_password}")
    out.println("${SSL_KEY_PASSWORD}=${sslProperties.key_password}")
  }
}

private fun removeKeystoreFiles() {
  removeFileIfExist(getPathToCACertificateFile())
  removeFileIfExist(getPathToKeystoreFile())
  removeFileIfExist(getPathToSSLPropertiesFile())
  removeFileIfExist(getPathToPluginSSLDir())
}

private fun removeFileIfExist(path: String) {
  with(File(path)) {
    if (exists())
      delete()
  }
}

private fun loadSSLProperties(): SSLProperties {
  val props = Properties().apply {
    load(FileInputStream(getPathToSSLPropertiesFile()))
  }

  return SSLProperties(
    store_type = props.getOrThrow(SSL_STORE_TYPE),
    file_path = props.getOrThrow(SSL_FILE_PATH),
    store_password = props.getOrThrow(SSL_STORE_PASSWORD),
    key_password = props.getOrThrow(SSL_KEY_PASSWORD)
  )
}

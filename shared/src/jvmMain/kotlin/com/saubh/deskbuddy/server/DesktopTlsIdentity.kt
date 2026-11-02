package com.saubh.deskbuddy.server

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.nio.channels.FileChannel
import javax.net.ssl.X509TrustManager

/** Persistent local TLS identity. Its certificate is pinned by phones after a manual first-use check. */
class DesktopTlsIdentity(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "server.p12"),
) {
    companion object {
        const val ALIAS = "konnekt-desktop"
        private const val STORE_PASSWORD = "deskbuddy-keystore-v1"
        private val timeFormat = DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'").withZone(ZoneOffset.UTC)
    }

    private val identity by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { loadOrCreate() }
    val keyStore: KeyStore get() = identity.first
    val certificate: X509Certificate get() = identity.second
    val password: CharArray get() = STORE_PASSWORD.toCharArray()
    val fingerprint: String by lazy { fingerprint(certificate) }

    /** Trust manager for the local second-instance handoff. */
    fun localTrustManager(): X509TrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf(certificate)
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty() || fingerprint(chain[0]) != fingerprint) {
                throw java.security.cert.CertificateException("Unexpected local KonneKt certificate")
            }
        }
    }

    private fun loadOrCreate(): Pair<KeyStore, X509Certificate> {
        Files.createDirectories(file.parent)
        runCatching { Files.setPosixFilePermissions(file.parent, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)) }
        val lockPath = file.resolveSibling("${file.fileName}.lock")
        return FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            runCatching { Files.setPosixFilePermissions(lockPath, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
            channel.lock().use {
                loadExisting() ?: createIdentity()
            }
        }
    }

    private fun loadExisting(): Pair<KeyStore, X509Certificate>? {
        if (!Files.exists(file)) return null
        runCatching { Files.setPosixFilePermissions(file, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
        val store = KeyStore.getInstance("PKCS12")
        Files.newInputStream(file).use { store.load(it, password) }
        val cert = store.getCertificate(ALIAS) as? X509Certificate
            ?: error("KonneKt TLS identity is missing its certificate")
        return store to cert
    }

    private fun createIdentity(): Pair<KeyStore, X509Certificate> {
        val store = KeyStore.getInstance("PKCS12")
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = createCertificate(pair.public.encoded, pair.private)
        store.load(null, password)
        store.setKeyEntry(ALIAS, pair.private, password, arrayOf(cert))
        val temp = Files.createTempFile(file.parent, "${file.fileName}-", ".tmp")
        runCatching { Files.setPosixFilePermissions(temp, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)) }
        try {
            Files.newOutputStream(temp).use { store.store(it, password) }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, file)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
        return store to cert
    }

    private fun createCertificate(subjectPublicKeyInfo: ByteArray, privateKey: java.security.PrivateKey): X509Certificate {
        val signatureAlgorithm = sequence(oid(1, 2, 840, 113549, 1, 1, 11), der(0x05, byteArrayOf()))
        val commonName = sequence(set(sequence(oid(2, 5, 4, 3), der(0x0c, "KonneKt Desktop".toByteArray(Charsets.UTF_8)))))
        val now = Instant.now()
        val validity = sequence(
            der(0x17, timeFormat.format(now.minusSeconds(86_400)).toByteArray(Charsets.US_ASCII)),
            der(0x18, DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'").withZone(ZoneOffset.UTC).format(now.plusSeconds(10L * 365 * 24 * 3600)).toByteArray(Charsets.US_ASCII)),
        )
        val sanValue = sequence(
            der(0x82, "localhost".toByteArray(Charsets.US_ASCII)),
            der(0x87, byteArrayOf(127, 0, 0, 1)),
            der(0x87, byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)),
        )
        val extensions = der(0xa3, sequence(sequence(oid(2, 5, 29, 17), der(0x04, sanValue))))
        val serial = ByteArray(16).also { java.security.SecureRandom().nextBytes(it); it[0] = (it[0].toInt() and 0x7f).toByte() }
        val tbs = sequence(
            der(0xa0, integer(byteArrayOf(2))),
            integer(serial),
            signatureAlgorithm,
            commonName,
            validity,
            commonName,
            subjectPublicKeyInfo,
            extensions,
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(tbs)
        val encoded = sequence(tbs, signatureAlgorithm, der(0x03, byteArrayOf(0) + signer.sign()))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
    }

    private fun fingerprint(certificate: X509Certificate): String = MessageDigest.getInstance("SHA-256")
        .digest(certificate.encoded)
        .joinToString(":") { "%02X".format(it) }

    private fun sequence(vararg values: ByteArray): ByteArray = der(0x30, values.fold(ByteArrayOutputStream()) { out, bytes -> out.apply { write(bytes) } }.toByteArray())
    private fun set(value: ByteArray): ByteArray = der(0x31, value)
    private fun integer(bytes: ByteArray): ByteArray {
        val withoutLeadingZeros = bytes.dropWhile { it == 0.toByte() }.toByteArray()
        val trimmed = if (withoutLeadingZeros.isEmpty()) byteArrayOf(0) else withoutLeadingZeros
        val positive = if (trimmed[0].toInt() and 0x80 != 0) byteArrayOf(0) + trimmed else trimmed
        return der(0x02, positive)
    }

    private fun oid(vararg arcs: Long): ByteArray {
        require(arcs.size >= 2)
        val bytes = ByteArrayOutputStream()
        fun writeArc(value: Long) {
            require(value >= 0)
            var current = value
            val parts = ArrayList<Int>()
            parts += (current and 0x7f).toInt()
            current = current ushr 7
            while (current > 0) { parts += ((current and 0x7f).toInt() or 0x80); current = current ushr 7 }
            parts.asReversed().forEach(bytes::write)
        }
        writeArc(arcs[0] * 40 + arcs[1])
        arcs.drop(2).forEach(::writeArc)
        return der(0x06, bytes.toByteArray())
    }

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        if (content.size < 128) out.write(content.size) else {
            var size = content.size
            val length = ByteArrayOutputStream()
            while (size > 0) { length.write(size and 0xff); size = size ushr 8 }
            val encodedLength = length.toByteArray().reversedArray()
            out.write(0x80 or encodedLength.size)
            out.write(encodedLength)
        }
        out.write(content)
        return out.toByteArray()
    }
}

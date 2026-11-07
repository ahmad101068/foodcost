package ir.foodcost.data.ops

import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.json.asObject
import ir.foodcost.data.json.json
import ir.foodcost.data.json.jsonObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * A backup locked with a password, for one that is sent or kept where others could read it: its
 * prices and recipes cannot be read, nor changed unnoticed, without the password. The key comes
 * from the password through PBKDF2 and the text is sealed with AES-GCM, which also tells a wrong
 * password or a changed file. There is no way back without the password.
 */
object BackupSeal {
    private const val FORMAT = "foodcost-backup-sealed"
    private const val VERSION = 1
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** The password does not open this backup, or the file was changed after it was sealed. */
    class WrongPassword : Exception("The password does not open this backup")

    /** Whether [text] is a backup locked with a password. */
    fun isSealed(text: String): Boolean = try {
        Json.parse(text).asObject("backup").stringOrNull("format") == FORMAT
    } catch (e: JsonException) {
        false
    }

    fun seal(text: String, password: CharArray, random: SecureRandom = SecureRandom()): String {
        require(password.isNotEmpty()) { "A sealed backup needs a password" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, ITERATIONS, iv)
        val data = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val base64 = Base64.getEncoder()
        return Json.write(
            jsonObject(
                "format" to FORMAT.json,
                "version" to VERSION.json,
                "kdf" to "PBKDF2WithHmacSHA256".json,
                "iterations" to ITERATIONS.json,
                "salt" to base64.encodeToString(salt).json,
                "iv" to base64.encodeToString(iv).json,
                "data" to base64.encodeToString(data).json,
            ),
        )
    }

    /** The backup inside [text]. Throws [WrongPassword], or [JsonException] for a file that is not one. */
    fun open(text: String, password: CharArray): String {
        val root = Json.parse(text).asObject("backup")
        if (root.stringOrNull("format") != FORMAT) throw JsonException("This file is not a sealed backup")
        val version = root.int("version")
        if (version != VERSION) throw JsonException("Sealed backup version $version is not supported")
        val iterations = root.int("iterations")
        if (iterations !in 10_000..10_000_000) throw JsonException("$iterations iterations is not a sealed backup's")
        val base64 = Base64.getDecoder()
        val parts = try {
            Triple(base64.decode(root.string("salt")), base64.decode(root.string("iv")), base64.decode(root.string("data")))
        } catch (e: IllegalArgumentException) {
            throw JsonException("A sealed backup's parts are not Base64")
        }
        val (salt, iv, data) = parts
        if (salt.size != SALT_BYTES) throw JsonException("A sealed backup's salt has ${salt.size} bytes")
        if (iv.size != IV_BYTES) throw JsonException("A sealed backup's nonce has ${iv.size} bytes")
        if (data.size < TAG_BITS / 8) throw JsonException("A sealed backup's encrypted data is too short")
        val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iterations, iv)
        val plain = try {
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
        return String(plain, Charsets.UTF_8)
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iterations: Int, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val key = try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, iv))
            updateAAD(FORMAT.toByteArray(Charsets.UTF_8))
        }
    }
}

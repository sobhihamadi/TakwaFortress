package com.example.takwafortress.services.security

import android.accounts.AccountManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Security
import java.security.cert.Certificate
import java.util.Date


class TakwaAdbManager private constructor() : AbsAdbConnectionManager() {
    companion object {
        private const val TAG = "TakwaAdbManager"

        @Volatile
        private var INSTANCE: TakwaAdbManager? = null

        fun getInstance(): TakwaAdbManager {
            try {
                Log.d(TAG, "getInstance() called")
                ensureBouncyCastleRegistered()
                if (INSTANCE == null) {
                    synchronized(this) {
                        if (INSTANCE == null) {
                            Log.d(TAG, "Creating new instance...")
                            INSTANCE = TakwaAdbManager()
                            Log.d(TAG, "Instance created successfully")
                        }
                    }
                }
                return INSTANCE!!
            } catch (e: Exception) {
                Log.e(TAG, "❌ FATAL: getInstance() failed", e)
                throw RuntimeException("Failed to get TakwaAdbManager instance: ${e.message}", e)
            }
        }

        private fun ensureBouncyCastleRegistered() {
            try {
                val existingProvider = Security.getProvider("BC")
                if (existingProvider == null) {
                    Log.d(TAG, "Registering BouncyCastle provider...")
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                    Log.d(TAG, "✅ BouncyCastle registered at position 1")
                } else {
                    Log.d(TAG, "✅ BouncyCastle already registered: ${existingProvider.name} v${existingProvider.version}")
                }
                Security.getProvider("BC") ?: throw RuntimeException("BouncyCastle registration failed!")
                Log.d(TAG, "═══════════════════════════════════════")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to register BouncyCastle", e)
                throw RuntimeException("BouncyCastle registration failed: ${e.message}", e)
            }
        }
    }

    private val mPrivateKey: PrivateKey
    private val mCertificate: Certificate

    init {
        try {
            Log.d(TAG, "🔧 Initializing TakwaAdbManager...")
            Log.d(TAG, "   Android version: ${android.os.Build.VERSION.SDK_INT}")
            Log.d(TAG, "   Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")

            Security.getProvider("BC") ?: throw RuntimeException("BouncyCastle provider not available!")
            Log.d(TAG, "✅ BC provider confirmed")

            setApi(android.os.Build.VERSION.SDK_INT)
            Log.d(TAG, "✅ API level set")

            Log.d(TAG, "Generating RSA key pair...")
            val keyGen = KeyPairGenerator.getInstance("RSA")
            keyGen.initialize(2048)
            val keyPair = keyGen.generateKeyPair()
            Log.d(TAG, "✅ Key pair generated (2048-bit RSA)")

            mPrivateKey = keyPair.private
            Log.d(TAG, "✅ Private key stored")

            Log.d(TAG, "Generating self-signed certificate...")
            mCertificate = generateCert(keyPair)
            Log.d(TAG, "✅ Certificate generated")

            Log.d(TAG, "🎉 TakwaAdbManager initialization complete!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ FATAL: Initialization failed", e)
            throw RuntimeException("TakwaAdbManager init failed: ${e.message}", e)
        }
    }

    override fun getPrivateKey(): PrivateKey = mPrivateKey
    override fun getCertificate(): Certificate = mCertificate
    override fun getDeviceName(): String = "TakwaFortress"

    private fun generateCert(keyPair: java.security.KeyPair): Certificate {
        try {
            Log.d(TAG, "🔐 Starting certificate generation...")
            val subject = X500Name("CN=TakwaFortress,O=TakwaFortress,C=US")
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
            Log.d(TAG, "   Subject: $subject")
            Log.d(TAG, "   Serial: $serial")
            Log.d(TAG, "   Creating content signer...")
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            Log.d(TAG, "   ✅ Content signer created")
            Log.d(TAG, "   Building certificate holder...")
            val certHolder = JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.public
            ).build(signer)
            Log.d(TAG, "   ✅ Certificate holder created")
            Log.d(TAG, "   Converting to X509 certificate...")
            val cert = JcaX509CertificateConverter().getCertificate(certHolder)
            Log.d(TAG, "   ✅ X509 conversion successful")
            Log.d(TAG, "✅ Certificate generated successfully!")
            Log.d(TAG, "   Type: ${cert.type}")
            Log.d(TAG, "   Issuer: ${cert.issuerX500Principal.name}")
            Log.d(TAG, "   Valid from: ${cert.notBefore}")
            Log.d(TAG, "   Valid until: ${cert.notAfter}")
            return cert
        } catch (e: Exception) {
            Log.e(TAG, "❌ Certificate generation failed", e)
            throw RuntimeException("Cert generation failed: ${e.message}", e)
        }
    }
}

class WirelessAdbService(private val context: Context) {

    companion object {
        private const val TAG = "WirelessAdbService"
        // How long to wait for dpm output before giving up
        private const val COMMAND_TIMEOUT_MS = 10_000L
    }

    suspend fun pairDevice(pairingCode: String, pairingPort: Int): WirelessAdbResult =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "🔗 PAIRING PROCESS STARTED")
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "Host: 127.0.0.1")
                Log.d(TAG, "Port: $pairingPort")
                Log.d(TAG, "Code: ${pairingCode.take(2)}****")

                Log.d(TAG, "Getting TakwaAdbManager instance...")
                val manager = TakwaAdbManager.getInstance()
                Log.d(TAG, "✅ Manager instance obtained")

                Log.d(TAG, "Calling manager.pair()...")
                val paired = manager.pair("127.0.0.1", pairingPort, pairingCode)
                Log.d(TAG, "Pair result: $paired")

                if (paired) {
                    Log.d(TAG, "✅ PAIRING SUCCESSFUL!")
                    Log.d(TAG, "═══════════════════════════════════════")
                    WirelessAdbResult.PairingSuccessNeedConnection
                } else {
                    Log.w(TAG, "❌ PAIRING REJECTED BY DEVICE")
                    Log.d(TAG, "═══════════════════════════════════════")
                    WirelessAdbResult.Failed(
                        "❌ Device rejected pairing\n\n" +
                                "Possible causes:\n" +
                                "• Wrong 6-digit code\n" +
                                "• Wrong port number\n" +
                                "• Code expired\n" +
                                "• Wireless Debugging off\n\n" +
                                "Try generating a new code."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ PAIRING EXCEPTION", e)
                val errorMsg = buildString {
                    append("❌ Pairing failed\n\nError: ${e.message}\n\n")
                    when {
                        e.message?.contains("timeout", ignoreCase = true) == true ->
                            append("Connection timeout.\nMake sure Wireless Debugging is ON.\n\n")
                        e.message?.contains("refused", ignoreCase = true) == true ->
                            append("Connection refused.\nCheck the port number.\n\n")
                    }
                    append("Technical details:\n${e.stackTraceToString().take(500)}")
                }
                WirelessAdbResult.Failed(errorMsg)
            }
        }

    suspend fun connectAndSetDeviceOwner(): WirelessAdbResult =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "═══════════════════════════════════════")
                Log.d(TAG, "📡 CONNECTION PROCESS STARTED")
                Log.d(TAG, "═══════════════════════════════════════")

                val manager = TakwaAdbManager.getInstance()
                Log.d(TAG, "Calling autoConnect()...")

                manager.autoConnect(context, 30_000L)
                Log.d(TAG, "✅ Connected via ADB")

                val command = "dpm set-device-owner com.example.takwafortress/.receivers.DeviceAdminReceiver"
                Log.d(TAG, "Executing: $command")

                // ── KEY FIX ──────────────────────────────────────────────────
                // readText() blocks forever because the ADB shell stream never
                // sends EOF. Instead, read line-by-line with a timeout.
                // The dpm command always emits exactly one line of output then stops.
                val output = readShellOutput(manager, command)
                // ─────────────────────────────────────────────────────────────

                Log.d(TAG, "Command output: '$output'")
                Log.d(TAG, "═══════════════════════════════════════")

                when {
                    output.contains("Success", ignoreCase = true) -> {
                        Log.d(TAG, "✅ DEVICE OWNER ACTIVATED!")
                        WirelessAdbResult.Success
                    }
                    output.contains("already", ignoreCase = true) -> {
                        Log.d(TAG, "✅ Device Owner already set")
                        WirelessAdbResult.Success
                    }
                    output.contains("account", ignoreCase = true) -> {
                        Log.w(TAG, "❌ ACCOUNTS STILL EXIST")
                        WirelessAdbResult.AccountsExist
                    }
                    output.isBlank() -> {
                        // Timed out reading — but dpm may have succeeded anyway.
                        // Check via DevicePolicyManager directly.
                        Log.w(TAG, "⚠️ No output received (timeout). Checking DPM directly...")
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                                as android.app.admin.DevicePolicyManager
                        if (dpm.isDeviceOwnerApp(context.packageName)) {
                            Log.d(TAG, "✅ DPM confirms Device Owner is active!")
                            WirelessAdbResult.Success
                        } else {
                            WirelessAdbResult.Failed("Command produced no output. Please try again.")
                        }
                    }
                    else -> {
                        Log.w(TAG, "❌ UNEXPECTED OUTPUT")
                        WirelessAdbResult.Failed("Activation failed:\n\n$output")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ CONNECTION/COMMAND EXCEPTION", e)
                Log.d(TAG, "═══════════════════════════════════════")

                // Even if we got an exception, the dpm command may have run.
                // Check directly before reporting failure.
                return@withContext try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                            as android.app.admin.DevicePolicyManager
                    if (dpm.isDeviceOwnerApp(context.packageName)) {
                        Log.d(TAG, "✅ Exception but DPM confirms Device Owner is active!")
                        WirelessAdbResult.Success
                    } else {
                        WirelessAdbResult.Failed("Error: ${e.message}")
                    }
                } catch (_: Exception) {
                    WirelessAdbResult.Failed("Error: ${e.message}")
                }
            }
        }

    /**
     * Reads shell command output line by line with a per-line timeout.
     *
     * Why not readText(): The ADB shell stream never closes — readText()
     * blocks forever waiting for EOF that never comes.
     *
     * Strategy:
     * 1. Read lines one at a time with a short timeout between each.
     * 2. Stop as soon as we see a recognisable result line.
     * 3. Fall back to a blank string if nothing arrives within the timeout.
     */
    private suspend fun readShellOutput(
        manager: TakwaAdbManager,
        command: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        val stream: AdbStream = manager.openStream("shell:$command")
        val output = StringBuilder()

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                val reader = BufferedReader(InputStreamReader(stream.openInputStream()))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isNotEmpty()) {
                        Log.d(TAG, "Shell line: $trimmed")
                        output.append(trimmed).append("\n")

                        // dpm always outputs one definitive line — stop reading
                        // as soon as we see it so we don't hang waiting for EOF
                        if (trimmed.contains("Success", ignoreCase = true) ||
                            trimmed.contains("Error", ignoreCase = true) ||
                            trimmed.contains("already", ignoreCase = true) ||
                            trimmed.contains("account", ignoreCase = true) ||
                            trimmed.contains("exception", ignoreCase = true)) {
                            break
                        }
                    }
                }
            }

            if (result == null) {
                Log.w(TAG, "⚠️ Shell read timed out after ${timeoutMs}ms")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Shell read interrupted: ${e.message}")
        } finally {
            try { stream.close() } catch (_: Exception) {}
        }

        output.toString().trim()
    }

    fun getPairingInstructions(): String = """
        🛡️ Wireless ADB Activation:
        
        1. Settings → Developer Options
        2. Turn ON "Wireless Debugging"
        3. Tap "Pair device with pairing code"
        4. Copy BOTH values:
           • 6-digit code
           • Port number (5 digits)
        5. Enter below and tap Activate
    """.trimIndent()

    fun isWirelessDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED, 0
            ) == 1
        } catch (_: Exception) { false }
    }

    fun hasConflictingAccounts(): Boolean =
        AccountManager.get(context).accounts.isNotEmpty()
}

sealed class WirelessAdbResult {
    object Success : WirelessAdbResult()
    object AccountsExist : WirelessAdbResult()
    object PairingSuccessNeedConnection : WirelessAdbResult()
    data class Failed(val reason: String) : WirelessAdbResult()
}
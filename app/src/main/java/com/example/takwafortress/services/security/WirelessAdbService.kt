package com.example.takwafortress.services.security

import android.accounts.AccountManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
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

                val bcProvider = Security.getProvider("BC")
                if (bcProvider == null) {
                    throw RuntimeException("BouncyCastle registration failed!")
                }

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

            val bcProvider = Security.getProvider("BC")
            if (bcProvider == null) {
                throw RuntimeException("BouncyCastle provider not available!")
            }
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
            Log.e(TAG, "   Exception type: ${e.javaClass.name}")
            Log.e(TAG, "   Message: ${e.message}")
            Log.e(TAG, "   Stack trace:", e)
            throw RuntimeException("TakwaAdbManager init failed: ${e.message}", e)
        }
    }

    override fun getPrivateKey(): PrivateKey = mPrivateKey
    override fun getCertificate(): Certificate = mCertificate
    override fun getDeviceName(): String = "TakwaFortress"

    /**
     * ✅ FINAL FIX: Use Android's native crypto for EVERYTHING
     * BouncyCastle jdk18on is incomplete on Android
     */
    private fun generateCert(keyPair: java.security.KeyPair): Certificate {
        try {
            Log.d(TAG, "🔐 Starting certificate generation...")

            val subject = X500Name("CN=TakwaFortress,O=TakwaFortress,C=US")
            val serial = BigInteger.valueOf(System.currentTimeMillis())
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)

            Log.d(TAG, "   Subject: $subject")
            Log.d(TAG, "   Serial: $serial")

            // ✅ STEP 1: Create signer using Android's default provider
            Log.d(TAG, "   Creating content signer...")
            val signer = JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.private)  // No setProvider() - uses Android's default
            Log.d(TAG, "   ✅ Content signer created")

            // ✅ STEP 2: Build certificate holder
            Log.d(TAG, "   Building certificate holder...")
            val certHolder = JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.public
            ).build(signer)
            Log.d(TAG, "   ✅ Certificate holder created")

            // ✅ STEP 3: Convert to X509 using Android's default provider (NOT BC)
            Log.d(TAG, "   Converting to X509 certificate...")
            val cert = JcaX509CertificateConverter()
                // DON'T call setProvider() - let it use Android's default X.509 support
                .getCertificate(certHolder)
            Log.d(TAG, "   ✅ X509 conversion successful")

            Log.d(TAG, "✅ Certificate generated successfully!")
            Log.d(TAG, "   Type: ${cert.type}")
            Log.d(TAG, "   Issuer: ${cert.issuerX500Principal.name}")
            Log.d(TAG, "   Valid from: ${cert.notBefore}")
            Log.d(TAG, "   Valid until: ${cert.notAfter}")

            return cert

        } catch (e: Exception) {
            Log.e(TAG, "❌ Certificate generation failed", e)
            Log.e(TAG, "   Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "   Message: ${e.message}")
            throw RuntimeException("Cert generation failed: ${e.message}", e)
        }
    }
}
// WirelessAdbService remains exactly the same as before
class WirelessAdbService(private val context: Context) {

    companion object {
        private const val TAG = "WirelessAdbService"
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
                Log.e(TAG, "Exception type: ${e.javaClass.name}")
                Log.e(TAG, "Message: ${e.message}")
                Log.e(TAG, "═══════════════════════════════════════")

                val errorMsg = buildString {
                    append("❌ Pairing failed\n\n")
                    append("Error: ${e.message}\n\n")

                    if (e.message?.contains("algorithm", ignoreCase = true) == true) {
                        append("This is a crypto library issue.\n")
                        append("Try restarting the app.\n\n")
                    } else if (e.message?.contains("timeout", ignoreCase = true) == true) {
                        append("Connection timeout.\n")
                        append("Make sure Wireless Debugging is ON.\n\n")
                    } else if (e.message?.contains("refused", ignoreCase = true) == true) {
                        append("Connection refused.\n")
                        append("Check the port number.\n\n")
                    }

                    append("Technical details:\n")
                    append(e.stackTraceToString().take(500))
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

                val output = manager.openStream("shell:$command").use { stream: AdbStream ->
                    stream.openInputStream().bufferedReader().readText()
                }

                Log.d(TAG, "Command output: $output")
                Log.d(TAG, "═══════════════════════════════════════")

                when {
                    output.contains("Success", ignoreCase = true) -> {
                        Log.d(TAG, "✅ DEVICE OWNER ACTIVATED!")
                        WirelessAdbResult.Success
                    }

                    output.contains("account", ignoreCase = true) -> {
                        Log.w(TAG, "❌ ACCOUNTS STILL EXIST")
                        WirelessAdbResult.AccountsExist
                    }

                    else -> {
                        Log.w(TAG, "❌ UNEXPECTED OUTPUT")
                        WirelessAdbResult.Failed("Activation failed:\n\n$output")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ CONNECTION/COMMAND EXCEPTION", e)
                Log.d(TAG, "═══════════════════════════════════════")
                WirelessAdbResult.Failed("Error: ${e.message}")
            }
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
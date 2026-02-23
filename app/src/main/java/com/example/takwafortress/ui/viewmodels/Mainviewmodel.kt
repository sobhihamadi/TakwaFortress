package com.example.takwafortress.ui.viewmodels

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.takwafortress.model.entities.IdentifierUser
import com.example.takwafortress.model.enums.SubscriptionStatus
import com.example.takwafortress.repository.implementations.FirebaseUserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

sealed class RouteDestination {
    object Welcome             : RouteDestination()
    object CommitmentSelection : RouteDestination()
    object DeviceOwnerSetup    : RouteDestination()
    object Dashboard           : RouteDestination()
    object ExpiredDashboard    : RouteDestination()   // ✅ plan ended → dashboard in expired mode
    object SubscriptionExpired : RouteDestination()   // paid sub cancelled mid-commitment
    // Legacy — kept so existing references compile, but routing no longer produces these
    object CommitmentComplete  : RouteDestination()
    object TrialExpired        : RouteDestination()
    data class UnauthorizedDevice(
        val currentDeviceId: String,
        val storedDeviceId: String
    ) : RouteDestination()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = FirebaseUserRepository(application)
    private val auth = FirebaseAuth.getInstance()

    private val _destination = MutableLiveData<RouteDestination>()
    val destination: LiveData<RouteDestination> = _destination

    // ── In-memory user cache ──────────────────────────────────────────────────
    // Avoids a Firestore round-trip every time MainActivity re-runs resolveRoute()
    // (e.g. after returning from another screen). TTL = 30 seconds.
    private var cachedUser: IdentifierUser? = null
    private var cacheTimestampMs: Long = 0L
    private val CACHE_TTL_MS = 30_000L

    fun clearCache() {
        cachedUser = null
        cacheTimestampMs = 0L
    }

    private fun getCurrentDeviceId(): String =
        Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"

    // ─────────────────────────────────────────────────────────────────────────
    // ROUTING DECISION TREE
    //
    // 1. Auth check
    // 2. Load user (cache → Firestore)
    // 3. Device ID validation
    // 4. Commitment end date check
    //      expired + status PENDING   → CommitmentSelection  (already reset last session)
    //      expired + status anything  → ExpiredDashboard     (plan just ended, show dashboard)
    // 5. Subscription status
    //      PENDING            → CommitmentSelection
    //      EXPIRED/CANCELLED  → SubscriptionExpired
    //      TRIAL/ACTIVE       → continue
    // 6. Device Owner check
    // 7. No commitment date → CommitmentSelection
    // 8. Active commitment  → Dashboard
    // ─────────────────────────────────────────────────────────────────────────
    fun resolveRoute() {
        viewModelScope.launch {
            try {
                // ── 1. Auth ──────────────────────────────────────────────────
                val firebaseUser = auth.currentUser
                if (firebaseUser == null) {
                    _destination.postValue(RouteDestination.Welcome)
                    return@launch
                }

                // ── 2. Load user (cached or Firestore) ────────────────────────
                val user = loadUser(firebaseUser.uid) ?: run {
                    _destination.postValue(RouteDestination.Welcome)
                    return@launch
                }

                // ── 3. Device ID validation ───────────────────────────────────
                val currentDeviceId = getCurrentDeviceId()
                val storedDeviceId  = user.deviceId
                val isPlaceholder   = storedDeviceId == "deviceid" ||
                        storedDeviceId == "unknown_device" ||
                        storedDeviceId.isBlank()

                if (!isPlaceholder && storedDeviceId != currentDeviceId) {
                    _destination.postValue(
                        RouteDestination.UnauthorizedDevice(currentDeviceId, storedDeviceId)
                    )
                    return@launch
                }

                // ── 4. Commitment end date check ──────────────────────────────
                val commitmentEndDate = user.commitmentEndDate
                val nowSeconds        = Timestamp.now().seconds

                if (commitmentEndDate != null && nowSeconds >= commitmentEndDate.seconds) {
                    // Plan has ended. Check subscription status:
                    //
                    // • PENDING → Firestore was already reset by a previous session
                    //             (FortressClearService ran, hasDeviceOwner=false, status=PENDING)
                    //             → Go straight to plan selection for a new cycle.
                    //
                    // • Anything else → Plan just ended this session or the user re-opened
                    //                   before Firestore was reset.
                    //                   → Send to dashboard in expired mode.
                    //                   FortressClearService + Firestore reset happen there.
                    if (user.subscriptionStatus == SubscriptionStatus.PENDING) {
                        _destination.postValue(RouteDestination.CommitmentSelection)
                    } else {
                        _destination.postValue(RouteDestination.ExpiredDashboard)
                    }
                    return@launch
                }

                // ── 5. Subscription status ────────────────────────────────────
                when (user.subscriptionStatus) {
                    SubscriptionStatus.PENDING -> {
                        _destination.postValue(RouteDestination.CommitmentSelection)
                        return@launch
                    }
                    SubscriptionStatus.TRIAL,
                    SubscriptionStatus.ACTIVE -> { /* continue */ }
                    SubscriptionStatus.EXPIRED,
                    SubscriptionStatus.CANCELLED -> {
                        _destination.postValue(RouteDestination.SubscriptionExpired)
                        return@launch
                    }
                }

                // ── 6. Device Owner check ─────────────────────────────────────
                if (!user.hasDeviceOwner) {
                    _destination.postValue(RouteDestination.DeviceOwnerSetup)
                    return@launch
                }

                // ── 7. No commitment date set yet ─────────────────────────────
                if (commitmentEndDate == null) {
                    _destination.postValue(RouteDestination.CommitmentSelection)
                    return@launch
                }

                // ── 8. Active commitment ──────────────────────────────────────
                _destination.postValue(RouteDestination.Dashboard)

            } catch (e: Exception) {
                Log.e("MainViewModel", "Routing error: ${e.message}")
                _destination.postValue(RouteDestination.Welcome)
            }
        }
    }

    /**
     * Returns the user from the 30-second in-memory cache if still fresh,
     * otherwise fetches from Firestore and updates the cache.
     *
     * This keeps routing snappy when the user navigates between screens
     * without spamming Firestore reads.
     */
    private suspend fun loadUser(uid: String): IdentifierUser? {
        val now    = System.currentTimeMillis()
        val cached = cachedUser
        if (cached != null && (now - cacheTimestampMs) < CACHE_TTL_MS) {
            Log.d("MainViewModel", "User loaded from cache")
            return cached
        }
        return try {
            val user = userRepository.get(uid)
            cachedUser       = user
            cacheTimestampMs = now
            Log.d("MainViewModel", "User fetched from Firestore and cached")
            user
        } catch (e: Exception) {
            Log.e("MainViewModel", "Could not load user: ${e.message}")
            null
        }
    }
}
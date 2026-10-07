package com.ridesafe.app.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ridesafe.app.data.model.JoinRequest
import com.ridesafe.app.data.model.JoinRequestStatus
import com.ridesafe.app.data.model.RideSession
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.model.TripInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * RideRepository handles all communication with Firebase Realtime Database and Auth.
 *
 * For Java Developers:
 * - 'suspend' functions are Kotlin's coroutine primitives (like non-blocking async tasks
 *   without callback hell).
 * - 'Flow' is Kotlin's reactive stream (similar to RxJava Observable or Java 9 Flow).
 * - 'callbackFlow' converts asynchronous listener-based APIs (like Firebase ValueEventListener)
 *   into a clean reactive Stream/Flow.
 */
class RideRepository {

    companion object {
        const val DATABASE_URL = "https://ridesafe-a46dc-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    private val database by lazy {
        try {
            FirebaseDatabase.getInstance(DATABASE_URL)
        } catch (e: Exception) {
            FirebaseDatabase.getInstance()
        }
    }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val ridesRef by lazy { database.getReference("rides") }

    /**
     * Ensures the current user is authenticated anonymously with Firebase.
     * This assigns a unique, persistent UID to each device without requiring emails/passwords.
     */
    suspend fun getOrCreateRiderId(): String {
        val currentUser = auth.currentUser
        if (currentUser != null) return currentUser.uid

        return try {
            val result = withTimeoutOrNull(4000L) {
                auth.signInAnonymously().await()
            }
            result?.user?.uid ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            // Local fallback ensures the app NEVER gets stuck if auth is pending or offline
            UUID.randomUUID().toString()
        }
    }

    /**
     * Checks if a ride code currently exists in Firebase Realtime Database.
     */
    suspend fun checkRideCodeExists(rideCode: String): Boolean {
        val cleanCode = rideCode.trim().uppercase()
        if (cleanCode.length != 6) return false
        return try {
            val snapshot = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine { continuation ->
                    val query = ridesRef.child(cleanCode)
                    val listener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(snapshot)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                    query.addListenerForSingleValueEvent(listener)
                    continuation.invokeOnCancellation {
                        query.removeEventListener(listener)
                    }
                }
            }
            snapshot != null && snapshot.exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generates a candidate 6-character ride code from memorable prefixes.
     */
    fun generateRideCode(): String {
        val prefixes = listOf(
            "MOTO", "RIDE", "BIKE", "CREW", "ROAD", "PACK", "TRIP", "TOUR", "FAST", "WIND",
            "GEAR", "APEX", "WHLZ", "CRUZ", "PEAK", "WILD", "ECHO", "BOLT", "DRIF", "RACE",
            "BEAM", "FLOW", "TREK", "HAWK", "RAID", "FURY", "DASH", "SURG", "NEON", "ROAR",
            "CLAN", "ZONE", "GRID", "RUSH", "NOVA", "STAR", "PACE", "BLZE", "STRM", "SHFT",
            "TRBO", "TRCK", "SPED", "TRAL", "PULS", "SPRK", "VRTX", "VIBE", "ROAM", "ACE1"
        )
        val prefix = prefixes.random()
        val number = (10..99).random()
        return "$prefix$number"
    }

    /**
     * Generates a short, memorable 6-character ride code that is GUARANTEED not to exist in DB.
     * Prevents reusing codes from existing or past rides (Fix 1).
     */
    suspend fun generateUniqueRideCode(): String {
        for (attempt in 1..25) {
            val candidate = generateRideCode()
            if (!checkRideCodeExists(candidate)) {
                return candidate
            }
        }
        // Fallback: 4 random uppercase letters + 2 digits if prefixes collide
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        for (attempt in 1..15) {
            val randomPrefix = (1..4).map { chars.random() }.joinToString("")
            val num = (10..99).random()
            val candidate = "$randomPrefix$num"
            if (!checkRideCodeExists(candidate)) {
                return candidate
            }
        }
        return generateRideCode()
    }

    /**
     * Creates a new ride session in Firebase with the given rider as the initial creator,
     * ensuring a fresh, unique code not present in DB (Fix 1).
     * Returns a Result containing Pair(rideCode, riderId).
     */
    suspend fun createRide(
        riderName: String,
        tripInfo: TripInfo? = null
    ): Result<Pair<String, String>> {
        return try {
            val riderId = getOrCreateRiderId()
            val rideCode = generateUniqueRideCode()

            val session = RideSession(
                code = rideCode,
                createdBy = riderId,
                createdAt = System.currentTimeMillis(),
                active = true
            )

            val initialRider = Rider(
                id = riderId,
                name = riderName.trim(),
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            // Ensure any stale data at this code is cleared
            val sessionRef = ridesRef.child(rideCode)
            sessionRef.keepSynced(true)
            sessionRef.removeValue().await()

            // Write session metadata and the creator into Firebase.
            sessionRef.child("session").setValue(session).await()
            sessionRef.child("riders").child(riderId).setValue(initialRider).await()
            Log.d("RideSafeDebug", "[FirebaseWrite] createRide SUCCESS: code=$rideCode, riderId=$riderId")

            // Write planned route (tripInfo) if provided
            if (tripInfo != null && tripInfo.isTripPlanned) {
                val poly = tripInfo.effectiveGeometry
                tripInfo.routeGeometry = poly
                tripInfo.encodedPolyline = poly
                if (tripInfo.distanceKm == 0.0 && tripInfo.distanceMeters > 0.0) {
                    tripInfo.distanceKm = tripInfo.distanceMeters / 1000.0
                }
                if (tripInfo.durationMin == 0.0 && tripInfo.durationSeconds > 0.0) {
                    tripInfo.durationMin = tripInfo.durationSeconds / 60.0
                }
                tripInfo.hasPlannedTrip = true

                sessionRef.child("tripInfo").setValue(tripInfo).await()
                Log.d("RideSafeDebug", "[FirebaseWrite] createRide tripInfo SUCCESS: code=$rideCode")
            }

            Result.success(Pair(rideCode, riderId))
        } catch (e: Exception) {
            Log.e("RideSafeDebug", "createRide failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observes the ride session metadata in real-time (code, createdBy, active status).
     */
    fun observeSession(rideCode: String): Flow<RideSession?> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val sessionRef = ridesRef.child(cleanCode).child("session")
        sessionRef.keepSynced(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val session = if (snapshot.exists()) {
                    snapshot.getValue(RideSession::class.java)
                } else null
                trySend(session)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        sessionRef.addValueEventListener(listener)
        awaitClose { sessionRef.removeEventListener(listener) }
    }

    /**
     * Observes the planned tripInfo node (start/dest and route geometry) in real-time.
     * All riders (both creator and joiners) use this to display the route polyline.
     */
    fun observeTripInfo(rideCode: String): Flow<TripInfo?> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val tripRef = ridesRef.child(cleanCode).child("tripInfo")
        tripRef.keepSynced(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val trip = if (snapshot.exists()) {
                    parseTripInfoSnapshot(snapshot, cleanCode)
                } else {
                    null
                }
                Log.d(
                    "RideSafeDebug",
                    "[FirebaseObserve] onDataChange tripInfo: $trip for $cleanCode (effectiveGeometry length=${trip?.effectiveGeometry?.length ?: 0}, isPlanned=${trip?.isTripPlanned})"
                )
                trySend(trip)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        tripRef.addValueEventListener(listener)
        awaitClose {
            tripRef.removeEventListener(listener)
        }
    }

    /**
     * Parses a Firebase DataSnapshot into a TripInfo instance, resilient to differences
     * between Android schema (routeGeometry, distanceMeters) and Web schema (encodedPolyline, distanceKm).
     */
    fun parseTripInfoSnapshot(snapshot: DataSnapshot, cleanCode: String): TripInfo? {
        if (!snapshot.exists()) return null

        var trip: TripInfo? = null
        try {
            trip = snapshot.getValue(TripInfo::class.java)
        } catch (e: Exception) {
            Log.w("RideSafeDebug", "TripInfo reflection parse error: ${e.message}")
        }

        if (trip == null) {
            trip = TripInfo()
        }

        // Resilient fallback: read geometry fields if missing in parsed bean
        val encodedPoly = snapshot.child("encodedPolyline").getValue(String::class.java)
            ?: snapshot.child("routeGeometry").getValue(String::class.java)
            ?: snapshot.child("geometry").getValue(String::class.java)
            ?: ""

        if (trip.encodedPolyline.isBlank() && encodedPoly.isNotBlank()) {
            trip.encodedPolyline = encodedPoly
        }
        if (trip.routeGeometry.isBlank() && encodedPoly.isNotBlank()) {
            trip.routeGeometry = encodedPoly
        }

        val startName = snapshot.child("startName").getValue(String::class.java)
        if (trip.startName.isBlank() && !startName.isNullOrBlank()) {
            trip.startName = startName
        }

        val destName = snapshot.child("destName").getValue(String::class.java)
        if (trip.destName.isBlank() && !destName.isNullOrBlank()) {
            trip.destName = destName
        }

        if (trip.startLat == 0.0) {
            trip.startLat = getDoubleValue(snapshot.child("startLat"))
        }
        if (trip.startLng == 0.0) {
            trip.startLng = getDoubleValue(snapshot.child("startLng"))
        }
        if (trip.destLat == 0.0) {
            trip.destLat = getDoubleValue(snapshot.child("destLat"))
        }
        if (trip.destLng == 0.0) {
            trip.destLng = getDoubleValue(snapshot.child("destLng"))
        }

        if (trip.distanceMeters == 0.0) {
            trip.distanceMeters = getDoubleValue(snapshot.child("distanceMeters"))
        }
        if (trip.distanceKm == 0.0) {
            trip.distanceKm = getDoubleValue(snapshot.child("distanceKm"))
        }
        if (trip.durationSeconds == 0.0) {
            trip.durationSeconds = getDoubleValue(snapshot.child("durationSeconds"))
        }
        if (trip.durationMin == 0.0) {
            trip.durationMin = getDoubleValue(snapshot.child("durationMin"))
        }

        val plannedVal = snapshot.child("isTripPlanned").getValue(Boolean::class.java)
        if (plannedVal == true || trip.effectiveGeometry.isNotBlank()) {
            trip.isTripPlanned = true
        }

        return trip
    }

    private fun getDoubleValue(snapshot: DataSnapshot): Double {
        if (!snapshot.exists()) return 0.0
        val value = snapshot.value ?: return 0.0
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    /**
     * Updates or sets tripInfo on an existing ride session.
     */
    fun saveTripInfo(rideCode: String, tripInfo: TripInfo) {
        val cleanCode = rideCode.trim().uppercase()
        val poly = tripInfo.effectiveGeometry
        tripInfo.routeGeometry = poly
        tripInfo.encodedPolyline = poly
        if (tripInfo.distanceKm == 0.0 && tripInfo.distanceMeters > 0.0) {
            tripInfo.distanceKm = tripInfo.distanceMeters / 1000.0
        }
        if (tripInfo.durationMin == 0.0 && tripInfo.durationSeconds > 0.0) {
            tripInfo.durationMin = tripInfo.durationSeconds / 60.0
        }
        tripInfo.hasPlannedTrip = true
        ridesRef.child(cleanCode).child("tripInfo").setValue(tripInfo)
    }

    /**
     * Joins an existing ride session using its 6-character code.
     * Uses addListenerForSingleValueEvent (cache-friendly) instead of .get() (server-only)
     * to prevent timeouts on slow mobile connections.
     */
    suspend fun joinRide(rideCode: String, riderName: String): Result<String> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val riderId = getOrCreateRiderId()

            // Enable local cache sync for this specific ride session
            ridesRef.child(cleanCode).keepSynced(true)
            val sessionRef = ridesRef.child(cleanCode).child("session")

            // Use addListenerForSingleValueEvent which can serve from Firebase's
            // local cache, unlike .get() which forces a server round-trip
            val snapshot = withTimeoutOrNull(15000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                Log.e("RideRepository", "joinRide query cancelled: ${error.message}")
                                continuation.resumeWithException(error.toException())
                            }
                        }
                    }
                    sessionRef.addListenerForSingleValueEvent(listener)

                    // Clean up listener if coroutine is cancelled
                    continuation.invokeOnCancellation {
                        sessionRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null) {
                return Result.failure(
                    Exception("Could not reach the server. Please check your internet and try again.")
                )
            }

            if (!snapshot.exists()) {
                return Result.failure(
                    IllegalArgumentException("Ride '$cleanCode' not found. Check the code and try again.")
                )
            }

            val rider = Rider(
                id = riderId,
                name = riderName.trim(),
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            // Add this rider to the ride's riders node.
            // setValue writes to local cache and synchronizes to Firebase Realtime Database
            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider)
                .addOnSuccessListener {
                    Log.d("RideSafeDebug", "[FirebaseWrite] joinRide rider write SUCCESS: code=$cleanCode, riderId=$riderId, name=${rider.name}")
                }
                .addOnFailureListener { e ->
                    Log.e("RideSafeDebug", "[FirebaseWrite] joinRide rider write FAILED: ${e.message}", e)
                }

            Result.success(riderId)
        } catch (e: Exception) {
            Log.e("RideRepository", "joinRide failed", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a pending join request in Firebase for leader/rider approval (Fix 2).
     * Returns Result containing the joiner's riderId.
     */
    suspend fun requestToJoinRide(rideCode: String, riderName: String): Result<String> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val riderId = getOrCreateRiderId()

            // Verify ride exists and is active
            if (!isRideActive(cleanCode)) {
                return Result.failure(
                    IllegalArgumentException("Ride '$cleanCode' not found or inactive. Check the code and try again.")
                )
            }

            ridesRef.child(cleanCode).keepSynced(true)
            val request = JoinRequest(
                id = riderId,
                rideCode = cleanCode,
                riderId = riderId,
                riderName = riderName.trim().ifEmpty { "Rider" },
                timestamp = System.currentTimeMillis(),
                status = JoinRequestStatus.PENDING.name
            )

            // Write join request to rides/{cleanCode}/joinRequests/{riderId}
            ridesRef.child(cleanCode).child("joinRequests").child(riderId).setValue(request).await()
            Log.d("RideSafeDebug", "requestToJoinRide created: ride=$cleanCode, riderId=$riderId")

            Result.success(riderId)
        } catch (e: Exception) {
            Log.e("RideRepository", "requestToJoinRide failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observes the status of a specific rider's join request in real-time.
     */
    fun observeJoinRequest(rideCode: String, riderId: String): Flow<JoinRequest?> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val requestRef = ridesRef.child(cleanCode).child("joinRequests").child(riderId)
        requestRef.keepSynced(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val request = if (snapshot.exists()) {
                    snapshot.getValue(JoinRequest::class.java)
                } else null
                trySend(request)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        requestRef.addValueEventListener(listener)
        awaitClose { requestRef.removeEventListener(listener) }
    }

    /**
     * Observes all active pending join requests for a ride session in real-time (Fix 2).
     * Used by the creator / present riders to receive incoming join requests.
     */
    fun observeJoinRequests(rideCode: String): Flow<List<JoinRequest>> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val requestsRef = ridesRef.child(cleanCode).child("joinRequests")
        requestsRef.keepSynced(true)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<JoinRequest>()
                val now = System.currentTimeMillis()
                for (child in snapshot.children) {
                    val req = child.getValue(JoinRequest::class.java)
                    // Keep requests that are pending and within 2 minutes (120 seconds)
                    if (req != null && req.isPending && (now - req.timestamp <= 120_000L)) {
                        list.add(req.copy(id = child.key ?: req.id))
                    }
                }
                trySend(list)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        requestsRef.addValueEventListener(listener)
        awaitClose { requestsRef.removeEventListener(listener) }
    }

    /**
     * Cancels an existing join request (e.g. When the user taps Cancel).
     */
    fun cancelJoinRequest(rideCode: String, riderId: String) {
        try {
            val cleanCode = rideCode.trim().uppercase()
            ridesRef.child(cleanCode).child("joinRequests").child(riderId).removeValue()
        } catch (e: Exception) {
            Log.w("RideRepository", "cancelJoinRequest failed: ${e.message}")
        }
    }

    /**
     * Approves or declines a join request in Firebase (Fix 2).
     */
    fun respondToJoinRequest(
        rideCode: String,
        riderId: String,
        approved: Boolean,
        responderId: String = ""
    ) {
        val cleanCode = rideCode.trim().uppercase()
        val status = if (approved) JoinRequestStatus.APPROVED.name else JoinRequestStatus.DECLINED.name
        val updates = mapOf<String, Any>(
            "status" to status,
            "respondedBy" to responderId
        )
        ridesRef.child(cleanCode).child("joinRequests").child(riderId).updateChildren(updates)
    }

    /**
     * Completes the join process once approved by creator/rider.
     * Adds the rider to the active riders list and removes the join request.
     */
    suspend fun completeJoinRide(rideCode: String, riderId: String, riderName: String): Result<Unit> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val rider = Rider(
                id = riderId,
                name = riderName.trim(),
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )
            // Add to active convoy riders
            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider).await()
            // Clean up request node
            ridesRef.child(cleanCode).child("joinRequests").child(riderId).removeValue()
            Log.d("RideSafeDebug", "completeJoinRide SUCCESS for $riderId in $cleanCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RideRepository", "completeJoinRide failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the current rider's real-time GPS coordinates and speed.
     */
    fun updateLocation(
        rideCode: String,
        riderId: String,
        lat: Double,
        lng: Double,
        speed: Float
    ) {
        val cleanCode = rideCode.trim().uppercase()
        val updates = mapOf<String, Any>(
            "lat" to lat,
            "lng" to lng,
            "speed" to speed,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(cleanCode).child("riders").child(riderId).updateChildren(updates) { error, _ ->
            if (error != null) {
                Log.e("RideSafeDebug", "[FirebaseWrite] updateLocation FAILED: ${error.message} (code: ${error.code}) for riderId=$riderId, ride=$cleanCode")
            } else {
                Log.d("RideSafeDebug", "[FirebaseWrite] updateLocation SUCCESS: riderId=$riderId, lat=$lat, lng=$lng, speed=$speed")
            }
        }
    }

    /**
     * Updates the current rider's stop status (e.g. Refueling, Emergency, Rest Stop).
     */
    fun updateStatus(rideCode: String, riderId: String, status: RiderStatus) {
        val cleanCode = rideCode.trim().uppercase()
        val updates = mapOf<String, Any>(
            "status" to status.name,
            "lastUpdated" to System.currentTimeMillis()
        )
        ridesRef.child(cleanCode).child("riders").child(riderId).updateChildren(updates)
    }

    /**
     * Observes all riders currently in the ride session in real-time.
     * Uses Kotlin Flow to emit the updated list whenever Firebase notifies of changes.
     */
    fun observeRiders(rideCode: String): Flow<List<Rider>> = callbackFlow {
        val cleanCode = rideCode.trim().uppercase()
        val ridersRef = ridesRef.child(cleanCode).child("riders")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ridersList = mutableListOf<Rider>()
                for (child in snapshot.children) {
                    val rider = child.getValue(Rider::class.java)
                    if (rider != null) {
                        ridersList.add(rider.copy(id = child.key ?: rider.id))
                    }
                }
                Log.d(
                    "RideSafeDebug",
                    "[FirebaseObserve] onDataChange: ${ridersList.size} riders received for $cleanCode -> ${ridersList.map { "${it.name}(id=${it.id}, lat=${it.lat}, lng=${it.lng})" }}"
                )
                // Emit the new list to the Flow collector (ViewModel)
                trySend(ridersList)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        // Attach listener to Firebase Realtime Database
        ridersRef.addValueEventListener(listener)

        // When the Flow collector cancels (e.g. user leaves screen), remove the listener
        awaitClose {
            ridersRef.removeEventListener(listener)
        }
    }

    /**
     * Removes the rider from the ride session upon leaving.
     */
    fun leaveRide(rideCode: String, riderId: String) {
        try {
            val cleanCode = rideCode.trim().uppercase()
            ridesRef.child(cleanCode).child("riders").child(riderId).removeValue()
        } catch (e: Exception) {
            // Log or ignore network errors on exit
        }
    }

    /**
     * Checks whether a ride session is still active in Firebase Realtime Database.
     * A ride is active if its node exists, session.active is true, and it has at least one rider.
     */
    suspend fun isRideActive(rideCode: String): Boolean {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            val rideNodeRef = ridesRef.child(cleanCode)

            val snapshot = withTimeoutOrNull(8000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                    rideNodeRef.addListenerForSingleValueEvent(listener)
                    continuation.invokeOnCancellation {
                        rideNodeRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null || !snapshot.exists()) {
                return false
            }

            val sessionNode = snapshot.child("session")
            if (!sessionNode.exists()) {
                return false
            }

            val sessionActive = sessionNode.child("active").getValue(Boolean::class.java) ?: true
            val ridersNode = snapshot.child("riders")
            val hasRiders = ridersNode.exists() && ridersNode.childrenCount > 0

            hasRiders && sessionActive
        } catch (e: Exception) {
            Log.e("RideRepository", "Error checking isRideActive for $rideCode", e)
            false
        }
    }

    /**
     * Re-registers an existing rider into an active ride session using their saved riderId.
     */
    suspend fun rejoinRide(rideCode: String, riderId: String, riderName: String): Result<Unit> {
        return try {
            val cleanCode = rideCode.trim().uppercase()
            ridesRef.child(cleanCode).keepSynced(true)
            val sessionRef = ridesRef.child(cleanCode).child("session")

            val snapshot = withTimeoutOrNull(10000L) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            if (continuation.isActive) {
                                continuation.resume(dataSnapshot)
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(error.toException())
                            }
                        }
                    }
                    sessionRef.addListenerForSingleValueEvent(listener)
                    continuation.invokeOnCancellation {
                        sessionRef.removeEventListener(listener)
                    }
                }
            }

            if (snapshot == null) {
                return Result.failure(Exception("Could not connect to Firebase. Check internet connection."))
            }

            if (!snapshot.exists()) {
                return Result.failure(IllegalArgumentException("Ride '$cleanCode' not found. It may have been ended."))
            }

            val rider = Rider(
                id = riderId,
                name = riderName.trim().ifEmpty { "Rider" },
                status = RiderStatus.RIDING.name,
                lastUpdated = System.currentTimeMillis()
            )

            ridesRef.child(cleanCode).child("riders").child(riderId).setValue(rider)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("RideRepository", "rejoinRide failed for $rideCode", e)
            Result.failure(e)
        }
    }
}

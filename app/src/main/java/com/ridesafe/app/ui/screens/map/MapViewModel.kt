package com.ridesafe.app.ui.screens.map

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ridesafe.app.data.model.RideSession
import com.ridesafe.app.data.model.Rider
import com.ridesafe.app.data.model.RiderStatus
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.service.LocationTrackingService
import com.ridesafe.app.util.LocationUtils
import com.ridesafe.app.util.PolylineUtils
import org.osmdroid.util.GeoPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RiderWithDistance pairs a Rider with their calculated distance, formatted string,
 * and ahead/behind relative position relative to the current app user.
 */
data class RiderWithDistance(
    val rider: Rider,
    val distanceMeters: Float = 0f,
    val formattedDistance: String = "",
    val isCurrentUser: Boolean = false,
    val relativePositionText: String = "", // e.g. "Rahul is 10 KM ahead of You"
    val isAhead: Boolean? = null // true = ahead, false = behind, null = self/unknown
)

/**
 * UI State for the Live Map Screen.
 * In Jetpack Compose, UI is a function of State. Whenever this state changes,
 * Compose automatically recomposes only the elements that need updating.
 */
data class MapUiState(
    val rideCode: String = "",
    val currentRiderId: String = "",
    val currentRiderName: String = "",
    val myStatus: RiderStatus = RiderStatus.RIDING,
    val riders: List<RiderWithDistance> = emptyList(),
    val selectedRider: RiderWithDistance? = null,
    val tripInfo: TripInfo? = null,
    val routePoints: List<GeoPoint> = emptyList(),
    val isStatusPickerOpen: Boolean = false,
    val isRiderListOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class MapViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = RideRepository()
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var riderObservationJob: Job? = null
    private var tripObservationJob: Job? = null
    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var lastKnownHeading: Float? = null
    private var localCurrentLocation: Rider? = null
    private var latestFirebaseRiders: List<Rider> = emptyList()

    /**
     * Initializes the map session for a given rideCode and rider.
     */
    fun initSession(rideCode: String, riderId: String, riderName: String) {
        val cleanCode = rideCode.trim().uppercase()
        Log.d("RideSafeDebug", "[MapViewModel] initSession called: cleanCode='$cleanCode', riderId='$riderId', riderName='$riderName'")

        // Guard against duplicate initialization resetting state on recomposition
        if (_uiState.value.rideCode == cleanCode && _uiState.value.currentRiderId == riderId && _uiState.value.riders.isNotEmpty()) {
            Log.d("RideSafeDebug", "[MapViewModel] initSession guard skipped duplicate init")
            return
        }

        val initialUser = Rider(
            id = riderId,
            name = riderName,
            status = RiderStatus.RIDING.name,
            lastUpdated = System.currentTimeMillis()
        )
        localCurrentLocation = initialUser

        _uiState.update {
            it.copy(
                rideCode = cleanCode,
                currentRiderId = riderId,
                currentRiderName = riderName,
                myStatus = RiderStatus.RIDING,
                riders = listOf(
                    RiderWithDistance(
                        rider = initialUser,
                        distanceMeters = 0f,
                        formattedDistance = "You",
                        isCurrentUser = true,
                        relativePositionText = "$riderName (You)",
                        isAhead = null
                    )
                ),
                isLoading = false
            )
        }

        // Start active in-app location tracking immediately so the user's location is always visible
        startLocationTracking()

        // Start listening to live updates from Firebase
        observeRiders()

        // Start listening to planned trip route from Firebase
        observeTripInfo(cleanCode)
    }


    private fun startLocationTracking() {
        val app = getApplication<Application>()
        val client = LocationServices.getFusedLocationProviderClient(app)
        fusedClient = client

        try {
            // 1. Check lastLocation immediately for instant rendering if available
            client.lastLocation.addOnSuccessListener { loc ->
                Log.d("RideSafeDebug", "[Location] MapViewModel lastLocation callback: loc=$loc")
                if (loc != null) {
                    onLocalLocationReceived(
                        loc.latitude,
                        loc.longitude,
                        loc.speed,
                        if (loc.hasBearing()) loc.bearing else null
                    )
                }
            }

            // 2. Actively request current fresh location fix from hardware
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                Log.d("RideSafeDebug", "[Location] MapViewModel getCurrentLocation callback: loc=$loc")
                if (loc != null) {
                    onLocalLocationReceived(
                        loc.latitude,
                        loc.longitude,
                        loc.speed,
                        if (loc.hasBearing()) loc.bearing else null
                    )
                }
            }

            // 3. Register continuous high-accuracy location updates for real-time map updates
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000L
            ).apply {
                setMinUpdateIntervalMillis(1500L)
                setMinUpdateDistanceMeters(0f)
                setWaitForAccurateLocation(false)
            }.build()

            // Remove existing callback if any
            locationCallback?.let { client.removeLocationUpdates(it) }

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    Log.d("RideSafeDebug", "[Location] MapViewModel onLocationResult: lat=${loc.latitude}, lng=${loc.longitude}, speed=${loc.speed}")
                    onLocalLocationReceived(
                        loc.latitude,
                        loc.longitude,
                        loc.speed,
                        if (loc.hasBearing()) loc.bearing else null
                    )
                }
            }
            locationCallback = callback
            Log.d("RideSafeDebug", "[Location] MapViewModel requesting continuous location updates...")
            client.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("RideSafeDebug", "[Location] MapViewModel SecurityException on location tracking: ${e.message}", e)
        }
    }

    private fun onLocalLocationReceived(lat: Double, lng: Double, speed: Float, heading: Float?) {
        Log.d("RideSafeDebug", "[Location] MapViewModel onLocalLocationReceived: lat=$lat, lng=$lng, speed=$speed, heading=$heading")
        if (heading != null && heading >= 0f) {
            lastKnownHeading = heading
        }

        val myId = _uiState.value.currentRiderId
        val myName = _uiState.value.currentRiderName
        val cleanCode = _uiState.value.rideCode

        val updatedUser = Rider(
            id = myId,
            name = myName,
            lat = lat,
            lng = lng,
            speed = speed,
            status = _uiState.value.myStatus.name,
            lastUpdated = System.currentTimeMillis()
        )
        localCurrentLocation = updatedUser

        // Push to Firebase so all other group members see our updated coordinates
        if (cleanCode.isNotEmpty() && myId.isNotEmpty()) {
            Log.d("RideSafeDebug", "[Location] Pushing location to Firebase from MapViewModel for $myId...")
            repository.updateLocation(cleanCode, myId, lat, lng, speed)
        }

        // Re-process UI state with updated location
        updateRidersWithLocalLocation()
    }

    private fun observeRiders() {
        riderObservationJob?.cancel()
        val rideCode = _uiState.value.rideCode

        riderObservationJob = viewModelScope.launch {
            repository.observeRiders(rideCode)
                .catch { error ->
                    _uiState.update { it.copy(errorMessage = error.message, isLoading = false) }
                }
                .collect { ridersList ->
                    latestFirebaseRiders = ridersList
                    updateRidersWithLocalLocation()
                }
        }
    }

    private fun observeTripInfo(codeToObserve: String? = null) {
        tripObservationJob?.cancel()
        val rideCode = (codeToObserve ?: _uiState.value.rideCode).trim().uppercase()
        if (rideCode.isEmpty()) return

        tripObservationJob = viewModelScope.launch {
            repository.observeTripInfo(rideCode)
                .catch { error ->
                    Log.w("RideSafeDebug", "observeTripInfo error: ${error.message}")
                }
                .collect { trip ->
                    val points = if (trip != null && trip.isTripPlanned) {
                        val decoded = PolylineUtils.decodeGeometry(trip.effectiveGeometry)
                        if (decoded.isEmpty() && trip.startLat != 0.0 && trip.destLat != 0.0) {
                            listOf(trip.startGeoPoint, trip.destGeoPoint)
                        } else {
                            decoded
                        }
                    } else if (trip != null && trip.effectiveGeometry.isNotBlank()) {
                        PolylineUtils.decodeGeometry(trip.effectiveGeometry)
                    } else {
                        emptyList()
                    }
                    Log.d(
                        "RideSafeDebug",
                        "[MapViewModel] Loaded tripInfo: start='${trip?.startName}', dest='${trip?.destName}', points=${points.size}, isTripPlanned=${trip?.isTripPlanned}"
                    )
                    _uiState.update {
                        it.copy(
                            tripInfo = trip,
                            routePoints = points
                        )
                    }
                }
        }
    }


    private fun updateRidersWithLocalLocation() {
        val myId = _uiState.value.currentRiderId
        val myName = _uiState.value.currentRiderName
        val myStatus = _uiState.value.myStatus

        // Find current user in latest Firebase list or fallback to local sensor location
        val firebaseMe = latestFirebaseRiders.find { it.id == myId }
        val effectiveMe = if (localCurrentLocation != null && localCurrentLocation?.lat != 0.0) {
            // Keep local GPS coordinates if they are valid, but keep status from Firebase if updated
            localCurrentLocation!!.copy(
                status = firebaseMe?.status ?: myStatus.name
            )
        } else if (firebaseMe != null && firebaseMe.lat != 0.0) {
            firebaseMe
        } else {
            localCurrentLocation ?: Rider(
                id = myId,
                name = myName,
                status = myStatus.name,
                lastUpdated = System.currentTimeMillis()
            )
        }

        // Merge riders: current user first, then all other riders from Firebase
        val otherRiders = latestFirebaseRiders.filter { it.id != myId }
        val allRiders = listOf(effectiveMe) + otherRiders

        Log.d(
            "RideSafeDebug",
            "[MapViewModel] updateRidersWithLocalLocation: effectiveMe='${effectiveMe.name}'(id=${effectiveMe.id}, lat=${effectiveMe.lat}, lng=${effectiveMe.lng}), otherRidersCount=${otherRiders.size}"
        )

        val ridersWithDistance = allRiders.map { rider ->
            val isMe = rider.id == myId
            val hasValidPositions = effectiveMe.lat != 0.0 && effectiveMe.lng != 0.0 &&
                    rider.lat != 0.0 && rider.lng != 0.0

            val distanceMeters = if (isMe || !hasValidPositions) {
                0f
            } else {
                LocationUtils.calculateDistanceMeters(
                    startLat = effectiveMe.lat,
                    startLng = effectiveMe.lng,
                    endLat = rider.lat,
                    endLng = rider.lng
                )
            }

            val formattedDist = if (isMe) {
                "You"
            } else if (rider.lat == 0.0) {
                "Locating..."
            } else {
                LocationUtils.formatDistance(distanceMeters)
            }

            val isAhead = if (isMe || !hasValidPositions) {
                null
            } else {
                LocationUtils.isRiderAhead(
                    myLat = effectiveMe.lat,
                    myLng = effectiveMe.lng,
                    myHeading = lastKnownHeading,
                    targetLat = rider.lat,
                    targetLng = rider.lng
                )
            }

            val relativeText = if (isMe) {
                "${rider.name.ifEmpty { "You" }} (You)"
            } else if (!hasValidPositions) {
                "${rider.name.ifEmpty { "Rider" }} is acquiring GPS..."
            } else {
                LocationUtils.getRelativePositionDescription(
                    riderName = rider.name,
                    distanceMeters = distanceMeters,
                    isAhead = isAhead ?: true
                )
            }

            RiderWithDistance(
                rider = rider,
                distanceMeters = distanceMeters,
                formattedDistance = formattedDist,
                isCurrentUser = isMe,
                relativePositionText = relativeText,
                isAhead = isAhead
            )
        }.sortedWith(
            // Show current user first, then sort other riders by distance
            compareByDescending<RiderWithDistance> { it.isCurrentUser }
                .thenBy { it.distanceMeters }
        )

        _uiState.update {
            it.copy(
                riders = ridersWithDistance,
                myStatus = effectiveMe.riderStatus,
                isLoading = false
            )
        }
    }

    /**
     * Updates the current rider's status (e.g. Refueling, Emergency, Riding).
     * Synchronizes to both Firebase and the Foreground Service notification.
     */
    fun setRiderStatus(status: RiderStatus) {
        val rideCode = _uiState.value.rideCode
        val riderId = _uiState.value.currentRiderId

        _uiState.update { it.copy(myStatus = status, isStatusPickerOpen = false) }

        // Update in Firebase Realtime Database
        if (rideCode.isNotEmpty() && riderId.isNotEmpty()) {
            repository.updateStatus(rideCode, riderId, status)
        }

        // Update Foreground Service notification
        LocationTrackingService.updateStatus(getApplication(), status)
    }

    fun openStatusPicker() {
        _uiState.update { it.copy(isStatusPickerOpen = true) }
    }

    fun closeStatusPicker() {
        _uiState.update { it.copy(isStatusPickerOpen = false) }
    }

    fun openRiderList() {
        _uiState.update { it.copy(isRiderListOpen = true) }
    }

    fun closeRiderList() {
        _uiState.update { it.copy(isRiderListOpen = false) }
    }

    fun selectRider(rider: RiderWithDistance?) {
        _uiState.update { it.copy(selectedRider = rider) }
    }

    /**
     * Leaves the ride session cleanly, stops the foreground service, and clears observers.
     */
    fun leaveRide(onLeaveComplete: () -> Unit) {
        val rideCode = _uiState.value.rideCode
        val riderId = _uiState.value.currentRiderId

        // 1. Stop local location updates
        locationCallback?.let { callback ->
            fusedClient?.removeLocationUpdates(callback)
        }

        // 2. Stop foreground tracking service immediately
        LocationTrackingService.stopTracking(getApplication())

        // 3. Clear real-time observation jobs
        riderObservationJob?.cancel()
        riderObservationJob = null
        tripObservationJob?.cancel()
        tripObservationJob = null

        // 4. Remove rider from Firebase asynchronously
        if (rideCode.isNotEmpty() && riderId.isNotEmpty()) {
            repository.leaveRide(rideCode, riderId)
        }

        // 5. Return to Home screen immediately
        onLeaveComplete()
    }

    override fun onCleared() {
        super.onCleared()
        riderObservationJob?.cancel()
        tripObservationJob?.cancel()
        locationCallback?.let { callback ->
            fusedClient?.removeLocationUpdates(callback)
        }
    }
}

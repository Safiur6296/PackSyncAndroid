package com.ridesafe.app.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.ridesafe.app.data.model.LocalRideSession
import com.ridesafe.app.data.model.LocalRideSessionUi
import com.ridesafe.app.data.model.PlaceSuggestion
import com.ridesafe.app.data.model.RouteResult
import com.ridesafe.app.data.model.TripInfo
import com.ridesafe.app.data.network.OsrmApiClient
import com.ridesafe.app.data.network.PhotonApiClient
import com.ridesafe.app.data.repository.RideRepository
import com.ridesafe.app.data.repository.SessionPreferencesRepository
import com.ridesafe.app.service.LocationTrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val riderName: String = "",
    val joinCode: String = "",
    val isCreatingRide: Boolean = false,
    val isJoiningRide: Boolean = false,
    val rejoiningCode: String? = null,
    val errorMessage: String? = null,
    val sessions: List<LocalRideSessionUi> = emptyList(),
    val isLoadingSessions: Boolean = true,
    // Trip Planning State
    val isTripPlannerOpen: Boolean = false,
    val startLocationQuery: String = "",
    val destLocationQuery: String = "",
    val selectedStartPlace: PlaceSuggestion? = null,
    val selectedDestPlace: PlaceSuggestion? = null,
    val startSuggestions: List<PlaceSuggestion> = emptyList(),
    val destSuggestions: List<PlaceSuggestion> = emptyList(),
    val isLoadingStartSuggestions: Boolean = false,
    val isLoadingDestSuggestions: Boolean = false,
    val isDetectingStartLocation: Boolean = false,
    val isCalculatingRoute: Boolean = false,
    val calculatedRoute: RouteResult? = null,
    val routeError: String? = null
)

class HomeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val sessionPrefs = SessionPreferencesRepository(application)
    private val rideRepository = RideRepository()
    private val photonClient = PhotonApiClient(application)
    private val osrmClient = OsrmApiClient()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var statusCheckJob: Job? = null
    private var startSearchJob: Job? = null
    private var destSearchJob: Job? = null
    private var routeCalculationJob: Job? = null
    private var cachedGpsLocation: Pair<Double, Double>? = null

    init {
        // Pre-fill last used rider name
        viewModelScope.launch {
            val savedName = sessionPrefs.lastRiderNameFlow.first()
            if (savedName.isNotBlank() && _uiState.value.riderName.isBlank()) {
                _uiState.update { it.copy(riderName = savedName) }
            }
        }

        // Observe local sessions from Jetpack DataStore
        viewModelScope.launch {
            sessionPrefs.sessionsFlow.collect { localList ->
                checkAndUpdateSessionsStatus(localList)
            }
        }
    }

    fun onRiderNameChange(name: String) {
        _uiState.update { it.copy(riderName = name, errorMessage = null) }
        viewModelScope.launch {
            sessionPrefs.saveLastRiderName(name)
        }
    }

    fun onJoinCodeChange(code: String) {
        if (code.length <= 8) {
            _uiState.update { it.copy(joinCode = code.uppercase(), errorMessage = null) }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshSessions() {
        viewModelScope.launch {
            val localList = sessionPrefs.sessionsFlow.first()
            checkAndUpdateSessionsStatus(localList)
        }
    }

    private fun checkAndUpdateSessionsStatus(localList: List<LocalRideSession>) {
        statusCheckJob?.cancel()
        statusCheckJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSessions = true) }

            if (localList.isEmpty()) {
                _uiState.update { it.copy(sessions = emptyList(), isLoadingSessions = false) }
                return@launch
            }

            // Check Firebase active status for each session in parallel
            val statusMap = localList.map { session ->
                async {
                    val active = rideRepository.isRideActive(session.rideCode)
                    session.rideCode to active
                }
            }.awaitAll().toMap()

            // Build UI models and sort: Active first, then by timestamp descending
            val uiSessions = localList.map { session ->
                LocalRideSessionUi(
                    session = session,
                    isActive = statusMap[session.rideCode] ?: false,
                    isChecking = false
                )
            }.sortedWith(
                compareByDescending<LocalRideSessionUi> { it.isActive }
                    .thenByDescending { it.timestamp }
            )

            _uiState.update {
                it.copy(
                    sessions = uiSessions,
                    isLoadingSessions = false
                )
            }
        }
    }

    fun openTripPlanner() {
        val name = _uiState.value.riderName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name first.") }
            return
        }
        _uiState.update {
            it.copy(
                isTripPlannerOpen = true,
                errorMessage = null,
                routeError = null
            )
        }
        if (_uiState.value.selectedStartPlace == null) {
            detectAndSetCurrentLocationAsStart()
        }
    }

    fun closeTripPlanner() {
        _uiState.update {
            it.copy(
                isTripPlannerOpen = false,
                startSuggestions = emptyList(),
                destSuggestions = emptyList(),
                routeError = null
            )
        }
    }

    fun detectAndSetCurrentLocationAsStart() {
        _uiState.update { it.copy(isDetectingStartLocation = true) }
        viewModelScope.launch {
            try {
                var lat: Double? = cachedGpsLocation?.first
                var lng: Double? = cachedGpsLocation?.second

                if (lat == null || lng == null) {
                    try {
                        val lastLoc = fusedLocationClient.lastLocation.await()
                        if (lastLoc != null) {
                            lat = lastLoc.latitude
                            lng = lastLoc.longitude
                            cachedGpsLocation = Pair(lat, lng)
                        } else {
                            val currLoc = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                            if (currLoc != null) {
                                lat = currLoc.latitude
                                lng = currLoc.longitude
                                cachedGpsLocation = Pair(lat, lng)
                            }
                        }
                    } catch (e: SecurityException) {
                        android.util.Log.w("HomeViewModel", "GPS permission not granted: ${e.message}")
                    } catch (e: Exception) {
                        android.util.Log.w("HomeViewModel", "Location fetch error: ${e.message}")
                    }
                }

                if (lat != null && lng != null) {
                    val place = photonClient.reverseGeocode(lat, lng) ?: PlaceSuggestion(
                        name = "Current Location",
                        locality = String.format(java.util.Locale.getDefault(), "%.4f, %.4f", lat, lng),
                        fullDisplayName = "Current Location",
                        lat = lat,
                        lng = lng
                    )
                    _uiState.update {
                        it.copy(
                            selectedStartPlace = place,
                            startLocationQuery = place.fullDisplayName,
                            isDetectingStartLocation = false
                        )
                    }
                    if (_uiState.value.selectedDestPlace != null) {
                        calculateRoute()
                    }
                } else {
                    _uiState.update { it.copy(isDetectingStartLocation = false) }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "Error detecting current location: ${e.message}", e)
                _uiState.update { it.copy(isDetectingStartLocation = false) }
            }
        }
    }

    fun onStartQueryChange(query: String) {
        _uiState.update {
            it.copy(
                startLocationQuery = query,
                selectedStartPlace = null,
                calculatedRoute = null,
                routeError = null
            )
        }

        startSearchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(startSuggestions = emptyList(), isLoadingStartSuggestions = false) }
            return
        }

        startSearchJob = viewModelScope.launch {
            delay(300L) // Debounce 300ms
            _uiState.update { it.copy(isLoadingStartSuggestions = true) }
            val results = photonClient.searchPlaces(
                query = query,
                biasLat = cachedGpsLocation?.first,
                biasLng = cachedGpsLocation?.second
            )
            _uiState.update {
                it.copy(
                    startSuggestions = results,
                    isLoadingStartSuggestions = false
                )
            }
        }
    }

    fun selectStartPlace(place: PlaceSuggestion) {
        startSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedStartPlace = place,
                startLocationQuery = place.fullDisplayName,
                startSuggestions = emptyList(),
                isLoadingStartSuggestions = false,
                routeError = null
            )
        }
        if (_uiState.value.selectedDestPlace != null) {
            calculateRoute()
        }
    }

    fun clearStartPlace() {
        startSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedStartPlace = null,
                startLocationQuery = "",
                startSuggestions = emptyList(),
                calculatedRoute = null,
                routeError = null
            )
        }
    }

    fun onDestQueryChange(query: String) {
        _uiState.update {
            it.copy(
                destLocationQuery = query,
                selectedDestPlace = null,
                calculatedRoute = null,
                routeError = null
            )
        }

        destSearchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(destSuggestions = emptyList(), isLoadingDestSuggestions = false) }
            return
        }

        destSearchJob = viewModelScope.launch {
            delay(300L) // Debounce 300ms
            _uiState.update { it.copy(isLoadingDestSuggestions = true) }
            val biasLat = _uiState.value.selectedStartPlace?.lat ?: cachedGpsLocation?.first
            val biasLng = _uiState.value.selectedStartPlace?.lng ?: cachedGpsLocation?.second
            val results = photonClient.searchPlaces(
                query = query,
                biasLat = biasLat,
                biasLng = biasLng
            )
            _uiState.update {
                it.copy(
                    destSuggestions = results,
                    isLoadingDestSuggestions = false
                )
            }
        }
    }

    fun selectDestPlace(place: PlaceSuggestion) {
        destSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDestPlace = place,
                destLocationQuery = place.fullDisplayName,
                destSuggestions = emptyList(),
                isLoadingDestSuggestions = false,
                routeError = null
            )
        }
        if (_uiState.value.selectedStartPlace != null) {
            calculateRoute()
        }
    }

    fun clearDestPlace() {
        destSearchJob?.cancel()
        _uiState.update {
            it.copy(
                selectedDestPlace = null,
                destLocationQuery = "",
                destSuggestions = emptyList(),
                calculatedRoute = null,
                routeError = null
            )
        }
    }

    fun calculateRoute() {
        val start = _uiState.value.selectedStartPlace ?: return
        val dest = _uiState.value.selectedDestPlace ?: return

        routeCalculationJob?.cancel()
        routeCalculationJob = viewModelScope.launch {
            _uiState.update { it.copy(isCalculatingRoute = true, routeError = null) }
            val result = osrmClient.getRoute(
                startLat = start.lat,
                startLng = start.lng,
                destLat = dest.lat,
                destLng = dest.lng
            )
            result.onSuccess { route ->
                _uiState.update {
                    it.copy(
                        calculatedRoute = route,
                        isCalculatingRoute = false,
                        routeError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        calculatedRoute = null,
                        isCalculatingRoute = false,
                        routeError = error.localizedMessage ?: "Failed to calculate route"
                    )
                }
            }
        }
    }

    fun createRideWithPlannedTrip(
        context: Context,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        val start = _uiState.value.selectedStartPlace
        val dest = _uiState.value.selectedDestPlace
        val route = _uiState.value.calculatedRoute

        if (start == null || dest == null) {
            _uiState.update { it.copy(errorMessage = "Please select both start and destination points.") }
            return
        }

        val distanceMeters = route?.distanceMeters ?: 0.0
        val durationSeconds = route?.durationSeconds ?: 0.0
        val polyline = route?.encodedPolyline ?: ""

        val tripInfo = TripInfo(
            startName = start.name,
            startLat = start.lat,
            startLng = start.lng,
            destName = dest.name,
            destLat = dest.lat,
            destLng = dest.lng,
            routeGeometry = polyline,
            encodedPolyline = polyline,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            distanceKm = if (distanceMeters > 0.0) distanceMeters / 1000.0 else 0.0,
            durationMin = if (durationSeconds > 0.0) durationSeconds / 60.0 else 0.0,
            hasPlannedTrip = true,
            createdAt = System.currentTimeMillis()
        )

        createRide(context, tripInfo, onRideJoined)
    }


    fun createRide(
        context: Context,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        createRide(context, null, onRideJoined)
    }

    fun createRide(
        context: Context,
        tripInfo: TripInfo? = null,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        val name = _uiState.value.riderName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name first.") }
            return
        }

        _uiState.update { it.copy(isCreatingRide = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = rideRepository.createRide(name, tripInfo)
                result.onSuccess { (rideCode, riderId) ->
                    android.util.Log.d("RideSafeDebug", "[Service] createRide success: rideCode=$rideCode, riderId=$riderId. Starting LocationTrackingService...")
                    // Save to local DataStore
                    sessionPrefs.saveSession(
                        LocalRideSession(
                            rideCode = rideCode,
                            riderId = riderId,
                            riderName = name,
                            timestamp = System.currentTimeMillis(),
                            isHost = true
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = rideCode,
                        riderId = riderId,
                        riderName = name
                    )
                    android.util.Log.d("RideSafeDebug", "[Service] LocationTrackingService.startTracking called for creator. Navigating to map...")

                    _uiState.update { it.copy(isTripPlannerOpen = false) }
                    onRideJoined(rideCode, riderId, name)
                }.onFailure { error ->
                    android.util.Log.e("RideSafeDebug", "[Service] createRide repository call failed: ${error.message}", error)
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to create ride.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "An unexpected error occurred.") }
            } finally {
                _uiState.update { it.copy(isCreatingRide = false) }
            }
        }
    }

    fun joinRide(
        context: Context,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        val name = _uiState.value.riderName.trim()
        val code = _uiState.value.joinCode.trim().uppercase()

        if (name.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter your name first.") }
            return
        }
        if (code.isEmpty() || code.length != 6) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid 6-character ride code.") }
            return
        }

        _uiState.update { it.copy(isJoiningRide = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val result = rideRepository.joinRide(code, name)
                result.onSuccess { riderId ->
                    // Save to local DataStore
                    sessionPrefs.saveSession(
                        LocalRideSession(
                            rideCode = code,
                            riderId = riderId,
                            riderName = name,
                            timestamp = System.currentTimeMillis(),
                            isHost = false
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = code,
                        riderId = riderId,
                        riderName = name
                    )

                    onRideJoined(code, riderId, name)
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Failed to join ride.")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = e.localizedMessage ?: "An unexpected error occurred.")
                }
            } finally {
                _uiState.update { it.copy(isJoiningRide = false) }
            }
        }
    }

    fun rejoinRide(
        context: Context,
        session: LocalRideSession,
        onRideJoined: (rideCode: String, riderId: String, riderName: String) -> Unit
    ) {
        _uiState.update { it.copy(rejoiningCode = session.rideCode, errorMessage = null) }

        viewModelScope.launch {
            try {
                val displayName = if (_uiState.value.riderName.isNotBlank()) {
                    _uiState.value.riderName.trim()
                } else {
                    session.riderName
                }

                val result = rideRepository.rejoinRide(
                    rideCode = session.rideCode,
                    riderId = session.riderId,
                    riderName = displayName
                )

                result.onSuccess {
                    // Update timestamp in DataStore
                    sessionPrefs.saveSession(
                        session.copy(
                            timestamp = System.currentTimeMillis(),
                            riderName = displayName
                        )
                    )

                    // Start background GPS tracking service
                    LocationTrackingService.startTracking(
                        context = context,
                        rideCode = session.rideCode,
                        riderId = session.riderId,
                        riderName = displayName
                    )

                    onRideJoined(session.rideCode, session.riderId, displayName)
                }.onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.localizedMessage ?: "Failed to rejoin ride.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.localizedMessage ?: "An error occurred while rejoining.") }
            } finally {
                _uiState.update { it.copy(rejoiningCode = null) }
            }
        }
    }

    fun deleteSession(rideCode: String) {
        viewModelScope.launch {
            sessionPrefs.removeSession(rideCode)
        }
    }
}

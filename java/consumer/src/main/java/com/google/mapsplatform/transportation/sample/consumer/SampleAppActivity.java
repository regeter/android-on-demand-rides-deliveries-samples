/* Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mapsplatform.transportation.sample.consumer;

import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.CONFIRMING_TRIP;
import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.INITIALIZED;
import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.JOURNEY_SHARING;
import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.SELECTING_DROPOFF;
import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.SELECTING_PICKUP;
import static com.google.mapsplatform.transportation.sample.consumer.state.AppStates.UNINITIALIZED;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.mapsplatform.transportation.consumer.ConsumerApi;
import com.google.android.libraries.mapsplatform.transportation.consumer.managers.TripModel;
import com.google.android.libraries.mapsplatform.transportation.consumer.managers.TripModelManager;
import com.google.android.libraries.mapsplatform.transportation.consumer.model.TerminalLocation;
import com.google.android.libraries.mapsplatform.transportation.consumer.model.TripInfo;
import com.google.android.libraries.mapsplatform.transportation.consumer.model.TripWaypoint;
import com.google.android.libraries.mapsplatform.transportation.consumer.sessions.JourneySharingSession;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerController;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerGoogleMap;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerGoogleMap.ConsumerMapReadyCallback;
import com.google.android.libraries.mapsplatform.transportation.consumer.view.ConsumerMapView;
import com.google.android.material.snackbar.Snackbar;
import com.google.mapsplatform.transportation.sample.consumer.provider.ProviderUtils;
import com.google.mapsplatform.transportation.sample.consumer.provider.model.TripData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Main activity for the sample application. */
public class SampleAppActivity extends AppCompatActivity
    implements ConsumerViewModel.JourneySharingListener {

  private static final String TAG = "SampleAppActivity";

  private static final long MINUTE_IN_MILLIS = 1000 * 60;

  /** Default zoom of initial map state. */
  private static final int DEFAULT_ZOOM = 16;

  /** Default Map location if failed to receive FLP location. Defaulted to Google MTV. */
  private static final LatLng DEFAULT_MAP_LOCATION = new LatLng(37.423061, -122.084051);

  // Default padding used when moving the camera within the bounds of the trip preview polyline.
  private static final int TRIP_PREVIEW_CAMERA_PADDING = 48;
  // Default color used for the trip preview polyline.
  private static final int TRIP_PREVIEW_POLYLINE_COLOR = Color.rgb(69, 151, 255);

  // The current journey sharing trip status.
  private TextView tripStatusView;
  // Displays the users trip id.
  private TextView tripIdView;
  // Displays the users vehicle id.
  private TextView vehicleIdView;
  // Indicates the Eta to the next waypoint in minutes.
  private TextView etaView;
  // Indicates the remaining distance to the next waypoint.
  private TextView remainingDistanceView;
  // The ridesharing map.
  private ConsumerMapView consumerMapView;
  // Multipurpose button depending on the app state (could be for selecting, drop-off, pickup, or
  // requesting trip)
  private Button actionButton;
  // Secondary button that is either 'Existing Trip' or 'Cancel'.
  private Button existingTripButton;
  // Button that adds a stop in between pickup/drop-off. Only visible when selecting drop-off.
  private Button addStopButton;
  // Dropoff pin in the center of the map.
  private View dropoffPin;
  // Pickup pin in the center of the map.
  private View pickupPin;
  // Prompts if trip to create could be 'Shared'.
  private Switch isSharedTripTypeSwitch;
  // The reference to the map that is being displayed.
  @MonotonicNonNull private ConsumerGoogleMap googleMap;
  // The last location of the device.
  @Nullable private LatLng lastLocation;
  // Marker representing the pickup location.
  private Marker pickupMarker = null;
  // Marker representing the dropoff location.
  private Marker dropoffMarker = null;
  // Array of markers representing intermediate stops during trip preview.
  private final ArrayList<Marker> intermediateStopsMarkers = new ArrayList<>();
  // Array of markers representing waypoints from other trips that the driver is completing before
  // getting to the next current trip waypoint.
  private final ArrayList<Marker> otherTripStopsMarkers = new ArrayList<>();

  // ViewModel for the consumer sample app.
  private ConsumerViewModel consumerViewModel;

  private Polyline tripPreviewPolyline = null;

  @MonotonicNonNull private TripModelManager tripModelManager;
  @MonotonicNonNull private ConsumerController consumerController;

  // Session monitoring the current active trip.
  @Nullable private JourneySharingSession journeySharingSession;

  private CardView tripStatusCardView;
  private View dividerView;
  @Nullable private ColorStateList defaultCardBackgroundColor;
  private float defaultCardElevation;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    initViews();

    // Get the ViewModel.
    consumerViewModel = new ViewModelProvider(this).get(ConsumerViewModel.class);
    consumerViewModel.setJourneySharingListener(this);

    initializeSdk();

    Log.i(TAG, "Consumer SDK version: " + ConsumerApi.getConsumerSDKVersion());
  }

  /**
   * Start the asynchronous call to get the map api and subsequently the consumer controller. The
   * ConsumerGoogleMap is returned, is used to access the ConsumerController. Observers are created
   * that update the UI based on observed trip data.
   */
  private void initializeSdk() {
    showStartupLocation();

    consumerMapView.getConsumerGoogleMapAsync(
        new ConsumerMapReadyCallback() {
          @Override
          public void onConsumerMapReady(ConsumerGoogleMap consumerGoogleMap) {
            // Safe to do so as controller will only be nullified during consumerMap's onDestroy()
            consumerController = requireNonNull(consumerGoogleMap.getConsumerController());

            Task<ConsumerApi> cachedTask = ConsumerApi.getInstance();
            Task<ConsumerApi> consumerApiTask =
                cachedTask != null && cachedTask.isSuccessful()
                    ? cachedTask
                    : ConsumerApi.initialize(
                        SampleAppActivity.this,
                        ProviderUtils.getProviderId(SampleAppActivity.this),
                        new TripAuthTokenFactory(getApplication()));

            consumerApiTask.addOnSuccessListener(
                consumerApi ->
                    tripModelManager = requireNonNull(consumerApi.getTripModelManager()));
            consumerApiTask.addOnFailureListener(
                task -> Log.e(TAG, "ConsumerApi Initialization Error:\n" + task.getMessage()));
            ConsumerMarkerUtils.setCustomMarkers(consumerController, SampleAppActivity.this);
            PolylineStyles.enableTrafficAwarePolyline(consumerController.getConsumerMapStyle());
            setupViewBindings();
            googleMap = consumerGoogleMap;
            centerCameraToLastLocation();
            consumerGoogleMap.setMyLocationEnabled(true);
            setupMapListener();
            tripStatusCardView.post(
                () -> {
                  int padding = tripStatusCardView.getHeight() + dividerView.getHeight();
                  Log.d(TAG, "Setting map bottom padding to: " + padding);
                  consumerGoogleMap.setPadding(0, 0, 0, padding);
                });
          }
        },
        /* fragmentActivity= */ this,
        /* googleMapOptions= */ null);
  }

  @Override
  public TripModel startJourneySharing(TripData tripData) {
    TripModel trip = requireNonNull(tripModelManager).getTripModel(tripData.tripName());
    journeySharingSession = JourneySharingSession.createInstance(trip);
    requireNonNull(consumerController).showSession(journeySharingSession);
    return trip;
  }

  /**
   * In case there's an existing `journeySharingSession` object, it stops it and frees up its
   * reference. This prevents any memory leaks and stops updates from `FleetEngine` into the SDK.
   *
   * <p>Call this method when you no longer need journey sharing, example: Activity `onDestroy`.
   */
  @Override
  public void stopJourneySharing() {
    if (journeySharingSession != null) {
      journeySharingSession.stop();
      journeySharingSession = null;
    }
    requireNonNull(consumerController).hideAllSessions();
  }

  /** Center the camera to the last location of the device. */
  private void centerCameraToLastLocation() {
    if (lastLocation != null) {
      requireNonNull(googleMap)
          .moveCamera(CameraUpdateFactory.newLatLngZoom(lastLocation, DEFAULT_ZOOM));
    }
  }

  @Override
  public void updateCurrentLocation(LatLng latLang) {
    lastLocation = latLang;
  }

  // Permissions are managed by the 'SplashScreenActivity'
  @SuppressLint("MissingPermission")
  private void showStartupLocation() {
    FusedLocationProviderClient fusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(this);
    fusedLocationProviderClient
        .getLastLocation()
        .addOnCompleteListener(
            task -> {
              if (task.isSuccessful() && task.getResult() != null) {
                Location location = task.getResult();
                lastLocation = new LatLng(location.getLatitude(), location.getLongitude());
              } else {
                lastLocation = DEFAULT_MAP_LOCATION;
              }
              if (googleMap != null) {
                centerCameraToLastLocation();
              }
            });
  }

  /** Setting up map idle listeners to select pickup and dropoff locations. */
  private void setupMapListener() {
    requireNonNull(googleMap)
        .setOnCameraIdleListener(
            () -> {
              CameraPosition cameraPosition = requireNonNull(googleMap).getCameraPosition();
              if (cameraPosition == null) {
                return;
              }

              LatLng cameraLocation = cameraPosition.target;
              TerminalLocation terminalLocation = TerminalLocation.create(cameraLocation);

              consumerViewModel.updateLocationPointForState(cameraLocation);
              updateMarkerBasedOnState(terminalLocation);

              // *** Enable the action button when the camera is idle ***
              actionButton.setEnabled(true);
              updateActionButtonAppearance();
            });

    // *** Disable the action button when the camera starts moving ***
    requireNonNull(googleMap)
        .setOnCameraMoveStartedListener(
            reason -> {
              actionButton.setEnabled(false);
              updateActionButtonAppearance();
            });
  }

  private void updateActionButtonAppearance() {
    int backgroundColorResource = actionButton.isEnabled() ? R.color.actionable : R.color.disabled;
    DrawableCompat.setTint(
        actionButton.getBackground(), ContextCompat.getColor(this, backgroundColorResource));
  }

  /**
   * Updates the current marker (depending on app state) to the given location. Ex: when app state
   * is 'SELECTING_PICKUP', it updates the pickupMarker object.
   */
  private void updateMarkerBasedOnState(TerminalLocation location) {
    Integer state = consumerViewModel.getAppState().getValue();
    if (state == null) {
      return;
    }
    if (state == SELECTING_PICKUP) {
      if (pickupMarker == null) {
        pickupMarker =
            requireNonNull(googleMap)
                .addMarker(
                    ConsumerMarkerUtils.getConsumerMarkerOptions(
                            this, ConsumerMarkerType.PICKUP_POINT)
                        .position(location.getLatLng()));
      }

      pickupMarker.setPosition(location.getLatLng());
    } else if (state == SELECTING_DROPOFF) {
      if (dropoffMarker == null) {
        dropoffMarker =
            requireNonNull(googleMap)
                .addMarker(
                    ConsumerMarkerUtils.getConsumerMarkerOptions(
                            this, ConsumerMarkerType.DROPOFF_POINT)
                        .position(location.getLatLng()));
      }

      dropoffMarker.setPosition(location.getLatLng());
    }
  }

  /**
   * When first selecting pickup, the idle handler on map is not initiated since the map was already
   * idle when selecting initial pickup. Select the current location as pickup initially.
   */
  private void resetPickupMarkerAndLocation() {
    if (googleMap == null || googleMap.getCameraPosition() == null) {
      return;
    }

    LatLng cameraLocation = googleMap.getCameraPosition().target;
    consumerViewModel.setPickupLocation(cameraLocation);

    updateMarkerBasedOnState(TerminalLocation.builder(cameraLocation).build());
  }

  /**
   * When first selecting pickup, the idle handler on map is not initiated since the map was already
   * idle when selecting initial pickup. Select the current location as pickup initially.
   */
  private void resetDropoffMarkerAndLocation() {
    if (googleMap == null || googleMap.getCameraPosition() == null) {
      return;
    }

    LatLng cameraLocation = googleMap.getCameraPosition().target;
    consumerViewModel.setDropoffLocation(cameraLocation);

    updateMarkerBasedOnState(TerminalLocation.create(cameraLocation));
  }

  /**
   * Returns a resource identifier which can be used to display trip status when driver is in
   * another trip waypoint.
   */
  private static @StringRes int getOtherRiderStringResourceId(
      @TripWaypoint.WaypointType int waypointType) {
    switch (waypointType) {
      case TripWaypoint.WaypointType.PICKUP:
        return R.string.state_picking_up_other_rider;
      case TripWaypoint.WaypointType.DROPOFF:
        return R.string.state_dropping_off_other_rider;
      case TripWaypoint.WaypointType.INTERMEDIATE_DESTINATION:
        return R.string.state_stopping_for_other_rider;
      default:
        throw new IllegalArgumentException("Invalid waypoint type.");
    }
  }

  /** Display the trip status based on the observed trip status. */
  private void displayTripStatus(int status) {
    if (consumerViewModel.isDriverInOtherTripWaypoint()) {
      setTripStatusTitle(
          getOtherRiderStringResourceId(consumerViewModel.getOtherTripWaypointType()));

      actionButton.setVisibility(View.INVISIBLE);
      isSharedTripTypeSwitch.setVisibility(View.GONE);
      return;
    }

    switch (status) {
      case TripInfo.TripStatus.NEW:
        if (consumerViewModel.isTripMatched()
            && requireNonNull(consumerViewModel.getTripInfo().getValue()).getNextWaypoint()
                != null) {
          setTripStatusTitle(R.string.state_enroute_to_pickup);
        } else {
          setTripStatusTitle(R.string.state_new);
        }
        actionButton.setVisibility(View.INVISIBLE);
        isSharedTripTypeSwitch.setVisibility(View.GONE);
        break;
      case TripInfo.TripStatus.ENROUTE_TO_PICKUP:
        removeAllMarkers();
        setTripStatusTitle(R.string.state_enroute_to_pickup);
        break;
      case TripInfo.TripStatus.ARRIVED_AT_PICKUP:
        setTripStatusTitle(R.string.state_arrived_at_pickup);
        break;
      case TripInfo.TripStatus.ENROUTE_TO_DROPOFF:
        setTripStatusTitle(R.string.state_enroute_to_dropoff);
        break;
      case TripInfo.TripStatus.ARRIVED_AT_INTERMEDIATE_DESTINATION:
        setTripStatusTitle(R.string.state_arrived_at_intermediate_destination);
        break;
      case TripInfo.TripStatus.ENROUTE_TO_INTERMEDIATE_DESTINATION:
        setTripStatusTitle(R.string.state_enroute_to_intermediate_destination);
        break;
      case TripInfo.TripStatus.COMPLETE:
      case TripInfo.TripStatus.CANCELED:
        setTripStatusTitle(R.string.state_end_of_trip);
        hideTripData();
        removeAllMarkers();
        break;
      default:
        break;
    }
  }

  /**
   * This draws markers that need to be displayed *only* during 'JourneySharing`. Example: Waypoints
   * belonging to a previous trip are not displayed by ConsumerSDK, so we manually render them.
   */
  private void drawJourneySharingStateMarkers(List<TripWaypoint> otherTripWaypoints) {
    Marker addedMarker;

    for (TripWaypoint waypoint : otherTripWaypoints) {
      addedMarker =
          requireNonNull(googleMap)
              .addMarker(
                  ConsumerMarkerUtils.getConsumerMarkerOptions(
                          this, ConsumerMarkerType.PREVIOUS_TRIP_PENDING_POINT)
                      .position(waypoint.getTerminalLocation().getLatLng()));

      otherTripStopsMarkers.add(addedMarker);
    }
  }

  /** Clears the list of markers drawn in drawJourneySharingStateMarkers. */
  private void clearJourneySharingStateMarkers() {
    for (Marker marker : otherTripStopsMarkers) {
      marker.remove();
    }

    otherTripStopsMarkers.clear();
  }

  /** Display the reported map state and show trip data when in journey sharing state. */
  private void displayAppState(int state) {
    if (state == INITIALIZED || state == UNINITIALIZED) {
      Log.d(TAG, "displayAppState: Setting card to transparent for initial state: " + state);
      tripStatusCardView.setCardBackgroundColor(Color.TRANSPARENT);
      tripStatusCardView.setCardElevation(0f);
    } else {
      Log.d(TAG, "displayAppState: Setting card to default for state: " + state);
      tripStatusCardView.setCardBackgroundColor(defaultCardBackgroundColor);
      tripStatusCardView.setCardElevation(defaultCardElevation);
    }

    List<Integer> tripFlowStates = Arrays.asList(SELECTING_PICKUP, SELECTING_DROPOFF, CONFIRMING_TRIP);
    if (tripFlowStates.contains(state)) {
      Log.d(TAG, "Configuring Cancel button for state " + state);
      existingTripButton.setText(R.string.button_cancel);
      existingTripButton.setOnClickListener(v -> cancelTripFlow());
    } else {
      Log.d(TAG, "Configuring Existing Trip button for state " + state);
      existingTripButton.setText(R.string.existing_trip_button_label);
      existingTripButton.setOnClickListener(v -> showTripSelectionDialog());
    }

    switch (state) {
      case SELECTING_PICKUP:
        setTripStatusTitle(R.string.state_select_pickup);
        actionButton.setText(R.string.pickup_label);
        existingTripButton.setVisibility(View.VISIBLE);
        pickupPin.setVisibility(View.VISIBLE);
        dropoffPin.setVisibility(View.GONE);
        addStopButton.setVisibility(View.GONE);
        isSharedTripTypeSwitch.setVisibility(View.GONE);
        centerCameraToLastLocation();
        resetPickupMarkerAndLocation();
        break;
      case SELECTING_DROPOFF:
        setTripStatusTitle(R.string.state_select_dropoff);
        actionButton.setText(R.string.dropoff_label);
        existingTripButton.setVisibility(View.VISIBLE);
        pickupPin.setVisibility(View.INVISIBLE);
        dropoffPin.setVisibility(View.VISIBLE);
        addStopButton.setVisibility(View.VISIBLE);
        isSharedTripTypeSwitch.setVisibility(View.GONE);
        break;
      case CONFIRMING_TRIP:
        actionButton.setText(R.string.confirm_trip_label);
        existingTripButton.setVisibility(View.VISIBLE);
        dropoffPin.setVisibility(View.INVISIBLE);
        tripStatusView.setVisibility(View.INVISIBLE);
        addStopButton.setVisibility(View.GONE);
        isSharedTripTypeSwitch.setVisibility(View.VISIBLE);
        drawTripPreviewPolyline();
        centerCameraForTripPreview();
        break;
      case JOURNEY_SHARING:
        existingTripButton.setVisibility(View.GONE);
        tripStatusView.setVisibility(View.VISIBLE);
        pickupPin.setVisibility(View.GONE);
        dropoffPin.setVisibility(View.GONE);
        addStopButton.setVisibility(View.GONE);
        isSharedTripTypeSwitch.setVisibility(View.GONE);
        clearTripPreviewPolyline();
        setTripStatusTitle(R.string.state_enroute_to_pickup);
        break;
      case UNINITIALIZED:
      case INITIALIZED:
        tripStatusView.setVisibility(View.INVISIBLE);
        pickupPin.setVisibility(View.GONE);
        dropoffPin.setVisibility(View.GONE);
        addStopButton.setVisibility(View.GONE);
        isSharedTripTypeSwitch.setVisibility(View.GONE);
        removeAllMarkers();
        clearTripPreviewPolyline();
        hideTripData();
        resetActionButton();
        break;
    }
  }

  private void cancelTripFlow() {
    Log.d(TAG, "Trip flow cancelled by user.");
    consumerViewModel.setState(INITIALIZED);
  }

  /**
   * Callback method fired with the 'Add stop' button (+) has been pressed/touched. It grabs the
   * location of the 'DropoffLocation' container, removes the current 'dropoffMarker' and replaces
   * it with a 'intermediateDestianationMarker'.
   */
  private void onAddStopButtonTapped(View view) {
    requireNonNull(googleMap);

    if (dropoffMarker == null) {
      return;
    }

    consumerViewModel.addIntermediateDestination();

    // Create an intermediate destination in place of the dropoffMarker.
    Marker intermediateStopMarker =
        googleMap.addMarker(
            ConsumerMarkerUtils.getConsumerMarkerOptions(
                    this, ConsumerMarkerType.INTERMEDIATE_DESTINATION_POINT)
                .position(dropoffMarker.getPosition()));

    intermediateStopsMarkers.add(intermediateStopMarker);

    dropoffMarker.remove();
    dropoffMarker = null;

    resetDropoffMarkerAndLocation();
  }

  /** Remove markers on the map used for preview. (pickup, dropoff, intermediate destinations) . */
  private void removeAllMarkers() {
    if (dropoffMarker != null) {
      dropoffMarker.remove();
      dropoffMarker = null;
    }

    if (pickupMarker != null) {
      pickupMarker.remove();
      pickupMarker = null;
    }

    for (Marker marker : intermediateStopsMarkers) {
      marker.remove();
    }

    intermediateStopsMarkers.clear();
  }

  /** Set button to be initial state. */
  private void resetActionButton() {
    actionButton.setText(R.string.request_button_label);
    actionButton.setVisibility(View.VISIBLE);
    Drawable roundedButton = actionButton.getBackground();
    DrawableCompat.setTint(roundedButton, ContextCompat.getColor(this, R.color.actionable));

    existingTripButton.setVisibility(View.VISIBLE);
    Drawable existingTripButtonBg = existingTripButton.getBackground();
    DrawableCompat.setTint(existingTripButtonBg, Color.DKGRAY);
  }

  private void hideTripData() {
    tripIdView.setVisibility(View.INVISIBLE);
    vehicleIdView.setVisibility(View.INVISIBLE);
    etaView.setVisibility(View.INVISIBLE);
    remainingDistanceView.setVisibility(View.INVISIBLE);
  }

  /** Sets the UI state based on the trip state. */
  private void setTripStatusTitle(@StringRes int resourceId) {
    tripStatusView.setText(resourceId);
    tripStatusView.setVisibility(View.VISIBLE);
  }

  /** Updates the displayed trip Id. */
  private void displayTripId(@Nullable String tripId) {
    if (tripId == null) {
      tripIdView.setText("");
      return;
    }
    tripIdView.setText(getResources().getString(R.string.trip_id_label, tripId));
    tripIdView.setVisibility(View.VISIBLE);
  }

  private void displayTripInfo(@Nullable TripInfo tripInfo) {
    if (tripInfo == null || tripInfo.getVehicleId() == null) {
      vehicleIdView.setText("");
      return;
    }
    vehicleIdView.setText(
        getResources().getString(R.string.vehicle_id_label, tripInfo.getVehicleId()));

    displayTripStatus(tripInfo.getCurrentTripStatus());

    int visibility =
        (tripInfo.getCurrentTripStatus() == TripInfo.TripStatus.COMPLETE
                || tripInfo.getCurrentTripStatus() == TripInfo.TripStatus.CANCELED)
            ? View.INVISIBLE
            : View.VISIBLE;
    vehicleIdView.setVisibility(visibility);
  }

  private void onActionButtonTapped(View view) {
    Integer currentState = consumerViewModel.getAppState().getValue();
    if (currentState == null) {
      return;
    }
    switch (currentState) {
      case INITIALIZED:
      case UNINITIALIZED:
        consumerViewModel.setState(SELECTING_PICKUP);
        break;
      case SELECTING_PICKUP:
        consumerViewModel.setState(SELECTING_DROPOFF);
        break;
      case SELECTING_DROPOFF:
        consumerViewModel.setState(CONFIRMING_TRIP);
        break;
      case CONFIRMING_TRIP:
        consumerViewModel.createTrip();
        break;
    }
  }

  /** Display the minutes remaining to the next waypoint. */
  private void displayEta(@Nullable Long etaValue) {
    if (etaValue == null) {
      etaView.setText("");
      return;
    }
    final Date current = new Date();
    final long diff = etaValue - current.getTime();
    final int minutes = (int) (double) (diff / MINUTE_IN_MILLIS);
    etaView.setVisibility(View.VISIBLE);
    if (minutes < 1) {
      etaView.setText(getResources().getString(R.string.eta_within_one_minute));
      return;
    }
    etaView.setText(
        getResources().getQuantityString(R.plurals.eta_format_string, minutes, minutes));
  }

  /** Displays the remaining distance to the next waypoint. */
  private void displayRemainingDistance(@Nullable Integer remainingDistanceMeters) {
    if (remainingDistanceMeters == null || remainingDistanceMeters <= 0) {
      remainingDistanceView.setText("");
      return;
    }
    if (remainingDistanceMeters >= 1000) {
      // Display remaining distance in km.
      remainingDistanceView.setText(
          getString(R.string.distance_format_string_km, remainingDistanceMeters / 1000.0));
    } else {
      // Display remaining distance in m.
      remainingDistanceView.setText(
          getResources()
              .getString(R.string.distance_format_string_meters, remainingDistanceMeters));
    }
    remainingDistanceView.setVisibility(View.VISIBLE);
  }

  private void displayErrorMessage(@StringRes int resourceId) {
    Snackbar.make(tripStatusView, resourceId, Snackbar.LENGTH_SHORT).show();
  }

  private void displayOtherTripMarkers(List<TripWaypoint> waypoints) {
    clearJourneySharingStateMarkers();
    Integer state = consumerViewModel.getAppState().getValue();
    if (state != null && state == JOURNEY_SHARING) {
      drawJourneySharingStateMarkers(waypoints);
    }
  }

  /** Renders a polyline representing all the points contained for the given trip. */
  private void drawTripPreviewPolyline() {
    LatLng pickupLocation = consumerViewModel.getPickupLocation();
    LatLng dropoffLocation = consumerViewModel.getDropoffLocation();

    List<LatLng> intermediateDestinations = consumerViewModel.getIntermediateDestinations();

    if (pickupLocation == null || dropoffLocation == null) {
      return;
    }

    PolylineOptions polylineOptions =
        new PolylineOptions()
            .width(8.0f)
            .color(TRIP_PREVIEW_POLYLINE_COLOR)
            .geodesic(true)
            .add(pickupLocation);

    if (intermediateDestinations != null) {
      for (LatLng destination : intermediateDestinations) {
        polylineOptions.add(destination);
      }
    }

    polylineOptions.add(dropoffLocation);

    tripPreviewPolyline = requireNonNull(googleMap).addPolyline(polylineOptions);
  }

  /** Centers the camera within the bounds of the trip preview polyline. */
  private void centerCameraForTripPreview() {
    LatLng pickupLocation = consumerViewModel.getPickupLocation();
    LatLng dropoffLocation = consumerViewModel.getDropoffLocation();
    List<LatLng> intermediateDestinations = consumerViewModel.getIntermediateDestinations();

    if (pickupLocation == null || dropoffLocation == null) {
      return;
    }

    LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();
    boundsBuilder.include(pickupLocation);
    boundsBuilder.include(dropoffLocation);

    if (intermediateDestinations != null) {
      for (LatLng destination : intermediateDestinations) {
        boundsBuilder.include(destination);
      }
    }

    requireNonNull(googleMap)
        .moveCamera(
            CameraUpdateFactory.newLatLngBounds(
                boundsBuilder.build(), TRIP_PREVIEW_CAMERA_PADDING));
  }

  /** Removes the trip preview polyline after a trip has been confirmed/cancelled. */
  private void clearTripPreviewPolyline() {
    if (tripPreviewPolyline != null) {
      tripPreviewPolyline.remove();
      tripPreviewPolyline = null;
    }
  }

  private void initViews() {
    // Find the UI views for later updates.
    tripStatusView = findViewById(R.id.tripStatus);
    tripIdView = findViewById(R.id.tripId);
    vehicleIdView = findViewById(R.id.vehicleId);
    etaView = findViewById(R.id.eta);
    remainingDistanceView = findViewById(R.id.remainingDistance);
    consumerMapView = findViewById(R.id.consumer_map_view);
    pickupPin = findViewById(R.id.pickup_pin);
    dropoffPin = findViewById(R.id.dropoff_pin);
    actionButton = findViewById(R.id.actionButton);
    actionButton.setOnClickListener(this::onActionButtonTapped);
    addStopButton = findViewById(R.id.addStopButton);
    addStopButton.setOnClickListener(this::onAddStopButtonTapped);
    existingTripButton = findViewById(R.id.existingTripButton);
    tripStatusCardView = findViewById(R.id.trip_status_cardView);
    dividerView = findViewById(R.id.divider);

    defaultCardBackgroundColor = tripStatusCardView.getCardBackgroundColor();
    defaultCardElevation = tripStatusCardView.getCardElevation();

    isSharedTripTypeSwitch = findViewById(R.id.is_shared_trip_type_switch);
    isSharedTripTypeSwitch.setOnCheckedChangeListener(
        (view, isChecked) -> consumerViewModel.setIsSharedTripType(isChecked));

    resetActionButton();
  }

  private void showTripSelectionDialog() {
    Log.d(TAG, "Showing trip selection dialog");
    FrameLayout container = new FrameLayout(this);
    FrameLayout.LayoutParams params =
            new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    int margin = (int) (20 * getResources().getDisplayMetrics().density);
    params.leftMargin = margin;
    params.rightMargin = margin;

    final EditText tripIdInput = new EditText(this);
    tripIdInput.setLayoutParams(params);
    tripIdInput.setHint("Enter Trip ID");
    container.addView(tripIdInput);

    new AlertDialog.Builder(this)
            .setTitle("Connect to Existing Trip")
            .setView(container)
            .setPositiveButton(
                    "Connect",
                    (dialog, which) -> {
                      String tripId = tripIdInput.getText().toString().trim();
                      if (!tripId.isEmpty()) {
                        Log.i(TAG, "User connecting to trip ID: " + tripId);
                        consumerViewModel.loadTrip(tripId);
                      } else {
                        Log.w(TAG, "User tried to connect with an empty trip ID.");
                        Snackbar.make(tripStatusView, "Trip ID cannot be empty.", Snackbar.LENGTH_SHORT)
                                .show();
                      }
                    })
            .setNegativeButton(
                    "Cancel",
                    (dialog, which) -> {
                      Log.d(TAG, "Connect to trip cancelled.");
                      dialog.dismiss();
                    })
            .show();
  }

  private void setupViewBindings() {
    // Start observing trip data.
    consumerViewModel.getTripStatus().observe(this, this::displayTripStatus);
    consumerViewModel.getAppState().observe(this, this::displayAppState);
    consumerViewModel.getTripId().observe(this, this::displayTripId);
    consumerViewModel.getTripInfo().observe(this, this::displayTripInfo);
    consumerViewModel.getNextWaypointEta().observe(this, this::displayEta);
    consumerViewModel.getRemainingDistanceMeters().observe(this, this::displayRemainingDistance);
    consumerViewModel.getErrorMessage().observe(this, this::displayErrorMessage);

    consumerViewModel.getOtherTripWaypoints().observe(this, this::displayOtherTripMarkers);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    /**
     * Apart from 'unregistering' the trip callback, stop journey sharing to cancel updates from
     * 'Fleet Engine'.
     */
    consumerViewModel.unregisterTripCallback();
    stopJourneySharing();
  }
}

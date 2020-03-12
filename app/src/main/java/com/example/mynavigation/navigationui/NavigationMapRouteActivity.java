package com.example.mynavigation.navigationui;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mynavigation.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.core.utils.TextUtils;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.ui.v5.route.OnRouteSelectionChangeListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static android.os.Environment.getExternalStoragePublicDirectory;
import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;

/*
This code is forked and modified from Android Sample in mapbox-navigation-android
https://github.com/mapbox/mapbox-navigation-android/blob/v0.43.0-alpha.1/app/src/main/java/com/mapbox/services/android/navigation/testapp/activity/navigationui/NavigationLauncherActivity.java#L308
latest SDK is 1.0.0, but use 0.42.4 for some compatibility
*/

public class NavigationMapRouteActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener, OnRouteSelectionChangeListener,
        Callback<DirectionsResponse>  {

    private static final int ONE_HUNDRED_MILLISECONDS = 100;
    private static final int CHANGE_SETTING_REQUEST_CODE = 1;
    private static final int CAMERA_ANIMATION_DURATION = 1000;
    private static final int DEFAULT_CAMERA_ZOOM = 16;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private static final int INITIAL_ZOOM = 16;

    private final List<Point> wayPoints = new ArrayList<>();
    private LocationEngine locationEngine;


    @BindView(R.id.mapView)
    MapView mapView;
    @BindView(R.id.routeLoadingProgressBar)
    ProgressBar routeLoading;
    @BindView(R.id.fabRemoveRoute)
    FloatingActionButton fabRemoveRoute;

    // for navigation launcher
    @BindView(R.id.loading)
    ProgressBar loading;
    @BindView(R.id.launch_route_btn)
    Button launchRouteBtn;
    @BindView(R.id.launch_btn_frame)
    FrameLayout launchBtnFrame;


    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private NavigationMapRoute navigationMapRoute;

    private Marker originMarker;
    private Marker destinationMarker;

    private ApplicationInfo appInfo = null;

    private static final String TAG = "MainActivity";
    private Point currentLocation;

    private List<DirectionsRoute> routes = new ArrayList<>();

    private DirectionsRoute route;
    private final NavigationMapRouteLocationCallback callback = new NavigationMapRouteLocationCallback(this);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.customInstructionView);
        super.onCreate(savedInstanceState);

        // 環境変数から、TOKENを取得
        try {
            appInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String apiKey = appInfo.metaData.getString("MAPBOX_ACCESS_TOKEN");

        Mapbox.getInstance(this, apiKey);

        // もしくは、res/values/strings.xmlに記載
        // Mapbox.getInstance(this, getString(R.string.access_token));

        setContentView(R.layout.activity_navigation_map_route);
        ButterKnife.bind(this);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

    }

    // original for my navigation

    @OnClick(R.id.fabRemoveRoute)
    public void onRemoveRouteClick(View fabRemoveRoute) {
        removeRouteAndMarkers();
        fabRemoveRoute.setVisibility(View.INVISIBLE);
    }


    // needed? duplicated in fetchRoute()
    @Override
    public void onResponse(@NonNull Call<DirectionsResponse> call, @NonNull Response<DirectionsResponse> response) {
        if (response.isSuccessful()
                && response.body() != null
                && !response.body().routes().isEmpty()) {
            routes = response.body().routes();
            navigationMapRoute.addRoutes(routes);
            Log.d(TAG, "Route is now finding...");
            Log.d(TAG, String.format("Route is for....%s", routes.get(0)));
            fabRemoveRoute.setVisibility(View.VISIBLE);
        }
    }

    // needed?
    @Override
    public void onFailure(@NonNull Call<DirectionsResponse> call, @NonNull Throwable throwable) {
        Timber.e(throwable);
    }

    // added suppresswarning
    @SuppressWarnings( {"MissingPermission"})
    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        if (locationEngine != null) {
            locationEngine.requestLocationUpdates(buildEngineRequest(), callback, null);
        }

    }

    // in launcher, if-else is not used.
    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        if (navigationMapRoute != null) {
            navigationMapRoute.onStart();
        }
    }

    // in launcher, if-else is not used.
    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
        if (navigationMapRoute != null) {
            navigationMapRoute.onStop();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        if (locationEngine != null) {
            locationEngine.removeLocationUpdates(callback);
        }

    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }


// BEGIN: implementation for mynavigation

    @SuppressWarnings("MissingPermission")
    private void initializeLocationComponent(MapboxMap mapboxMap) {

        // PermissionManagerの追加

        if (PermissionsManager.areLocationPermissionsGranted(this)) {

        LocationComponent locationComponent = mapboxMap.getLocationComponent();
        locationComponent.activateLocationComponent(this, mapboxMap.getStyle());
        locationComponent.setLocationComponentEnabled(true);
        locationComponent.setRenderMode(RenderMode.COMPASS);
        locationComponent.setCameraMode(CameraMode.TRACKING);
        locationComponent.zoomWhileTracking(10d);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }


    // PermissionManagerの設定

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    initializeLocationComponent(mapboxMap);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }


    private void removeRouteAndMarkers() {
        mapboxMap.removeMarker(originMarker);
        originMarker = null;
        mapboxMap.removeMarker(destinationMarker);
        destinationMarker = null;
        navigationMapRoute.removeRoute();
    }

    public void findRoute(Point origin, Point destination) {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(currentLocation)
                .profile(getRouteProfileFromSharedPreferences())    // added for getting profile()
                .destination(destination)
                .addWaypoint(origin)
                .alternatives(true)
                .build()
                .getRoute(this);

        launchRouteBtn.setEnabled(true);

    }

// END: implementation for mynavigation

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    // for launcher button
    @OnClick(R.id.launch_route_btn)
    public void onRouteLaunchClick() {

        Log.d(TAG, "Route is now finding...");
        Log.d(TAG, String.format("Route is for....%s", routes.get(0)));

        launchNavigationWithRoute();
    }

    // modified from my navigation


    @Override
    public void onMapReady(@NotNull MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(Style.MAPBOX_STREETS, style -> {

//        mapboxMap.setStyle(new Style.Builder().fromUri(getString(R.string.style_uri)), style -> {

            initializeLocationComponent(mapboxMap);
            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap);
            navigationMapRoute.setOnRouteSelectionChangeListener(this);
            Snackbar.make(mapView, "now loading ...", Snackbar.LENGTH_SHORT).show();
            initializeLocationEngine(); // added for launcher

            try {
                // Add the marathon route source to the map
                // Create a GeoJsonSource and use the Mapbox Datasets API to retrieve the GeoJSON data
                // More info about the Datasets API at https://www.mapbox.com/api-documentation/#retrieve-a-dataset
                // More info of GeoJsonSource https://docs.mapbox.com/android/api/map-sdk/8.6.2/com/mapbox/mapboxsdk/style/sources/GeoJsonSource.html#GeoJsonSource-java.lang.String-java.net.URI-

                final GeoJsonSource source = new GeoJsonSource(
                        "test-source", new URI("asset://tokyoguide.geojson"));

                if(style.getSource("test-source") == null) {

                    style.addSource(source);
                    Expression visible = eq(get("start"), literal("true"));

                    CircleLayer layer = new CircleLayer("test-layer", source.getId())
                            .withFilter(visible);
                    style.addLayer(layer);


                    // new MapView.OnDidBecomeIdleListener()  をLambdaに変更

                    mapView.addOnDidBecomeIdleListener(() -> {

                        List<Feature> start_features = source.querySourceFeatures(eq(get("start"), literal("true")));
                        Toast.makeText(NavigationMapRouteActivity.this, String.format("Found %s start point features",
                                start_features.size()), Toast.LENGTH_LONG).show();

                        Geometry start_geometry = start_features.get(0).geometry();
                        Point start_point = (Point) start_geometry;
                        LatLng start_latLng = new LatLng(start_point.latitude(),start_point.longitude());

                        Log.d(TAG, String.format("Feature name is %s", start_features.get(0).getProperty("name")));

                        originMarker = mapboxMap.addMarker(new MarkerOptions().position(start_latLng).icon(IconFactory.getInstance(NavigationMapRouteActivity.this).fromResource(R.drawable.blue_marker)));

                        List<Feature> finish_features = source.querySourceFeatures(eq(get("finish"), literal("true")));
                        Toast.makeText(NavigationMapRouteActivity.this, String.format("Found %s finish point features",
                                finish_features.size()), Toast.LENGTH_SHORT).show();

                        Geometry finish_geometry = finish_features.get(0).geometry();
                        Point finish_point = (Point) finish_geometry;
                        LatLng finish_latLng = new LatLng(finish_point.latitude(),finish_point.longitude());

                        Log.d(TAG, String.format("Feature name is %s", finish_features.get(0).getProperty("name")));

                        destinationMarker = mapboxMap.addMarker(new MarkerOptions().position(finish_latLng).icon(IconFactory.getInstance(NavigationMapRouteActivity.this).fromResource(R.drawable.yellow_marker)));

                        findRoute(start_point, finish_point);


                    });

                }

            } catch (URISyntaxException exception) {
                Timber.d(exception);
            }

        });
    }

    @Override
    public void onNewPrimaryRouteSelected(DirectionsRoute directionsRoute) {
    }

    // add for NavigationLauncherLocationCallback
    void updateCurrentLocation(Point currentLocation) {
        this.currentLocation = currentLocation;
    }

    // for launcher
    @SuppressWarnings( {"MissingPermission"})
    private void initializeLocationEngine() {
        locationEngine = LocationEngineProvider.getBestLocationEngine(getApplicationContext());
        LocationEngineRequest request = buildEngineRequest();
        locationEngine.requestLocationUpdates(request, callback, null);
        locationEngine.getLastLocation(callback);
    }


    // for launcher
    private boolean getShouldSimulateRouteFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(getString(R.string.simulate_route_key), false);
    }

    // for launcher
    private String getRouteProfileFromSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(
                getString(R.string.route_profile_key), DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
        );
    }

    // for launcher
    private String obtainOfflinePath() {
        File offline = getExternalStoragePublicDirectory("Offline");
        return offline.getAbsolutePath();
    }

    // for launcher
    private String retrieveOfflineVersionFromPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(getString(R.string.offline_version_key), "");
    }

    // for launcher
    private void launchNavigationWithRoute() {

        route = routes.get(0);

        if (route != null) {
            Log.d(TAG, "Route is fixing...");
            Log.d(TAG, String.format("Route is now fixing....%s", route));
        }

        if (route == null) {
            Snackbar.make(mapView, R.string.error_route_not_available, Snackbar.LENGTH_SHORT).show();
            return;
        }

        NavigationLauncherOptions.Builder optionsBuilder = NavigationLauncherOptions.builder()
                .shouldSimulateRoute(getShouldSimulateRouteFromSharedPreferences());
        CameraPosition initialPosition = new CameraPosition.Builder()
                .target(new LatLng(currentLocation.latitude(), currentLocation.longitude()))
                .zoom(INITIAL_ZOOM)
                .build();
        optionsBuilder.initialMapCameraPosition(initialPosition);
        optionsBuilder.directionsRoute(route);
        String offlinePath = obtainOfflinePath();
        if (!TextUtils.isEmpty(offlinePath)) {
            optionsBuilder.offlineRoutingTilesPath(offlinePath);
        }
        String offlineVersion = retrieveOfflineVersionFromPreferences();
        if (!offlineVersion.isEmpty()) {
            optionsBuilder.offlineRoutingTilesVersion(offlineVersion);
        }
        // TODO Testing dynamic offline
        /**
         * File downloadDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
         * String databaseFilePath = downloadDirectory + "/" + "kingfarm.db";
         * String offlineStyleUrl = "mapbox://styles/mapbox/navigation-guidance-day-v4";
         * optionsBuilder.offlineMapOptions(new MapOfflineOptions(databaseFilePath, offlineStyleUrl));
         */
        NavigationLauncher.startNavigation(this, optionsBuilder.build());
    }


    // for launcher
    @NonNull
    private LocationEngineRequest buildEngineRequest() {
        return new LocationEngineRequest.Builder(UPDATE_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
                .build();
    }


    // for launcher
    private static class NavigationMapRouteLocationCallback implements LocationEngineCallback<LocationEngineResult> {


        private final WeakReference<NavigationMapRouteActivity> activityWeakReference;

        NavigationMapRouteLocationCallback(NavigationMapRouteActivity activity) {
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(LocationEngineResult result) {
            NavigationMapRouteActivity activity = activityWeakReference.get();

            if (activity != null) {
                Location location = result.getLastLocation();
                if (location == null) {
                    return;
                }
                activity.updateCurrentLocation(Point.fromLngLat(location.getLongitude(), location.getLatitude()));
            }
        }

        @Override
        public void onFailure(@NonNull Exception exception) {
            Timber.e(exception);
        }
    }



}
package com.example.mynavigation.navigationui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.JsonObject;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.example.mynavigation.R;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;

public class NavigationMapRouteActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener,
        MapboxMap.OnMapLongClickListener, Callback<DirectionsResponse>  {

    private static final int ONE_HUNDRED_MILLISECONDS = 100;

    @BindView(R.id.mapView)
    MapView mapView;
    @BindView(R.id.routeLoadingProgressBar)
    ProgressBar routeLoading;
    @BindView(R.id.fabRemoveRoute)
    FloatingActionButton fabRemoveRoute;

    private PermissionsManager permissionsManager;
    private MapboxMap mapboxMap;
    private NavigationMapRoute navigationMapRoute;

    private Marker originMarker;
    private Marker destinationMarker;

    private ApplicationInfo appInfo = null;

    private static final String TAG = "MainActivity";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

    @OnClick(R.id.fabRemoveRoute)
    public void onRemoveRouteClick(View fabRemoveRoute) {
        removeRouteAndMarkers();
        fabRemoveRoute.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;


        mapboxMap.setStyle(new Style.Builder().fromUri(getString(R.string.style_uri)), style -> {
            initializeLocationComponent(mapboxMap);
            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap);
//            mapboxMap.addOnMapLongClickListener(this);
            Snackbar.make(mapView, "Long press to select route", Snackbar.LENGTH_SHORT).show();

            try {
                // Add the marathon route source to the map
                // Create a GeoJsonSource and use the Mapbox Datasets API to retrieve the GeoJSON data
                // More info about the Datasets API at https://www.mapbox.com/api-documentation/#retrieve-a-dataset
                final GeoJsonSource courseRouteGeoJson = new GeoJsonSource(
                        "coursedata", new URI("asset://marathon_route.geojson"));

                if(style.getSource("test-source") == null) {

                    final GeoJsonSource source  = new GeoJsonSource("test-source",

                            FeatureCollection.fromFeatures(new Feature[]{
                                    Feature.fromGeometry(Point.fromLngLat(139.714709,35.677919), featureProperties("start", "true")),
                                    Feature.fromGeometry(Point.fromLngLat(139.689021,35.687315), featureProperties("start", "false")),
                                    Feature.fromGeometry(Point.fromLngLat(139.700354,35.667577), featureProperties("start", "false")),
                                    Feature.fromGeometry(Point.fromLngLat(139.691151,35.686705), featureProperties("start", "false")),
                                    Feature.fromGeometry(Point.fromLngLat(139.692602,35.692551), featureProperties("finish", "true")),
                            }));


                    style.addSource(source);
                    Expression visible = eq(get("start"), literal("true"));

                    CircleLayer layer = new CircleLayer("test-layer", source.getId())
                            .withFilter(visible);
                    style.addLayer(layer);

                    mapView.addOnDidBecomeIdleListener(new MapView.OnDidBecomeIdleListener() {

                        @Override
                        public void onDidBecomeIdle() {

                            List<Feature> start_features = source.querySourceFeatures(eq(get("start"), literal("true")));
                            Toast.makeText(NavigationMapRouteActivity.this, String.format("Found %s start point features",
                                    start_features.size()), Toast.LENGTH_SHORT).show();

                            Geometry start_geometry = start_features.get(0).geometry();
                            Point start_point = (Point) start_geometry;
                            LatLng start_latLng = new LatLng(start_point.latitude(),start_point.longitude());

                            originMarker = mapboxMap.addMarker(new MarkerOptions().position(start_latLng).icon(IconFactory.getInstance(NavigationMapRouteActivity.this).fromResource(R.drawable.blue_marker)));

                            List<Feature> finish_features = source.querySourceFeatures(eq(get("finish"), literal("true")));
                            Toast.makeText(NavigationMapRouteActivity.this, String.format("Found %s finish point features",
                                    finish_features.size()), Toast.LENGTH_SHORT).show();

                            Geometry finish_geometry = finish_features.get(0).geometry();
                            Point finish_point = (Point) finish_geometry;
                            LatLng finish_latLng = new LatLng(finish_point.latitude(),finish_point.longitude());

                            destinationMarker = mapboxMap.addMarker(new MarkerOptions().position(finish_latLng));

                            findRoute(start_point, finish_point);
                        }


                    });

                }




//                // Add a click listener
//                mapboxMap.addOnMapClickListener(point -> {
//                    // Query
//
//                    List<Feature> features = source.querySourceFeatures(eq(get("start"), literal("true")));
//                    Toast.makeText(this, String.format("Found %s features",
//                            features.size()), Toast.LENGTH_SHORT).show();
//                    }
//
//                    return false;
//                });



            } catch (URISyntaxException exception) {
                Timber.d(exception);
            }

        });
    }

    @Override
    public boolean onMapLongClick(@NonNull LatLng point) {
//        handleClicked(point);
        return true;
    }

    @Override
    public void onResponse(@NonNull Call<DirectionsResponse> call, @NonNull Response<DirectionsResponse> response) {
        if (response.isSuccessful()
                && response.body() != null
                && !response.body().routes().isEmpty()) {
            List<DirectionsRoute> routes = response.body().routes();
            navigationMapRoute.addRoutes(routes);
            routeLoading.setVisibility(View.INVISIBLE);
            fabRemoveRoute.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onFailure(@NonNull Call<DirectionsResponse> call, @NonNull Throwable throwable) {
        Timber.e(throwable);
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
        if (navigationMapRoute != null) {
            navigationMapRoute.onStart();
        }
    }

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

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

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
                .origin(origin)
                .destination(destination)
                .alternatives(true)
                .build()
                .getRoute(this);
    }

    private JsonObject featureProperties(String key, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(key,value);
        return object;
    }



}
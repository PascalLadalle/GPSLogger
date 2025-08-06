package com.example.geotrackerapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    private WebView webView;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private Sensor orientationSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true); // Activer le localStorage pour le JS

        // Charger notre page HTML locale
        webView.loadUrl("file:///android_asset/app_gps.html");

        // Initialiser les services de localisation et les capteurs
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        // Demander les autorisations
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startGpsUpdates();
        }
    }

    private void startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Demander des mises à jour très fréquentes : toutes les 500ms et à chaque mètre.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, this);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // Un nouveau point GPS est arrivé ! On l'envoie au JavaScript.
        final double lat = location.getLatitude();
        final double lng = location.getLongitude();
        final float accuracy = location.getAccuracy();

        // Utiliser evaluateJavascript pour appeler une fonction JS
        webView.post(() -> {
            webView.evaluateJavascript(String.format("javascript:updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy), null);
        });
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Une nouvelle orientation est arrivée ! On l'envoie au JavaScript.
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            final float heading = event.values[0];
            webView.post(() -> {
                webView.evaluateJavascript(String.format("javascript:updateHeadingFromNative(%f);", heading), null);
            });
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Pas nécessaire pour notre cas
    }

    // Gérer la réponse à la demande d'autorisation
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGpsUpdates();
        }
    }
    
    // S'assurer de démarrer et arrêter les capteurs avec l'activité
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI);
    }
    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }
}
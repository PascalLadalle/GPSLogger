package com.ladalle.gpslogger;

import android.content.Context;
import android.content.Intent;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// On implémente directement les écouteurs ici
public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private WebView webview;
    private WebAppInterface webAppInterface;

    // Variables pour les capteurs
    private LocationManager locationManager;
    private SensorManager sensorManager;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialisation des managers pour les capteurs
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // Configuration de la WebView (inchangée)
        webview = findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.getSettings().setGeolocationEnabled(true);
        webview.getSettings().setDatabaseEnabled(true);
        webview.getSettings().setAllowFileAccess(true);

        webAppInterface = new WebAppInterface(this);
        webview.addJavascriptInterface(webAppInterface, "Android");

        webview.loadUrl("file:///android_asset/app_gps(2).html");

        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
        // Si la permission est déjà accordée, les capteurs seront démarrés dans onResume()
    }

    // Méthode pour démarrer l'écoute des capteurs
    private void startSensorUpdates() {
        // Vérification de la permission (nécessaire car onResume peut être appelé avant la réponse)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Démarrage du GPS
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this);

        // Démarrage du capteur d'orientation
        Sensor rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    // Méthode pour arrêter l'écoute (économiser la batterie)
    private void stopSensorUpdates() {
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                 // La permission a été accordée, on peut démarrer les capteurs
                 startSensorUpdates();
            } else {
                Toast.makeText(this, "La permission de localisation est nécessaire pour utiliser l'application.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // On démarre les capteurs quand l'activité redevient visible
        startSensorUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // On arrête les capteurs quand l'activité n'est plus visible pour économiser la batterie
        stopSensorUpdates();
    }


    // --- MÉTHODES DES ÉCOUTEURS (LISTENERS) ---

    @Override
    public void onLocationChanged(@NonNull Location location) {
        // La position a changé, on envoie les données au JavaScript
        webview.evaluateJavascript("javascript:updatePositionFromNative(" + location.getLatitude() + "," + location.getLongitude() + "," + location.getAccuracy() + ")", null);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // L'orientation a changé
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            float heading = (float) Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientationAngles)[0]);

            // On envoie le cap (heading) au JavaScript
            webview.evaluateJavascript("javascript:updateHeadingFromNative(" + heading + ")", null);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Non utilisé ici
    }

    // Méthodes requises par LocationListener
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(@NonNull String provider) {}

    @Override
    public void onProviderDisabled(@NonNull String provider) {}
}

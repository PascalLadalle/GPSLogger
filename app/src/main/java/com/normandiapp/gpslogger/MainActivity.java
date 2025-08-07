package com.normandiapp.gpslogger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
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
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener, SensorEventListener {

    private WebView webView;
    private LocationManager locationManager;
    private SensorManager sensorManager;
    
    // --- MODIFICATION 1/3 : Remplacement du capteur obsolète ---
    private Sensor rotationVectorSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientationAngles = new float[3];
    // --- FIN MODIFICATION ---

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.loadUrl("file:///android_asset/app_gps.html");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        // --- MODIFICATION 2/3 : On récupère le capteur moderne ---
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        // --- FIN MODIFICATION ---

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startGpsUpdates();
        }
    }

    // Le code du partage de fichier est le vôtre, il est correct et ne change pas.
    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }
        @JavascriptInterface
        public void shareKml(String kmlContent, String fileName) {
            try {
                File cachePath = new File(mContext.getCacheDir(), "kml_files");
                cachePath.mkdirs();
                File file = new File(cachePath, fileName);
                FileOutputStream stream = new FileOutputStream(file);
                stream.write(kmlContent.getBytes());
                stream.close();
                Uri contentUri = FileProvider.getUriForFile(mContext, "com.normandiapp.gpslogger.provider", file);
                if (contentUri != null) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setType("application/vnd.google-earth.kml+xml");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    startActivity(Intent.createChooser(shareIntent, "Compartir KML"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startGpsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 1, this);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        final double lat = location.getLatitude();
        final double lng = location.getLongitude();
        final float accuracy = location.getAccuracy();
        String script = String.format(Locale.US, "javascript:updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy);
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // --- MODIFICATION 3/3 : Logique pour interpréter le capteur moderne ---
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // Convertir le vecteur de rotation en matrice
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            // Obtenir l'orientation (azimut/heading) en degrés
            final float heading = (float) Math.toDegrees(SensorManager.getOrientation(rotationMatrix, orientationAngles)[0]);
            
            String script = String.format(Locale.US, "javascript:updateHeadingFromNative(%f);", heading);
            webView.post(() -> webView.evaluateJavascript(script, null));
        }
        // --- FIN MODIFICATION ---
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGpsUpdates();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // On enregistre l'écouteur pour le nouveau capteur
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }
}

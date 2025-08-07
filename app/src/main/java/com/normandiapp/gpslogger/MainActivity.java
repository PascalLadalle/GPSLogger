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
    private Sensor orientationSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setWebViewClient(new WebViewClient());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // --- PONT JAVASCRIPT ---
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");
        webView.loadUrl("file:///android_asset/app_gps.html");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        
        // Utilisation du capteur d'orientation original
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            startGpsUpdates();
        }
    }

    // Classe interne qui contient les méthodes accessibles depuis le JavaScript
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

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
        // Utilisation de Locale.US pour s'assurer que le point décimal est un "."
        String script = String.format(Locale.US, "javascript:updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy);
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // La logique pour la rotation du curseur est déjà ici et correcte.
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            final float heading = event.values[0];
            String script = String.format(Locale.US, "javascript:updateHeadingFromNative(%f);", heading);
            webView.post(() -> webView.evaluateJavascript(script, null));
        }
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
        // On enregistre l'écouteur pour l'orientation ici
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // On arrête les écouteurs pour économiser la batterie
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }
}

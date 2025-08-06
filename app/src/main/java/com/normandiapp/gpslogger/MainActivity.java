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
        // On attache notre classe Java "WebAppInterface" au JavaScript.
        // Dans le JS, on pourra appeler ses méthodes via l'objet "Android".
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        webView.loadUrl("file:///android_asset/app_gps.html");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
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

        // Cette annotation est obligatoire pour que la méthode soit visible par le JS
        @JavascriptInterface
        public void shareKml(String kmlContent, String fileName) {
            try {
                // Créer un fichier temporaire dans le dossier cache de l'application
                File cachePath = new File(mContext.getCacheDir(), "kml_files");
                cachePath.mkdirs(); // Crée le dossier s'il n'existe pas
                File file = new File(cachePath, fileName);
                FileOutputStream stream = new FileOutputStream(file);
                stream.write(kmlContent.getBytes());
                stream.close();

                // Obtenir une URI sécurisée pour ce fichier via le FileProvider
                Uri contentUri = FileProvider.getUriForFile(mContext, "com.normandiapp.gpslogger.provider", file);

                if (contentUri != null) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Permission de lecture temporaire
                    shareIntent.setType("application/vnd.google-earth.kml+xml");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);

                    // Lancer la boîte de dialogue de partage d'Android
                    startActivity(Intent.createChooser(shareIntent, "Compartir KML"));
                }
            } catch (Exception e) {
                // En cas d'erreur, on l'affiche dans les logs pour le débogage
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
        webView.post(() -> webView.evaluateJavascript(String.format("javascript:updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy), null));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
            final float heading = event.values[0];
            webView.post(() -> webView.evaluateJavascript(String.format("javascript:updateHeadingFromNative(%f);", heading), null));
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
        sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_UI);
    }
    @Override
    protected void onPause() {
        super.onPause();
        locationManager.removeUpdates(this);
        sensorManager.unregisterListener(this);
    }
}
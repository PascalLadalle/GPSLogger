package com.ladalle.gpslogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private WebView webview;
    private BroadcastReceiver locationUpdateReceiver;
    private BroadcastReceiver headingUpdateReceiver; // <-- Nouveau receiver pour le cap
    private WebAppInterface webAppInterface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webview = findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true); // Activer le DOM Storage pour localStorage
        webview.getSettings().setGeolocationEnabled(true);
        webview.getSettings().setDatabaseEnabled(true);
        webview.getSettings().setAllowFileAccess(true);

        webAppInterface = new WebAppInterface(this);
        webview.addJavascriptInterface(webAppInterface, "Android");

        webview.loadUrl("file:///android_asset/app_gps(2).html");

        locationUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(GPSLoggerService.LOCATION_UPDATE_ACTION)) {
                    Location location = intent.getParcelableExtra(GPSLoggerService.EXTRA_LOCATION);
                    if (location != null) {
                        webview.evaluateJavascript("javascript:updatePositionFromNative(" + location.getLatitude() + "," + location.getLongitude() + "," + location.getAccuracy() + ")", null);
                    }
                }
            }
        };

        // --- Définition du nouveau receiver pour le cap ---
        headingUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(GPSLoggerService.HEADING_UPDATE_ACTION)) {
                    float heading = intent.getFloatExtra(GPSLoggerService.EXTRA_HEADING, 0);
                    webview.evaluateJavascript("javascript:updateHeadingFromNative(" + heading + ")", null);
                }
            }
        };
        // --- Fin de la définition ---

        checkLocationPermission();
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            startGpsService();
        }
    }

    private void startGpsService() {
        Intent serviceIntent = new Intent(this, GPSLoggerService.class);
        startService(serviceIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGpsService();
            } else {
                Toast.makeText(this, "La permission de localisation est nécessaire pour utiliser l'application.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(locationUpdateReceiver, new IntentFilter(GPSLoggerService.LOCATION_UPDATE_ACTION));
        registerReceiver(headingUpdateReceiver, new IntentFilter(GPSLoggerService.HEADING_UPDATE_ACTION)); // <-- Enregistrement du receiver
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(locationUpdateReceiver);
        unregisterReceiver(headingUpdateReceiver); // <-- Désenregistrement du receiver
    }

    @Override
    protected void onDestroy() {
        // Optionnel : arrêter le service si l'application est complètement détruite
        // Intent serviceIntent = new Intent(this, GPSLoggerService.class);
        // stopService(serviceIntent);
        super.onDestroy();
    }
}

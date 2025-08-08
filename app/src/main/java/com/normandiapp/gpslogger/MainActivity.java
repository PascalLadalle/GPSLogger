package com.normandiapp.gpslogger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private WebView webview;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private BroadcastReceiver serviceLocationReceiver;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webview = findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.addJavascriptInterface(new WebAppInterface(this), "Android");
        webview.loadUrl("file:///android_asset/app_gps.html");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        setupLocationListener();
        setupServiceReceiver();

        requestLocationPermissions();
    }

    private void setupLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                sendLocationToWebView(location);
            }
        };
    }

    private void setupServiceReceiver() {
        serviceLocationReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && LocationService.ACTION_LOCATION_BROADCAST.equals(intent.getAction())) {
                    double lat = intent.getDoubleExtra(LocationService.EXTRA_LATITUDE, 0);
                    double lng = intent.getDoubleExtra(LocationService.EXTRA_LONGITUDE, 0);
                    float accuracy = intent.getFloatExtra(LocationService.EXTRA_ACCURACY, 0);
                    float heading = intent.getFloatExtra(LocationService.EXTRA_HEADING, 0);

                    String script = String.format("window.updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy);
                    webview.post(() -> webview.evaluateJavascript(script, null));

                    String headingScript = String.format("window.updateHeadingFromNative(%f);", heading);
                    webview.post(() -> webview.evaluateJavascript(headingScript, null));
                }
            }
        };
    }

    private void sendLocationToWebView(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 0;
        float heading = location.hasBearing() ? location.getBearing() : 0;

        String script = String.format("window.updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy);
        webview.post(() -> webview.evaluateJavascript(script, null));

        String headingScript = String.format("window.updateHeadingFromNative(%f);", heading);
        webview.post(() -> webview.evaluateJavascript(headingScript, null));
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (!isRecording) {
            startPassiveLocationUpdates();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceLocationReceiver, new IntentFilter(LocationService.ACTION_LOCATION_BROADCAST));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isRecording) {
            stopPassiveLocationUpdates();
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceLocationReceiver);
    }

    private void startPassiveLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, locationListener);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopPassiveLocationUpdates() {
        locationManager.removeUpdates(locationListener);
    }

    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }
    
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void shareKml(String kmlContent, String fileName) {
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(path, fileName);

            try {
                if (!path.exists()) {
                    path.mkdirs();
                }
                FileWriter writer = new FileWriter(file);
                writer.append(kmlContent);
                writer.flush();
                writer.close();

                // --- RETOUR À L'ANCIENNE MÉTHODE COMME DEMANDÉ ---
                Uri fileUri = Uri.fromFile(file);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/vnd.google-earth.kml+xml");
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                
                startActivity(Intent.createChooser(shareIntent, "Compartir archivo KML"));

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(mContext, "Error al guardar el archivo KML.", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void startNativeUpdates() {
            isRecording = true;
            stopPassiveLocationUpdates();
            
            Intent serviceIntent = new Intent(mContext, LocationService.class);
            ContextCompat.startForegroundService(mContext, serviceIntent);
        }

        @JavascriptInterface
        public void stopNativeUpdates() {
            isRecording = false;
            
            Intent serviceIntent = new Intent(mContext, LocationService.class);
            mContext.stopService(serviceIntent);

            runOnUiThread(this::startPassiveLocationUpdates);
        }
    }
}

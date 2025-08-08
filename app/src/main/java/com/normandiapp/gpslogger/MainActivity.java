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
    private BroadcastReceiver locationReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webview = findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.addJavascriptInterface(new WebAppInterface(this), "Android");
        webview.loadUrl("file:///android_asset/app_gps.html");

        setupReceiver();
        requestLocationPermissions();
        
        startLocationService();
    }
    
    @Override
    protected void onDestroy() {
        stopLocationService();
        super.onDestroy();
    }

    private void setupReceiver() {
        locationReceiver = new BroadcastReceiver() {
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

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, new IntentFilter(LocationService.ACTION_LOCATION_BROADCAST));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver);
    }

    private void startLocationService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Intent serviceIntent = new Intent(this, LocationService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        }
    }

    private void stopLocationService() {
        Intent serviceIntent = new Intent(this, LocationService.class);
        stopService(serviceIntent);
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
    }
}

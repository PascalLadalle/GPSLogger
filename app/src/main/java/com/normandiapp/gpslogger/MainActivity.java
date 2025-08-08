package com.normandiapp.gpslogger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
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
            try {
                // --- DÉBUT DE LA CORRECTION FINALE ---
                // 1. Obtenir le chemin du cache interne, comme spécifié dans votre file_paths.xml
                File cachePath = new File(mContext.getCacheDir(), "kml_files");
                
                // 2. Créer le sous-dossier s'il n'existe pas
                if (!cachePath.exists()) {
                    cachePath.mkdirs();
                }

                // 3. Créer le fichier à l'intérieur de ce chemin de cache
                File file = new File(cachePath, fileName);
                // --- FIN DE LA CORRECTION FINALE ---

                FileOutputStream stream = new FileOutputStream(file);
                stream.write(kmlContent.getBytes());
                stream.close();

                Uri contentUri = FileProvider.getUriForFile(mContext, "com.normandiapp.gpslogger.provider", file);

                if (contentUri != null) {
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    
                    startActivity(Intent.createChooser(shareIntent, "Compartir archivo KML"));
                }

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(mContext, "Error al guardar el archivo KML.", Toast.LENGTH_SHORT).show());
            } catch (IllegalArgumentException e) {
                 e.printStackTrace();
                 runOnUiThread(() -> Toast.makeText(mContext, "Error de configuración interna.", Toast.LENGTH_LONG).show());
            }
        }
    }
}

package com.normandiapp.gpslogger;

import androidx.annotation.NonNull;
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
import android.os.Build; // Ajout nécessaire
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList; // Ajout nécessaire

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

                    String script = String.format(java.util.Locale.US, "window.updatePositionFromNative(%f, %f, %f);", lat, lng, accuracy);
                    webview.post(() -> webview.evaluateJavascript(script, null));

                    String headingScript = String.format(java.util.Locale.US, "window.updateHeadingFromNative(%f);", heading);
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
    
    // --- MODIFICATION CLÉ ---
    // Cette méthode demande maintenant la permission de notifier sur les versions récentes d'Android.
    private void requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationService();
        } else {
            ArrayList<String> permissionsToRequest = new ArrayList<>();
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

            // Sur Android 13 (API 33) et plus, on doit demander la permission de notifier.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }

            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // On vérifie spécifiquement si la permission de localisation a été accordée pour démarrer le service.
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationService();
            } else {
                Toast.makeText(this, "La autorización de GPS es necesaria para el funcionamiento de la aplicación.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    public class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void shareKml(String kmlContent, String fileName) {
            try {
                File cachePath = new File(mContext.getCacheDir(), "kml_files");
                if (!cachePath.exists()) {
                    cachePath.mkdirs();
                }
                File file = new File(cachePath, fileName);
                
                FileOutputStream stream = new FileOutputStream(file);
                stream.write(kmlContent.getBytes());
                stream.close();

                String authority = mContext.getPackageName() + ".provider";
                Uri contentUri = FileProvider.getUriForFile(mContext, authority, file);

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
                runOnUiThread(() -> Toast.makeText(mContext, "Error de configuración del FileProvider.", Toast.LENGTH_LONG).show());
            }
        }
    }
}

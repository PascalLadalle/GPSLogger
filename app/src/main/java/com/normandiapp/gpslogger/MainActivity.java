package com.normandiapp.gpslogger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

// Imports nécessaires pour FusedLocationProviderClient
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayList;

// Note: Il n'est PAS nécessaire d'importer Point ou Track car ils sont dans le même package.
// public class MainActivity extends AppCompatActivity implements LocationListener { <-- L'implémentation a été supprimée

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Nouvelles variables pour FusedLocationProviderClient
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;

    // Variables d'état de l'application (inchangées)
    private Track currentTrack = null;
    private Boolean isLogging = false;

    // Vues de l'interface utilisateur (inchangées)
    private Button button;
    private TextView latitudeLabel;
    private TextView longitudeLabel;
    private ListView trackList;
    private ArrayAdapter<String> trackAdapter;
    private ArrayList<String> trackTitles;

    // Constantes (inchangées)
    private static final long UPDATE_INTERVAL = 3000;
    private static final long MINIMUM_DISTANCE = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button);
        latitudeLabel = findViewById(R.id.latitude_label);
        longitudeLabel = findViewById(R.id.longitude_label);
        trackList = findViewById(R.id.track_list);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLogging) {
                    stopLogging();
                } else {
                    startLogging();
                }
            }
        });

        requestPermissions();
        updateTrackList();
    }

    private void requestPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permissions granted");
            } else {
                Toast.makeText(this, "Se requieren permisos para usar esta aplicación", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startLogging() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de ubicación no concedido", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }

        currentTrack = new Track();
        isLogging = true;
        button.setText(R.string.stop_logging);
        
        // Création de la LocationRequest (syntaxe corrigée)
        LocationRequest locationRequest = new LocationRequest.Builder(UPDATE_INTERVAL)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateDistanceMeters(MINIMUM_DISTANCE)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "Location Changed: " + location.getLatitude() + ", " + location.getLongitude());

                        // Logique originale de création de point (fonctionne car le conflit est résolu)
                        Point point = new Point();
                        point.setLatitude(location.getLatitude());
                        point.setLongitude(location.getLongitude());
                        point.setAltitude(location.getAltitude());
                        point.setSpeed(location.getSpeed());
                        point.setTime(location.getTime());

                        currentTrack.addPoint(point);

                        latitudeLabel.setText(String.format("Latitud: %s", point.getLatitude()));
                        longitudeLabel.setText(String.format("Longitud: %s", point.getLongitude()));
                    }
                }
            }
        };

        // Démarrage des mises à jour
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void stopLogging() {
        if (isLogging) {
            isLogging = false;
            button.setText(R.string.start_logging);

            if (fusedLocationProviderClient != null && locationCallback != null) {
                fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            }

            if (currentTrack != null && currentTrack.getPoints().size() > 0) {
                currentTrack.saveToFile(this);
                updateTrackList();
            }
            currentTrack = null;
        }
    }

    private void updateTrackList() {
        // Logique originale d'affichage des traces (fonctionne car le conflit est résolu)
        trackTitles = Track.getTrackTitles(this);
        trackAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, trackTitles);
        trackList.setAdapter(trackAdapter);
    }
}

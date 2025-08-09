package com.normandiapp.gpslogger;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class Track implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final String TAG = "Track";

    private ArrayList<Point> points;
    private long startTime;

    public Track() {
        this.points = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
    }

    public void addPoint(Point point) {
        this.points.add(point);
    }

    public ArrayList<Point> getPoints() {
        return points;
    }

    public void saveToFile(Context context) {
        if (points.isEmpty()) {
            Log.w(TAG, "La ruta no tiene puntos, no se guardará.");
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        // Préfixe de fichier traduit en "ruta" (route/trace)
        String fileName = "ruta_" + sdf.format(new Date(this.startTime)) + ".ser";

        File file = new File(context.getFilesDir(), fileName);

        try (FileOutputStream fos = new FileOutputStream(file);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(this);
            Log.d(TAG, "Ruta guardada correctamente en " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error al guardar la ruta en el archivo", e);
        }
    }

    public static ArrayList<String> getTrackTitles(Context context) {
        ArrayList<String> titles = new ArrayList<>();
        File directory = context.getFilesDir();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                // Vérification avec le préfixe traduit "ruta_"
                if (file.getName().startsWith("ruta_") && file.getName().endsWith(".ser")) {
                    titles.add(file.getName());
                }
            }
        }
        return titles;
    }
}

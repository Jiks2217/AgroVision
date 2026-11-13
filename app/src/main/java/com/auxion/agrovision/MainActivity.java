package com.auxion.agrovision;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private PlantClassifier classifier;
    private TextView tvResult;
    private ImageView imagePreview;
    private ProgressBar progressBar;
    private Button btnAnalyze;
    
    private Bitmap currentBitmap;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadImagePreview(uri);
            });

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraImageUri != null) loadImagePreview(cameraImageUri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tvResult);
        imagePreview = findViewById(R.id.imagePreview);
        progressBar = findViewById(R.id.progressBar);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        Button btnTakePhoto = findViewById(R.id.btnTakePhoto);
        Button btnSelect = findViewById(R.id.btnSelectImage);

        try {
            classifier = new PlantClassifier(this);
        } catch (IOException e) {
            tvResult.setText("Error loading model: " + e.getMessage());
        }

        btnTakePhoto.setOnClickListener(v -> {
            try {
                File photoFile = File.createTempFile("leaf_", ".jpg", getCacheDir());
                cameraImageUri = FileProvider.getUriForFile(this,
                        "com.auxion.agrovision.fileprovider", photoFile);
                takePictureLauncher.launch(cameraImageUri);
            } catch (IOException e) {
                Toast.makeText(this, "Failed to create temp file", Toast.LENGTH_SHORT).show();
            }
        });

        btnSelect.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnAnalyze.setOnClickListener(v -> analyzeImage());
    }

    private void loadImagePreview(Uri uri) {
        try {
            currentBitmap = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(getContentResolver(), uri));
            imagePreview.setImageBitmap(currentBitmap);
            tvResult.setText("Image ready. Tap 'Analyze Leaf' to scan.");
            btnAnalyze.setEnabled(true);
        } catch (IOException e) {
            tvResult.setText("Error loading image: " + e.getMessage());
        }
    }

    private void analyzeImage() {
        if (classifier == null || currentBitmap == null) return;

        progressBar.setVisibility(View.VISIBLE);
        tvResult.setText("Analyzing leaf...");
        btnAnalyze.setEnabled(false);

        new Thread(() -> {
            List<PlantClassifier.Prediction> results = classifier.classify(currentBitmap);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnAnalyze.setEnabled(true);
                displayResults(results);
            });
        }).start();
    }

    private void displayResults(List<PlantClassifier.Prediction> results) {
        if (results == null || results.isEmpty()) {
            tvResult.setText("No results found.");
            return;
        }
        StringBuilder sb = new StringBuilder("Diagnosis:\n\n");
        for (int i = 0; i < results.size(); i++) {
            PlantClassifier.Prediction p = results.get(i);
            sb.append(String.format("%d. %s\n   Confidence: %.1f%%\n\n", (i+1), p.label, p.score * 100));
        }
        tvResult.setText(sb.toString().trim());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) classifier.close();
    }
}

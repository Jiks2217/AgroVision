package com.auxion.agrovision;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private PlantClassifier classifier;
    private TextView tvResult;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) processImage(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tvResult);
        Button btnSelect = findViewById(R.id.btnSelectImage);

        try {
            classifier = new PlantClassifier(this);
        } catch (IOException e) {
            tvResult.setText("Error loading model: " + e.getMessage());
        }

        btnSelect.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void processImage(Uri imageUri) {
        if (classifier == null) return;
        try {
            Bitmap bitmap = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(getContentResolver(), imageUri));
            List<PlantClassifier.Prediction> results = classifier.classify(bitmap);
            displayResults(results);
        } catch (IOException e) {
            tvResult.setText("Error: " + e.getMessage());
        }
    }

    private void displayResults(List<PlantClassifier.Prediction> results) {
        if (results == null || results.isEmpty()) {
            tvResult.setText("No results.");
            return;
        }
        StringBuilder sb = new StringBuilder("Predictions:\n");
        for (PlantClassifier.Prediction p : results) {
            sb.append(String.format("- %s: %.2f%%\n", p.label, p.score * 100));
        }
        tvResult.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) classifier.close();
    }
}

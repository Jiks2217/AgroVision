package com.auxion.agrovision;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class PlantClassifier {
    private static final String TAG = "PlantClassifier";
    private static final String MODEL_FILE  = "plantai_model.tflite";
    private static final String LABELS_FILE = "labels.txt";
    private static final int IMAGE_SIZE  = 224;
    private static final int NUM_CLASSES = 39;
    private static final int TOP_K       = 3;

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD  = {0.229f, 0.224f, 0.225f};

    private Interpreter interpreter;
    private List<String> labels;

    public PlantClassifier(Context context) throws IOException {
        MappedByteBuffer modelBuffer = loadModel(context);
        Interpreter.Options opts = new Interpreter.Options();
        opts.setNumThreads(4);
        interpreter = new Interpreter(modelBuffer, opts);
        labels = loadLabels(context);
        Log.i(TAG, "Model loaded, labels=" + labels.size());
    }

    private MappedByteBuffer loadModel(Context context) throws IOException {
        AssetFileDescriptor fd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(fd.getFileDescriptor());
        FileChannel fc = fis.getChannel();
        return fc.map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    private List<String> loadLabels(Context context) throws IOException {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(context.getAssets().open(LABELS_FILE)))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) out.add(line.trim());
            }
        }
        return out;
    }

    public List<Prediction> classify(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);

        ByteBuffer input = ByteBuffer.allocateDirect(1 * 3 * IMAGE_SIZE * IMAGE_SIZE * 4);
        input.order(ByteOrder.nativeOrder());

        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);

        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i];
                int r = (p >> 16) & 0xFF;
                int g = (p >> 8)  & 0xFF;
                int b =  p        & 0xFF;
                float v;
                if (c == 0)      v = r / 255f;
                else if (c == 1) v = g / 255f;
                else             v = b / 255f;
                v = (v - MEAN[c]) / STD[c];
                input.putFloat(v);
            }
        }
        input.rewind();

        float[][] output = new float[1][NUM_CLASSES];
        interpreter.run(input, output);

        float[] probs = toProbabilities(output[0]);
        return topK(probs, TOP_K);
    }

    private float[] toProbabilities(float[] raw) {
        float sum = 0f;
        boolean inRange = true;
        for (float v : raw) {
            if (v < 0f || v > 1f) { inRange = false; break; }
            sum += v;
        }
        if (inRange && Math.abs(sum - 1f) < 0.1f) return raw;
        return softmax(raw);
    }

    private float[] softmax(float[] logits) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;
        float sum = 0f;
        float[] out = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            out[i] = (float) Math.exp(logits[i] - max);
            sum += out[i];
        }
        for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }

    private List<Prediction> topK(float[] probs, int k) {
        List<Prediction> out = new ArrayList<>();
        boolean[] used = new boolean[probs.length];
        for (int n = 0; n < k; n++) {
            int best = -1;
            float bestScore = -1f;
            for (int i = 0; i < probs.length; i++) {
                if (!used[i] && probs[i] > bestScore) {
                    bestScore = probs[i];
                    best = i;
                }
            }
            if (best == -1) break;
            used[best] = true;
            String label = best < labels.size() ? labels.get(best) : "class_" + best;
            out.add(new Prediction(label, bestScore));
        }
        return out;
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }

    public static class Prediction {
        public final String label;
        public final float score;
        public Prediction(String label, float score) {
            this.label = label;
            this.score = score;
        }
    }
}

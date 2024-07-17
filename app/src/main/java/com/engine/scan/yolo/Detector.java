package com.engine.scan.yolo;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Detector {
    private final Context context;
    private final String modelPath;
    private final String labelPath;

    protected Interpreter interpreter;
    private final List<String> labels = new ArrayList<>();

    private int tensorWidth = 0;
    private int tensorHeight = 0;
    private int numChannel = 0;
    private int numElements = 0;

    private final ImageProcessor imageProcessor;

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.3F;
    private static final float IOU_THRESHOLD = 0.5F;

    // Yolo v8
    public Detector(Context context, String modelPath, String labelPath) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;

        this.imageProcessor = new ImageProcessor.Builder()
                .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
                .add(new CastOp(INPUT_IMAGE_TYPE))
                .build();

        initInterpreter();
    }

    private void initInterpreter() {
        CompatibilityList compatList = new CompatibilityList();

        Interpreter.Options options = new Interpreter.Options();
        if (compatList.isDelegateSupportedOnThisDevice()) {
            options.addDelegate(new GpuDelegate(compatList.getBestOptionsForThisDevice()));
        } else {
            options.setNumThreads(4);
        }

        try {
            AssetManager assetManager = context.getAssets();
            InputStream modelInputStream = assetManager.open(modelPath);
            ByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
            interpreter = new Interpreter(model, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();

            tensorWidth = (inputShape[1] == 3) ? inputShape[2] : inputShape[1];
            tensorHeight = (inputShape[1] == 3) ? inputShape[3] : inputShape[2];

            numChannel = outputShape[1];
            numElements = outputShape[2];

            loadLabels(assetManager);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLabels(AssetManager assetManager) {
        try {
            InputStream is = assetManager.open(labelPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public List<BoundingBox> detect(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, tensorWidth, tensorHeight, false);
        TensorImage tensorImage = new TensorImage(INPUT_IMAGE_TYPE);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        ByteBuffer imageBuffer = processedImage.getBuffer();

        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(imageBuffer, output.getBuffer());

        return bestBox(output.getFloatArray());
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private List<BoundingBox> bestBox(float[] array) {
        List<BoundingBox> boundingBoxes = new ArrayList<>();

        for (int c = 0; c < numElements; c++) {
            float maxConf = CONFIDENCE_THRESHOLD;
            int maxIdx = -1;

            for (int j = 4; j < numChannel; j++) {
                int arrayIdx = c + numElements * j;
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx];
                    maxIdx = j - 4;
                }
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                float cx = array[c];
                float cy = array[c + numElements];
                float w = array[c + numElements * 2];
                float h = array[c + numElements * 3];

                float x1 = cx - (w / 2F);
                float y1 = cy - (h / 2F);
                float x2 = cx + (w / 2F);
                float y2 = cy + (h / 2F);

                if (x1 < 0F || x1 > 1F || y1 < 0F || y1 > 1F || x2 < 0F || x2 > 1F || y2 < 0F || y2 > 1F) continue;

                boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cx, cy, w, h, maxConf, maxIdx, labels.get(maxIdx)));
            }
        }

        if (boundingBoxes.isEmpty()) return null;

        return applyNMS(boundingBoxes);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        boxes.sort((b1, b2) -> Float.compare(b2.getCnf(), b1.getCnf())); // Sort by confidence

        List<BoundingBox> selectedBoxes = new ArrayList<>();

        while (!boxes.isEmpty()) {
            BoundingBox first = boxes.get(0);
            selectedBoxes.add(first);
            boxes.remove(0);

            boxes.removeIf(nextBox -> calculateIoU(first, nextBox) >= IOU_THRESHOLD);
        }

        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        float x1 = Math.max(box1.getX1(), box2.getX1());
        float y1 = Math.max(box1.getY1(), box2.getY1());
        float x2 = Math.min(box1.getX2(), box2.getX2());
        float y2 = Math.min(box1.getY2(), box2.getY2());

        float intersectionArea = Math.max(0F, x2 - x1) * Math.max(0F, y2 - y1);
        float box1Area = box1.getW() * box1.getH();
        float box2Area = box2.getW() * box2.getH();

        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }
}

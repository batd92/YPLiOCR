package com.engine.scan;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.engine.scan.common.AssetCopier;
import com.engine.scan.ppocr.Native;
import com.engine.scan.ppocr.OcrResultModel;
import com.engine.scan.yolo.BoundingBox;
import com.engine.scan.yolo.Detector;
import com.engine.scan.yolo.OverlayView;
import com.engine.scan.common.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

    protected static final String MODEL_PATH_DEFAULT = "models";
    protected static final String LABEL_PATH_DEFAULT = "labels";
    protected static final String CONFIG_PATH_DEFAULT = "config";

    // PaddleOCR model 
    protected String detModelPath = "ch_ppocr_mobile_v2.0_det_slim_opt.nb";//"ch_PP-OCRv3_det_slim_infer.nb";
    protected String recModelPath = "ch_ppocr_mobile_v2.0_rec_slim_opt.nb";
    protected String clsModelPath = "ch_ppocr_mobile_v2.0_cls_slim_opt.nb";
    protected String labelPath = "ppocr_keys_v1.txt";
    protected String configPath = "config.txt";
    protected int cpuThreadNum = 1;
    protected String cpuPowerMode = "LITE_POWER_HIGH";

    public static final int REQUEST_LOAD_MODEL = 0;
    public static final int REQUEST_RUN_MODEL = 1;
    public static final int OPEN_GALLERY_REQUEST_CODE = 0;

    // state model
    public static final int LOAD_MODEL_SUCCESSFUL= 0;
    public static final int LOAD_MODEL_FAILED = 1;
    public static final int RUN_MODEL_SUCCESSFUL = 2;
    public static final int RUN_MODEL_FAILED = 3;

    // dialog
    protected ProgressDialog pbLoadModel = null;
    protected ProgressDialog pbRunModel = null;

    Native paddleNative = new Native();
    protected Detector yoloV8;

    protected Handler receiver = null; // Receive messages from worker thread
    protected Handler sender = null; // Send command to worker thread
    protected HandlerThread worker = null; // Worker thread to load&run model

    // Layout
    protected ImageView ivInputImage;
    String savedImagePath = "images/save.jpg";

    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clearPreferences();
        getLayoutOnScreen();
        initHandlers();
        initWorkerThread();
        // copy image Assets
        AssetCopier.copyAssetsImagesToPhotos(this);
    }

    // Setup the UI components
    private void getLayoutOnScreen() {
        ivInputImage = findViewById(R.id.iv_input_image);
    }

    /*
    * When close app
     */
    private void clearPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }

    private void initYoLov8() {
        yoloV8 = new Detector(this);
    }

    @SuppressLint("HandlerLeak")
    private void initHandlers() {
        receiver = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                handleReceiverMessage(msg);
            }
        };
    }

    @SuppressLint("HandlerLeak")
    private void initWorkerThread() {
        worker = new HandlerThread("Predictor Worker");
        worker.start();
        sender = new Handler(worker.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message message) {
                handleSenderMessage(message);
            }
        };
    }


    /**
     * WorkerThread receiver message of model
     */
    private void handleReceiverMessage(@NonNull Message message) {
        switch (message.what) {
            case LOAD_MODEL_SUCCESSFUL:
                dismissProgressDialog(pbLoadModel);
                onLoadModelSuccess();
                break;
            case LOAD_MODEL_FAILED:
                dismissProgressDialog(pbLoadModel);
                Toast.makeText(MainActivity.this, "Load model failed!", Toast.LENGTH_SHORT).show();
                onLoadModelFailed();
                break;
            case RUN_MODEL_SUCCESSFUL:
                dismissProgressDialog(pbRunModel);
                onRunModelSuccess();
                break;
            case RUN_MODEL_FAILED:
                dismissProgressDialog(pbRunModel);
                Toast.makeText(MainActivity.this, "Run model failed!", Toast.LENGTH_SHORT).show();
                onRunModelFailed();
                break;
            default:
                break;
        }
    }

    /**
     * WorkerThread Receiver Request
     */
    private void handleSenderMessage(@NonNull Message message) {
        int responseMessage;
        switch (message.what) {
            case REQUEST_LOAD_MODEL:
                responseMessage = onLoadModel() ? LOAD_MODEL_SUCCESSFUL : LOAD_MODEL_FAILED;
                break;
            case REQUEST_RUN_MODEL:
                responseMessage = onRunModel() ? RUN_MODEL_SUCCESSFUL : RUN_MODEL_FAILED;
                break;
            default:
                return;
        }
        receiver.sendEmptyMessage(responseMessage);
    }

    private void dismissProgressDialog(ProgressDialog progressDialog) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        if (paddleNative != null) {
            paddleNative.release();
        }
        worker.quit();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkRunLoadModel();
    }

    public void checkRunLoadModel() {
        pbLoadModel = ProgressDialog.show(this, "", "loading model...", false, false);
        sender.sendEmptyMessage(REQUEST_LOAD_MODEL);
    }

    /*
    * Load model (Paddle + Yolo)
    */
    public boolean onLoadModel() {
        try {
            String realModelPath = "";
            String realLabelPath = "";
            String realConfigPath = "";
            if (MODEL_PATH_DEFAULT.charAt(0) != '/') {
                realModelPath = this.getCacheDir() + "/" + MODEL_PATH_DEFAULT;
                Utils.copyDirectoryFromAssets(this, MODEL_PATH_DEFAULT, realModelPath);

                realLabelPath = this.getCacheDir() + "/" + LABEL_PATH_DEFAULT;
                Utils.copyDirectoryFromAssets(this, LABEL_PATH_DEFAULT, realLabelPath);

                realConfigPath = this.getCacheDir() + "/" + CONFIG_PATH_DEFAULT;
                Utils.copyDirectoryFromAssets(this, CONFIG_PATH_DEFAULT, realConfigPath);
            }
            if (realModelPath.isEmpty()) {
                return false;
            }

            String labelRealDir = realLabelPath + "/" + labelPath;
            String configRealDir = realConfigPath + "/" + configPath;
            String detRealModelDir = realModelPath + "/" + detModelPath;
            String clsRealModelDir =  realModelPath + "/" + clsModelPath;
            String recRealModelDir = realModelPath + "/" + recModelPath;

            boolean isLoadPaddle = paddleNative.init(
                MainActivity.this,
                detRealModelDir,
                clsRealModelDir,
                recRealModelDir,
                configRealDir,
                labelRealDir,
                cpuThreadNum,
                cpuPowerMode
            );

            // Load Yolo v8
            initYoLov8();
            return isLoadPaddle;
        } catch (Throwable e) {
            return false;
        }
    }

    /*
    * Run detect image
    */
    public void btn_run_model_click(View view) {
        if (paddleNative != null && yoloV8 != null) {
            runDetector();
        }
    }

    /*
    * Sender run 
    */
    public void runDetector() {
        pbRunModel = ProgressDialog.show(this, "", "running model...", false, false);
        sender.sendEmptyMessage(REQUEST_RUN_MODEL);
    }

    /**
     * Detect image
     */
    public boolean onRunModel() {
        // Detect by yolo -> paddler ocr
        try {
            Bitmap image = ((BitmapDrawable) ivInputImage.getDrawable()).getBitmap();
            Log.d("onRunModel", "Image and modes are ready");
            if (image != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    List<BoundingBox> bestBoxes = yoloV8.detect(image);

                    if (bestBoxes != null) {
                        Log.d("onRunModel", "Bounding boxes detected: " + bestBoxes.size());

                        List<Bitmap> croppedImages = cropBoundingBoxes(image, bestBoxes);
                        Log.d("onRunModel", "Cropped images created: " + croppedImages.size());

                        StringBuilder ocrResults = new StringBuilder();
                        for (Bitmap croppedImage : croppedImages) {
                            saveImageToStorage(croppedImage);
                            ArrayList<OcrResultModel> result = paddleNative.runImage(croppedImage);

                            for (OcrResultModel ocrResult : result) {
                                ocrResults.append(ocrResult.getText()).append("\n");
                            }
                        }

                        TextView tvResult = findViewById(R.id.tv_result);
                        tvResult.setText(ocrResults.toString());

                        // Draw bounding boxes
                        OverlayView overlayView = findViewById(R.id.overlayView);
                        overlayView.setResults(bestBoxes);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("onRunModel", "Error running model", e);
            return false;
        }
    }

    private void saveImageToStorage(Bitmap bitmap) {
        String fileName = "cropped_image_" + System.currentTimeMillis() + ".png"; // Generate unique file name
        File storageDir = getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = new File(storageDir, fileName);

        try {
            FileOutputStream fos = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos); // Compress bitmap into PNG format
            fos.close();
            Log.d("saveImageToStorage", "Image saved to: " + imageFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("saveImageToStorage", "Error saving image: " + e.getMessage());
        }
    }

    private List<Bitmap> cropBoundingBoxes(Bitmap image, List<BoundingBox> boxes) {
        List<Bitmap> croppedImages = new ArrayList<>();
        int frameSize = 448;

        for (BoundingBox box : boxes) {
            int left = (int) (box.getX1() * image.getWidth());
            int top = (int) (box.getY1() * image.getHeight());
            int right = (int) (box.getX2() * image.getWidth());
            int bottom = (int) (box.getY2() * image.getHeight());

            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(image.getWidth(), right);
            bottom = Math.min(image.getHeight(), bottom);

            int width = right - left;
            int height = bottom - top;

            Bitmap croppedImage = Bitmap.createBitmap(image, left, top, width, height);

            Bitmap blackFrame = Bitmap.createBitmap(frameSize, frameSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(blackFrame);
            canvas.drawColor(Color.BLACK);

            int xOffset = (frameSize - width) / 2;
            int yOffset = (frameSize - height) / 2;

            canvas.drawBitmap(croppedImage, xOffset, yOffset, null);

            croppedImages.add(blackFrame);
        }
        return croppedImages;
    }


    public void onLoadModelFailed() {
        // TODO
    }

    /**
     * Load UI when detect success
     */
    @SuppressLint("SetTextI18n")
    public void onRunModelSuccess() {
        // TODO
    }

    @SuppressLint("SetTextI18n")
    public void onRunModelFailed() {
        // TODO
    }

    @SuppressLint("SetTextI18n")
    public void onLoadModelSuccess() {
        // TODO
    }

    public void btn_choice_img_click(View view) {
        openGallery();
    }

    public void btn_reset_img_click(View view) {
        OverlayView overlayView = findViewById(R.id.overlayView);
        overlayView.setResults(null);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (resultCode == RESULT_OK && requestCode == OPEN_GALLERY_REQUEST_CODE) {
                if (data != null) {

                    ContentResolver resolver = getContentResolver();
                    Uri uri = data.getData();
                    Bitmap image = MediaStore.Images.Media.getBitmap(resolver, uri);
                    String[] proj = { MediaStore.Images.Media.DATA};
                    Cursor cursor = managedQuery(uri, proj, null, null, null);
                    cursor.moveToFirst();
                    if (image != null) {
                        ivInputImage.setImageBitmap(image);
                        OverlayView overlayView = findViewById(R.id.overlayView);
                        overlayView.setResults(null);
                        TextView tvResult = findViewById(R.id.tv_result);
                        tvResult.setText("");
                    }
                }
            }
        } catch (IOException e) {
            Log.e("IOException ", e.toString());
        }
    }
}
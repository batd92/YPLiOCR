package com.engine.scan.common;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.*;
import java.util.List;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static void copyFileFromAssets(Context appCtx, String srcPath, String dstPath) {
        if (srcPath == null || srcPath.isEmpty() || dstPath == null || dstPath.isEmpty()) {
            return;
        }

        try (InputStream is = new BufferedInputStream(appCtx.getAssets().open(srcPath));
             OutputStream os = new BufferedOutputStream(new FileOutputStream(new File(dstPath)))) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                os.write(buffer, 0, length);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void copyDirectoryFromAssets(Context appCtx, String srcDir, String dstDir) {
        if (srcDir == null || srcDir.isEmpty() || dstDir == null || dstDir.isEmpty()) {
            return;
        }

        try {
            String[] assets = appCtx.getAssets().list(srcDir);
            if (assets == null || assets.length == 0) {
                return;
            }

            File dstDirFile = new File(dstDir);
            if (!dstDirFile.exists() && !dstDirFile.mkdirs()) {
                throw new IOException("Failed to create directory: " + dstDir);
            }

            for (String fileName : assets) {
                String srcSubPath = srcDir + File.separator + fileName;
                String dstSubPath = dstDir + File.separator + fileName;

                if (isDirectory(appCtx, srcSubPath)) {
                    copyDirectoryFromAssets(appCtx, srcSubPath, dstSubPath);
                } else {
                    copyFileFromAssets(appCtx, srcSubPath, dstSubPath);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean isDirectory(Context appCtx, String path) {
        try {
            String[] assets = appCtx.getAssets().list(path);
            return assets != null && assets.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public static float[] parseFloatsFromString(String string, String delimiter) {
        String[] pieces = string.trim().toLowerCase().split(delimiter);
        float[] floats = new float[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            floats[i] = Float.parseFloat(pieces[i].trim());
        }
        return floats;
    }

    public static long[] parseLongsFromString(String string, String delimiter) {
        String[] pieces = string.trim().toLowerCase().split(delimiter);
        long[] longs = new long[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            longs[i] = Long.parseLong(pieces[i].trim());
        }
        return longs;
    }

    public static String getSDCardDirectory() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static String getDCIMDirectory() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
    }

    public static Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeight() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    public static int getCameraDisplayOrientation(Context context, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;   // compensate the mirror
        } else {
            // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    public static int createShaderProgram(String vss, String fss) {
        int vshader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vshader, vss);
        GLES20.glCompileShader(vshader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, GLES20.glGetShaderInfoLog(vshader));
            GLES20.glDeleteShader(vshader);
            vshader = 0;
            return 0;
        }

        int fshader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fshader, fss);
        GLES20.glCompileShader(fshader);
        GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, GLES20.glGetShaderInfoLog(fshader));
            GLES20.glDeleteShader(vshader);
            GLES20.glDeleteShader(fshader);
            fshader = 0;
            return 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vshader);
        GLES20.glAttachShader(program, fshader);
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vshader);
        GLES20.glDeleteShader(fshader);
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            program = 0;
            return 0;
        }
        GLES20.glValidateProgram(program);
        GLES20.glGetProgramiv(program, GLES20.GL_VALIDATE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
            return 0;
        }

        return program;
    }

    public static boolean isSupportedNPU() {
        String hardware = Build.HARDWARE;
        return hardware.equalsIgnoreCase("kirin810") || hardware.equalsIgnoreCase("kirin990");
    }
    public static void checkFile(String nnFileName) throws
            SDKExceptions.PathNotExist {

        File file = new File(nnFileName);
        if (!file.exists()) {
            throw new SDKExceptions.PathNotExist();
        }

    }

    public static void copyAssets(Context ctx, String nnFileName) throws
            SDKExceptions.NoSDCardPermission,
            SDKExceptions.MissingModleFileInAssetFolder
    {
        AssetManager assetManager = ctx.getAssets();

        try (InputStream is = assetManager.open(nnFileName)) {
        } catch (IOException ex) {
            throw new SDKExceptions.MissingModleFileInAssetFolder();
        }

        int perm = ctx.checkCallingOrSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE");
        if (perm != PackageManager.PERMISSION_GRANTED) {
            throw new SDKExceptions.NoSDCardPermission();
        }

        File fileInSD = new File(ctx.getExternalFilesDir(null), nnFileName);
        if (fileInSD.exists()) {
            Log.d("debug===", "NN model on SD card " + fileInSD);
            return;
        }

        try (InputStream in = assetManager.open(nnFileName);
             OutputStream out = new FileOutputStream(fileInSD)) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Log.e("tag", "Failed to copy asset file: " + nnFileName, e);
        }
    }
}

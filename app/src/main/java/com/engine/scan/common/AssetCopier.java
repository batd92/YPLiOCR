package com.engine.scan.common;

import android.content.ContentValues;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AssetCopier {

    public static void copyAssetsImagesToPhotos(Context context) {
        String[] assetFiles = null;
        try {
            assetFiles = context.getAssets().list("images");
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (assetFiles != null) {
            for (String assetFile : assetFiles) {
                if (assetFile.endsWith(".jpg") || assetFile.endsWith(".png")) {
                    copyAssetToPhotos(context, "images/" + assetFile);
                }
            }
        }
    }

    private static void copyAssetToPhotos(Context context, String assetFileName) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getAssets().open(assetFileName);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, new File(assetFileName).getName());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                out = context.getContentResolver().openOutputStream(context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values));
            } else {
                File photosDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!photosDir.exists()) {
                    photosDir.mkdirs();
                }
                File outFile = new File(photosDir, new File(assetFileName).getName());
                out = new FileOutputStream(outFile);
            }

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

            in.close();
            if (out != null) {
                out.flush();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

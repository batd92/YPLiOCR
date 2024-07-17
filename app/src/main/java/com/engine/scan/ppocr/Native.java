package com.engine.scan.ppocr;

import android.content.Context;
import android.util.Log;

import com.engine.scan.common.SDKExceptions;
import com.engine.scan.common.Utils;

import java.util.concurrent.atomic.AtomicBoolean;

public class Native {
    private static final AtomicBoolean isSOLoaded = new AtomicBoolean();

    public static void loadLibrary() throws RuntimeException {
        if (!isSOLoaded.get() && isSOLoaded.compareAndSet(false, true)) {
            try {
                System.loadLibrary("Native");
            } catch (Throwable e) {
                throw new RuntimeException(
                        "Load libNative.so failed, please check it exists in apk file.", e);
            }
        }
    }


    private long ctx = 0;
    private boolean run_status = false;

    public boolean init(Context mContext,
                        String detModelPath,
                        String clsModelPath,
                        String recModelPath,
                        String configPath,
                        String labelPath,
                        int cpuThreadNum,
                        String cpuPowerMode) {
        ctx = nativeInit(
                detModelPath,
                clsModelPath,
                recModelPath,
                configPath,
                labelPath,
                cpuThreadNum,
                cpuPowerMode);
        return ctx == 0;
    }

    public boolean release() {
        if (ctx == 0) {
            return false;
        }
        return nativeRelease(ctx);
    }

    public boolean process(int inTextureId, int outTextureId, int textureWidth, int textureHeight, String savedImagePath) {
        if (ctx == 0) {
            return false;
        }
        run_status = nativeProcess(ctx, inTextureId, outTextureId, textureWidth, textureHeight, savedImagePath);
        return run_status;
    }

    public static native long nativeInit(String detModelPath,
                                         String clsModelPath,
                                         String recModelPath,
                                         String configPath,
                                         String labelPath,
                                         int cpuThreadNum,
                                         String cpuPowerMode);

    public static native boolean nativeRelease(long ctx);

    public static native boolean nativeProcess(long ctx, int inTextureId, int outTextureId, int textureWidth, int textureHeight, String savedImagePath);
}

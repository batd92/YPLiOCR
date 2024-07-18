package com.engine.scan.ppocr;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;

public class OcrResultModel {
    public String text;
    public float score;

    public OcrResultModel(String text, float score) {
        this.text = text;
        this.score = score;
    }
}

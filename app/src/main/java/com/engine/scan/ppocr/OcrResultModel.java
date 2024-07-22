package com.engine.scan.ppocr;

import android.graphics.Point;

import java.util.ArrayList;
import java.util.List;

public class OcrResultModel {
    private String text;
    private float score;

    public OcrResultModel(String text, float score) {
        this.text = text;
        this.score = score;
    }

    public String getText() {
        return text;
    }

    public float getScore() {
        return score;
    }
}

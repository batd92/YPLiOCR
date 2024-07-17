package com.engine.scan.yolo;

public class BoundingBox {

    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;
    private final float cx;
    private final float cy;
    private final float w;
    private final float h;
    private final float cnf;
    private final int cls;
    private final String clsName;
    private String valueLabel;

    public BoundingBox(float x1, float y1, float x2, float y2, float cx, float cy, float w, float h, float cnf, int cls, String clsName) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.cx = cx;
        this.cy = cy;
        this.w = w;
        this.h = h;
        this.cnf = cnf;
        this.cls = cls;
        this.clsName = clsName;
        this.valueLabel = "";
    }

    public float getX1() {
        return x1;
    }

    public float getY1() {
        return y1;
    }

    public float getX2() {
        return x2;
    }

    public float getY2() {
        return y2;
    }

    public float getCx() {
        return cx;
    }

    public float getCy() {
        return cy;
    }

    public float getW() {
        return w;
    }

    public float getH() {
        return h;
    }

    public float getCnf() {
        return cnf;
    }

    public int getCls() {
        return cls;
    }

    public String getClsName() {
        return clsName;
    }

    public String getValueLabel() {
        return valueLabel;
    }

    public void setValueLabel(String label) {
        this.valueLabel = label;
    }
}
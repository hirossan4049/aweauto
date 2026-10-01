package com.h1rose.aweauto.map;

import android.view.Surface;

interface IMapInputService {
    void destroy() = 16777114;
    int attach(in Surface surface, int width, int height, int densityDpi, String mapPackageName) = 1;
    void detach() = 2;
    void tap(float x, float y) = 3;
    void swipe(float startX, float startY, float endX, float endY, long durationMs) = 4;
    void back() = 5;
}

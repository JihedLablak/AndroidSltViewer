package com.nuitdinfo.sltviewer

import android.opengl.Matrix

class Light(private var lightPosInWorldSpace: FloatArray) {
    val positionInEyeSpace = FloatArray(4)
    var ambientColor = floatArrayOf(0.4f, 0.4f, 0.4f, 1.0f)
    var diffuseColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)
    var specularColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)

    fun setPosition(position: FloatArray) {
        lightPosInWorldSpace = position
    }

    fun applyViewMatrix(viewMatrix: FloatArray) {
        Matrix.multiplyMV(positionInEyeSpace, 0, viewMatrix, 0, lightPosInWorldSpace, 0)
    }
}

package com.nuitdinfo.sltviewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.nuitdinfo.sltviewer.model.Floor
import com.nuitdinfo.sltviewer.model.Model
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ModelRenderer(private val model: Model?) : GLSurfaceView.Renderer {
    private val light = Light(floatArrayOf(0f, 3f, 3f, 1f))
    private val floor = Floor()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var rotateAngleX = 10f
    private var rotateAngleY = 0f
    private var translateZ = -MODEL_BOUND_SIZE * 3f

    fun zoom(scale: Float) {
        if (scale != 0f) {
            translateZ /= scale
        }
        updateViewMatrix()
    }

    fun translate(dx: Float, dy: Float) {
        val translateScaleFactor = MODEL_BOUND_SIZE / 200f
        model?.translate(dx * translateScaleFactor, -dy * translateScaleFactor)
    }

    fun rotate(aX: Float, aY: Float) {
        val rotateScaleFactor = 0.5f
        rotateAngleX -= aX * rotateScaleFactor
        rotateAngleY += aY * rotateScaleFactor
        updateViewMatrix()
    }

    private fun updateViewMatrix() {
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.translateM(viewMatrix, 0, 0f, 0f, translateZ)
        Matrix.rotateM(viewMatrix, 0, rotateAngleX, 1f, 0f, 0f)
        Matrix.rotateM(viewMatrix, 0, rotateAngleY, 0f, 1f, 0f)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // Update light position in eye space.
        light.applyViewMatrix(viewMatrix)

        floor.draw(viewMatrix, projectionMatrix, light)
        model?.draw(viewMatrix, projectionMatrix, light)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, Z_NEAR, Z_FAR)

        // initialize the view matrix
        updateViewMatrix()
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1f)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        floor.setup(MODEL_BOUND_SIZE)
        model?.let {
            it.setup(MODEL_BOUND_SIZE)
            floor.setOffsetY(it.floorOffset)
        }
    }

    companion object {
        private const val MODEL_BOUND_SIZE = 50f
        private const val Z_NEAR = 2f
        private const val Z_FAR = MODEL_BOUND_SIZE * 20
    }
}

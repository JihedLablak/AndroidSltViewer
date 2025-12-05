package com.nuitdinfo.sltviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import com.nuitdinfo.sltviewer.model.Model
import com.nuitdinfo.sltviewer.util.Util.pxToDp
import kotlin.math.sqrt

@SuppressLint("ViewConstructor")
class ModelSurfaceView(context: Context, model: Model?) : GLSurfaceView(context) {

    private val renderer: ModelRenderer
    private var previousX = 0f
    private var previousY = 0f
    private var pinchStartDistance = 0.0f
    private var touchMode = TOUCH_NONE

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            touchMode = TOUCH_MOVE
        }
    })


    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (touchMode == TOUCH_MOVE) {
                        val x = event.x
                        val y = event.y
                        val dx = x - previousX
                        val dy = y - previousY
                        previousX = x
                        previousY = y
                        renderer.translate(pxToDp(dx), pxToDp(dy))
                    } else {
                        if (touchMode != TOUCH_ROTATE) {
                            previousX = event.x
                            previousY = event.y
                        }
                        touchMode = TOUCH_ROTATE
                        val x = event.x
                        val y = event.y
                        val dx = x - previousX
                        val dy = y - previousY
                        previousX = x
                        previousY = y
                        renderer.rotate(pxToDp(dy), pxToDp(dx))
                    }
                } else if (event.pointerCount == 2) {
                    if (touchMode != TOUCH_ZOOM) {
                        pinchStartDistance = getPinchDistance(event)
                        touchMode = TOUCH_ZOOM
                    } else {
                        val pinchScale = getPinchDistance(event) / pinchStartDistance
                        pinchStartDistance = getPinchDistance(event)
                        renderer.zoom(pinchScale)
                    }
                }
                requestRender()
            }
            MotionEvent.ACTION_UP -> {
                touchMode = TOUCH_NONE
            }
        }
        return true
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt((x * x + y * y).toDouble()).toFloat()
    }

    private fun getPinchCenterPoint(event: MotionEvent, pt: PointF) {
        pt.x = (event.getX(0) + event.getX(1)) * 0.5f
        pt.y = (event.getY(0) + event.getY(1)) * 0.5f
    }

    companion object {
        private const val TOUCH_NONE = 0
        private const val TOUCH_ROTATE = 1
        private const val TOUCH_ZOOM = 2
        private const val TOUCH_MOVE = 3
    }

    init {
        setEGLContextClientVersion(2)
        renderer = ModelRenderer(model)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }
}

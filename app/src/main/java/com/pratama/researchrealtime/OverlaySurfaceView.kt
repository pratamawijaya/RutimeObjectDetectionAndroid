package com.pratama.researchrealtime

import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView

class OverlaySurfaceView(surfaceView: SurfaceView) : SurfaceView(surfaceView.context),
    SurfaceHolder.Callback {


    init {
        surfaceView.holder.addCallback(this)
        surfaceView.setZOrderOnTop(true)
    }

    private var surfaceHolder = surfaceView.holder
    private val paint = Paint()
    private val pathColorList = listOf(Color.RED, Color.GREEN, Color.CYAN, Color.BLUE)

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceHolder.setFormat(PixelFormat.TRANSPARENT)
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
    }

    fun draw(detectedObjectList: List<DetectionObject>){
        //Get canvas via surfaceHolder(Even when the screen is not active, it will be drawn and exception may occur, so make it nullable and handle it below)
        val canvas: Canvas? = surfaceHolder.lockCanvas()
        //Clear what was previously drawn
        canvas?.drawColor(0, PorterDuff.Mode.CLEAR)

        detectedObjectList.mapIndexed { i, detectionObject ->
            //Bounding box display
            paint.apply {
                color = pathColorList[i]
                style = Paint.Style.STROKE
                strokeWidth = 7f
                isAntiAlias = false
            }
            canvas?.drawRect(detectionObject.boundingBox, paint)

            //Label and score display
            paint.apply {
                style = Paint.Style.FILL
                isAntiAlias = true
                textSize = 77f
            }
            canvas?.drawText(
                detectionObject.label + " " + "%,.2f".format(detectionObject.score * 100) + "%",
                detectionObject.boundingBox.left,
                detectionObject.boundingBox.top - 5f,
                paint
            )
        }

        surfaceHolder.unlockCanvasAndPost(canvas ?: return)
    }
}
package com.example.drawingapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet): View(context, attrs) {
    private var mDrawPath: CustomPath? = null
    private var mCanvasBitmap: Bitmap? = null
    private var mDrawPaint: Paint? = null
    private var mCanvasPaint: Paint? = null
    private var mBrushSize: Float = 0.toFloat()
    private var color = Color.BLACK
    private var canvas: Canvas? = null
    private val mPaths = ArrayList<CustomPath>()
    private val mUndoPaths = ArrayList<CustomPath>()

    init{
        setupDrawing()
    }

    fun onClickUndo(){
        if(mPaths.size > 0){
            mUndoPaths.add(mPaths.removeAt(mPaths.size -1))
            //now we need to draw the new path
            invalidate() // this internally calls onDraw()
        }
    }

    fun onClickRedo(){
        if(mUndoPaths.size > 0){
            mPaths.add(mUndoPaths.removeAt(mUndoPaths.size-1))
            invalidate()
        }
    }

    private fun setupDrawing(){
        mDrawPaint = Paint()
        mDrawPath = CustomPath(color, mBrushSize)
        mDrawPaint!!.color = color
        mDrawPaint!!.style = Paint.Style.STROKE
        mDrawPaint!!.strokeJoin = Paint.Join.ROUND
        mDrawPaint!!.strokeCap = Paint.Cap.ROUND
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
        //mBrushSize = 20.toFloat()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mCanvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvas = Canvas(mCanvasBitmap!!)
    }

    override fun onDraw(canvas: Canvas/*by default its nullable but we remove the "?"*/) {
        super.onDraw(canvas)
        canvas.drawBitmap(mCanvasBitmap!!, 0f, 0f, mCanvasPaint)

        for(path in mPaths){
            mDrawPaint!!.strokeWidth = path!!.brushThickness
            mDrawPaint!!.color = path!!.color
            canvas.drawPath(path, mDrawPaint!!)
        }

        if(!mDrawPath!!.isEmpty){
            mDrawPaint!!.strokeWidth = mDrawPath!!.brushThickness
            mDrawPaint!!.color = mDrawPath!!.color
            canvas.drawPath(mDrawPath!!, mDrawPaint!!)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {

        val touchX = event?.x
        val touchY = event?.y

        when(event?.action){
            //three action events that are important are
            //actionDown: when we put our finger on screen
            //actionMove: when we drag our finger on screen
            //actionUp: when we remove our finger from screen
            MotionEvent.ACTION_DOWN ->{
                mDrawPath!!.color = color
                mDrawPath!!.brushThickness = mBrushSize
                //these are setting the path not the paint that we did before

                //mDrawPath!!.reset()
                if (touchX != null) {
                    if (touchY != null) {
                        mDrawPath!!.moveTo(touchX,touchY)
                    }
                }
            }
            MotionEvent.ACTION_MOVE ->{
                if (touchX != null) {
                    if (touchY != null) {
                        mDrawPath!!.lineTo(touchX,touchY)
                    }
                }
            }
            MotionEvent.ACTION_UP ->{
                mPaths.add(mDrawPath!!)
                mDrawPath = CustomPath(color, mBrushSize)
            }
            else -> return false //this is for all the other cases that are possible
        }
        invalidate() //invalidates the whole view if view is visible
        //return super.onTouchEvent(event)
        return true
    }

    fun setSizeForBrush(newSize : Float){
        mBrushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        newSize, resources.displayMetrics) //so that brush size is ok for different screensizes

        mDrawPaint!!.strokeWidth = mBrushSize
    }

   fun setColor(newColor : String){
       color = Color.parseColor(newColor)
       mDrawPaint!!.color = color
   }

    internal inner class CustomPath(var color: Int, var brushThickness: Float) : Path(){

    }
}
package org.primeval.manager

import android.graphics.Bitmap

/** Cache the scanned row so segmentation does not cross JNI for every pixel. */
class BitmapPixels(private val image:Bitmap):Pixels{
    override val width=image.width
    override val height=image.height
    private var yCached=-1
    private val row=IntArray(width)
    override fun rgb(x:Int,y:Int):Int{
        if(y!=yCached){image.getPixels(row,0,width,0,y,width,1);yCached=y}
        return row[x]
    }
}

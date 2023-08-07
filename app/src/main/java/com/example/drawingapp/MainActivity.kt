package com.example.drawingapp
//need provider in manifest to save to external storage
import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton? = null
    var customProgressDialog: Dialog? = null
    //launcher for picking image
    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result->
            if(result.resultCode == RESULT_OK && result.data!=null){
                val imageBackground = findViewById<ImageView>(R.id.iv_background)
                //we get URI that is path of image on the device
                imageBackground.setImageURI(result.data?.data) //result.data? is the location and .data gives image
            }
        }

    val requestPermission:ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if (isGranted){
                    Snackbar.make(
                        drawingView!!,
                        "Permission granted for storage.",
                        Snackbar.LENGTH_SHORT
                    ).show();

                    //to open image picker we can use intent
                    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    //we need a launcher to select image
                    openGalleryLauncher.launch(pickIntent)

                }else{
                    if(permissionName==Manifest.permission.READ_MEDIA_IMAGES) {
                        Snackbar.make(
                            drawingView!!,
                            "Permission denied for storage.",
                            Snackbar.LENGTH_SHORT
                        ).show();
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(20.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ibBrush = findViewById<ImageButton>(R.id.ib_brush)
        ibBrush.setOnClickListener{
            showBrushSizeDialog()
        }

        val ibGallery = findViewById<ImageButton>(R.id.ib_gallery)
        ibGallery.setOnClickListener{
            requestStoragePermission()
        }

        val ibUndo = findViewById<ImageButton>(R.id.ib_undo)
        ibUndo.setOnClickListener{
            drawingView?.onClickUndo()
        }

        val ibRedo = findViewById<ImageButton>(R.id.ib_redo)
        ibRedo.setOnClickListener{
            drawingView?.onClickRedo()
        }

        val ibSave = findViewById<ImageButton>(R.id.ib_save)
        ibSave.setOnClickListener{
            showProgressDialog()
            lifecycleScope.launch{
                val flDrawingView: FrameLayout = findViewById(R.id.fl_drawing_view_container)
                saveBitmapFile(getBitmapFromView(flDrawingView))
            }
        }
    }

    private fun showRationaleDialog(title:String, message:String){
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){dialog, _->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun showBrushSizeDialog(){
        //dialog is a popup
        val brushDialog : Dialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size:")
        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)

        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }
    fun paintClicked(view: View){
        if(view != mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            //tag is the #value of the name the tag is given
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)
            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )
            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )
            mImageButtonCurrentPaint = view
        }
    }
    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_MEDIA_IMAGES)){
            showRationaleDialog("Drawing App needs to access your external storage",
                "External storage permission is denied")
        }else{
            requestPermission.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            )
        }
    }

    //to export the image first we need to extract bitmap from view
    private fun getBitmapFromView(view:View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        //we need bind canvas that is on the view i.e what we drew
        val canvas = Canvas(returnedBitmap)
        //we also need to include background
        val bgDrawable = view.background
        if(bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }
        //now draw canvas onto view
        view.draw(canvas)
        return(returnedBitmap)
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap): String{
       var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap!=null){
                try{
                    val bytes = ByteArrayOutputStream() //initial capacity = 32 bytes
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val f = File(externalCacheDir?.absoluteFile.toString() +
                    File.separator+"DrawingApp_"+System.currentTimeMillis()/1000+".png")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        cancelProgressDialog()
                        if(result.isNotEmpty()){
                          shareImage(result)
                          Toast.makeText(this@MainActivity, "File saved successfully :$result", Toast.LENGTH_LONG).show()
                        }else{
                          Toast.makeText(this@MainActivity, "Something went wrong while saving the file :$result", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                catch(e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setCanceledOnTouchOutside(false)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }
    private fun cancelProgressDialog(){
        if(customProgressDialog != null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result: String /*path where image is at*/){
        MediaScannerConnection.scanFile(this, arrayOf(result), null){
            path, uri->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND //.action tells what kind of action the intent does
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri) // passing where image is in files
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share")) //this opens the sharing screen

        }
    }
}
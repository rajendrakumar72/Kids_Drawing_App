package com.mrkumar.kidsdrawingapp

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.mrkumar.kidsdrawingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageButtonCurrentColor:ImageButton
    private var customProgressDialog:Dialog?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding= ActivityMainBinding.inflate(layoutInflater)
        val view =binding.root
        setContentView(view)
        binding.drawLayout.setSizeForBrush(20.toFloat())
        imageButtonCurrentColor=binding.llPaintColorPallet[1] as ImageButton
        imageButtonCurrentColor.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.pallet_selected))

        binding.brush.setOnClickListener {
            showCustomBrushDialog()
        }

        binding.btnGalleryOpen.setOnClickListener {
            if (isReadAndStorageAllowed()){
                //action to take image from external storage
                val photoTakeIntent= Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                startActivityForResult(photoTakeIntent, GALLERY)

            }else{
                requiredStoragePermission()
            }
        }

        binding.btnUndo.setOnClickListener {
            binding.drawLayout.undoPaint()
        }

        //saveImage in external Storage
        binding.btnSaveImage.setOnClickListener {
            if (isReadAndStorageAllowed()){
                showCustomBrushDialog()
                lifecycleScope.launch {
                    val flParent=binding.frameParentLayout

                    saveBitmapToStorage(getBitmapFromView(flParent))
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode==Activity.RESULT_OK){
            if (requestCode== GALLERY){
                try {
                    if (data!!.data!=null){
                        binding.imgBg.visibility =View.VISIBLE
                        binding.imgBg.setImageURI(data.data)
                    }else{
                        Toast.makeText(this,"Image Picking Error",Toast.LENGTH_SHORT).show()

                    }
                }catch (e:Exception){
                    Log.e("TAG", "imageExec: $e")
                }
            }
        }
    }

    private fun showCustomBrushDialog(){
        val dialog=Dialog(this)
        dialog.setContentView(R.layout.dialog_brush_size)
        dialog.setTitle("Choose Brush Size")

        val btnSmall=dialog.findViewById<ImageButton>(R.id.smallBrushButton)
        btnSmall.setOnClickListener {
            binding.drawLayout.setSizeForBrush(10.toFloat())
            dialog.dismiss()
        }

        val btnMedium=dialog.findViewById<ImageButton>(R.id.mediumBrushButton)
        btnMedium.setOnClickListener {
            binding.drawLayout.setSizeForBrush(20.toFloat())
            dialog.dismiss()
        }

        val btnLarge=dialog.findViewById<ImageButton>(R.id.largeBrushButton)
        btnLarge.setOnClickListener {
            binding.drawLayout.setSizeForBrush(30.toFloat())
            dialog.dismiss()
        }

        dialog.show()
    }

    fun selectedColor(view: View){
        if (view !=imageButtonCurrentColor){
            val imageButton= view as ImageButton

            val colorTag=imageButton.tag.toString()
            binding.drawLayout.setColor(colorTag)

            imageButton.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.pallet_selected))

            imageButtonCurrentColor.setImageDrawable(ContextCompat.getDrawable(this,R.drawable.pallet_normal))
            imageButtonCurrentColor=view
        }
    }

    private fun isReadAndStorageAllowed():Boolean {
       val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result==PackageManager.PERMISSION_GRANTED
    }

    private fun requiredStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE).toString())){
            Toast.makeText(this,"Need a Background Permission to access file",Toast.LENGTH_SHORT).show()
        }
        ActivityCompat.requestPermissions(this,arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE), READ_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode== READ_PERMISSION){
            if (grantResults.isNotEmpty()&& grantResults[0]==PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"Permission Granted",Toast.LENGTH_SHORT).show()

            }else{
                Toast.makeText(this,"Permission Denied",Toast.LENGTH_SHORT).show()

            }
        }
    }

    private fun getBitmapFromView(view: View):Bitmap{
        val returnedBitmap= Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)

        val canvas= Canvas(returnedBitmap)

        val bgDrawable=view.background

        if (bgDrawable!=null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmapToStorage(bitmap: Bitmap):String{
        var result=""

        withContext(Dispatchers.IO){
            if (bitmap!=null){
                try {
                   val bytes=ByteArrayOutputStream()

                   bitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)

                    val file=File(externalCacheDir?.absoluteFile.toString()+
                            File.separator+"DrawingImage"+
                            System.currentTimeMillis()/1000+".png")

                    val fo=FileOutputStream(file)
                    fo.write(bytes.toByteArray())
                    fo.close()
                    result=file.absolutePath

                    withContext(Dispatchers.Main){
                        cancelProgressDialog()
                        if (result.isNotEmpty()){
                            Toast.makeText(
                                this@MainActivity,
                                "File saved successfully :$result",
                                Toast.LENGTH_SHORT
                            ).show()
                            shareImage(result)
                        }else{
                            Toast.makeText(
                                this@MainActivity,
                                "Something went wrong while saving the file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }catch (e:Exception){
                    result=""
                    Log.e("TAG", "saveBitmapToStorage: ${e.localizedMessage}" )
                }
            }
        }
        return result
    }

    private fun showProgressDialog() {
        customProgressDialog = Dialog(this@MainActivity)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        customProgressDialog?.setContentView(R.layout.custom_progress_dialog)

        //Start the dialog and display it on screen.
        customProgressDialog?.show()
    }

    /** Todo 3: create function to cancel dialog
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun cancelProgressDialog() {
        if (customProgressDialog != null) {
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result:String){

        /*MediaScannerConnection provides a way for applications to pass a
        newly created or downloaded media file to the media scanner service.
        The media scanner service will read metadata from the file and add
        the file to the media content provider.
        The MediaScannerConnectionClient provides an interface for the
        media scanner service to return the Uri for a newly scanned file
        to the client of the MediaScannerConnection class.*/

        /*scanFile is used to scan the file when the connection is established with MediaScanner.*/
        MediaScannerConnection.scanFile(
            this@MainActivity, arrayOf(result), null
        ) { path, uri ->
            // This is used for sharing the image after it has being stored in the storage.
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(
                Intent.EXTRA_STREAM,
                uri
            ) // A content: URI holding a stream of data associated with the Intent, used to supply the data being sent.
            shareIntent.type =
                "image/png" // The MIME type of the data being handled by this intent.
            startActivity(
                Intent.createChooser(
                    shareIntent,
                    "Share"
                )
            )// Activity Action: Display an activity chooser,
            // allowing the user to pick what they want to before proceeding.
            // This can be used as an alternative to the standard activity picker
            // that is displayed by the system when you try to start an activity with multiple possible matches,
            // with these differences in behavior:
        }
        // END
    }

    companion object{
        const val READ_PERMISSION =1
        const val GALLERY =2
    }
}

package com.example.videochooser.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLException
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.videochooser.R
import com.example.videochooser.ui.camerarecorder.CameraRecordListener
import com.example.videochooser.ui.camerarecorder.CameraRecorder
import com.example.videochooser.ui.camerarecorder.CameraRecorderBuilder
import com.example.videochooser.ui.camerarecorder.LensFacing
import com.example.videochooser.ui.widget.Filters
import com.example.videochooser.ui.widget.SampleGLView
import kotlinx.android.synthetic.main.activity_base_camera.*
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.*
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10

class BaseCameraActivity : AppCompatActivity() {
    private var sampleGLView: SampleGLView? = null
    protected var cameraRecorder: CameraRecorder? = null
    private var filepath: String? = null
    protected var lensFacing = LensFacing.BACK

    private var filterDialog: AlertDialog? = null
    private var toggleClick = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base_camera)
        listeners();
        onCreateActivity()
    }

    private fun listeners() {
        galleryButton.setOnClickListener(View.OnClickListener {
            pickFromGallery()
        })
        cameraButton.setOnClickListener(View.OnClickListener {
            manageViews(true)
            onCreateActivity()
        })
    }

    override fun onResume() {
        super.onResume()
        checkPermission()
    }

    private fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        // request camera permission if it has not been grunted.
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED || (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            )
                    != PackageManager.PERMISSION_GRANTED) || (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), CAMERA_PERMISSION_REQUEST_CODE
            )
            return false
        } else {
            setUpCamera()
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this@BaseCameraActivity,
                    "camera permission has been granted.",
                    Toast.LENGTH_SHORT
                ).show()
                setUpCamera()
            } else {
                Toast.makeText(
                    this@BaseCameraActivity,
                    "[WARN] camera permission is not granted.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    protected fun onCreateActivity() {
        println("onCreateActivity:::")
        supportActionBar!!.hide()
        btnRecord.setOnClickListener(View.OnClickListener { v: View? ->
            if (btnRecord.getText() == getString(R.string.app_record)) {
                filepath = videoFilePath
                cameraRecorder!!.start(filepath)
                btnRecord.setText("Stop")
            } else {
                cameraRecorder!!.stop()
                btnRecord.setText(getString(R.string.app_record))
            }
        })
        findViewById<View>(R.id.btn_flash).setOnClickListener { v: View? ->
            if (cameraRecorder != null && cameraRecorder!!.isFlashSupport) {
                cameraRecorder!!.switchFlashMode()
                cameraRecorder!!.changeAutoFocus()
            }
        }
        findViewById<View>(R.id.btn_switch_camera).setOnClickListener { v: View? ->
            releaseCamera()
            lensFacing = if (lensFacing == LensFacing.BACK) {
                LensFacing.FRONT
            } else {
                LensFacing.BACK
            }
            toggleClick = true
        }

        /*findViewById<View>(R.id.btn_filter).setOnClickListener { v: View ->
            if (filterDialog == null) {
                val builder =
                    AlertDialog.Builder(v.context)
                builder.setTitle("Choose a filter")
                builder.setOnDismissListener { dialog: DialogInterface? ->
                    filterDialog = null
                }
                val filters = Filters.values()
                val charList =
                    arrayOfNulls<CharSequence>(filters.size)
                var i = 0
                val n = filters.size
                while (i < n) {
                    charList[i] = filters[i].name
                    i++
                }
                builder.setItems(
                    charList
                ) { dialog: DialogInterface?, item: Int ->
                    changeFilter(filters[item])
                }
                filterDialog = builder.show()
            } else {
                filterDialog!!.dismiss()
            }
        }*/

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    override fun onStop() {
        super.onStop()
        releaseCamera()
    }

    private fun releaseCamera() {
        if (sampleGLView != null) {
            sampleGLView!!.onPause()
        }
        if (cameraRecorder != null) {
            cameraRecorder!!.stop()
            cameraRecorder!!.release()
            cameraRecorder = null
        }
        if (sampleGLView != null) {
            (findViewById<View>(R.id.wrap_view) as FrameLayout).removeView(sampleGLView)
            sampleGLView = null
        }
    }

    private fun setUpCameraView() {
        runOnUiThread {
            val frameLayout = findViewById<FrameLayout>(R.id.wrap_view)
            frameLayout.removeAllViews()
            sampleGLView = null
            sampleGLView = SampleGLView(applicationContext)
            sampleGLView!!.setTouchListener { event: MotionEvent, width: Int, height: Int ->
                if (cameraRecorder == null) return@setTouchListener
                cameraRecorder!!.changeManualFocusPoint(event.x, event.y, width, height)
            }
            frameLayout.addView(sampleGLView)
        }
    }

    private fun setUpCamera() {
        setUpCameraView()
        cameraRecorder = CameraRecorderBuilder(this, sampleGLView) //.recordNoFilter(true)
            .cameraRecordListener(object : CameraRecordListener {
                override fun onGetFlashSupport(flashSupport: Boolean) {
                    runOnUiThread {
                        findViewById<View>(R.id.btn_flash).isEnabled = flashSupport
                    }
                }

                override fun onRecordComplete() {
                    exportMp4ToGallery(
                        applicationContext,
                        filepath
                    )

                }

                override fun onRecordStart() {}
                override fun onError(exception: Exception) {
                    Log.e("CameraRecorder", exception.toString())
                }

                override fun onCameraThreadFinish() {
                    if (toggleClick) {
                        runOnUiThread { setUpCamera() }
                    }
                    toggleClick = false
                }
            }) /*                .videoSize(videoWidth, videoHeight)
                .cameraSize(cameraWidth, cameraHeight)*/
            .lensFacing(lensFacing)
            .build()
    }

    private fun changeFilter(filters: Filters) {
        cameraRecorder!!.setFilter(Filters.getFilterInstance(filters, applicationContext))
    }

    private interface BitmapReadyCallbacks {
        fun onBitmapReady(bitmap: Bitmap?)
    }

    private fun captureBitmap(bitmapReadyCallbacks: BitmapReadyCallbacks) {
        sampleGLView!!.queueEvent {
            val egl = EGLContext.getEGL() as EGL10
            val gl = egl.eglGetCurrentContext().gl as GL10
            val snapshotBitmap = createBitmapFromGLSurface(
                sampleGLView!!.measuredWidth,
                sampleGLView!!.measuredHeight,
                gl
            )
            runOnUiThread { bitmapReadyCallbacks.onBitmapReady(snapshotBitmap) }
        }
    }

    private fun createBitmapFromGLSurface(w: Int, h: Int, gl: GL10): Bitmap? {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)
        try {
            gl.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            var texturePixel: Int
            var blue: Int
            var red: Int
            var pixel: Int
            for (i in 0 until h) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0 until w) {
                    texturePixel = bitmapBuffer[offset1 + j]
                    blue = texturePixel shr 16 and 0xff
                    red = texturePixel shl 16 and 0x00ff0000
                    pixel = texturePixel and -0xff0100 or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            Log.e("CreateBitmap", "createBitmapFromGLSurface: " + e.message, e)
            return null
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

    fun saveAsPngImage(bitmap: Bitmap, filePath: String?) {
        try {
            val file = File(filePath)
            val outStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun exportMp4ToGallery(
        context: Context,
        filePath: String?
    ) {
        val values = ContentValues(2)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.DATA, filePath)
        context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        )
        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://$filePath")
            )
        )
        Handler().postDelayed(Runnable {
            setVideoMode(filepath)
        },2000)
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 88888
        private const val REQUEST_VIDEO_TRIMMER = 999

        val videoFilePath: String
            get() = androidMoviesFolder
                .absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss")
                .format(Date()) + "cameraRecorder.mp4"

        val androidMoviesFolder: File
            get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)

        private fun exportPngToGallery(
            context: Context,
            filePath: String
        ) {
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            val f = File(filePath)
            val contentUri = Uri.fromFile(f)
            mediaScanIntent.data = contentUri
            context.sendBroadcast(mediaScanIntent)
        }

        val imageFilePath: String
            get() = androidImageFolder
                .absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss")
                .format(Date()) + "cameraRecorder.png"

        val androidImageFolder: File
            get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    }


    //gallery
    private fun pickFromGallery() {
            val intent = Intent()
            intent.setTypeAndNormalize("video/*")
            intent.action = Intent.ACTION_GET_CONTENT
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(
                Intent.createChooser(
                    intent,
                    getString(R.string.label_select_video)
                ),
                BaseCameraActivity.REQUEST_VIDEO_TRIMMER
            )
    }

    public override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == BaseCameraActivity.REQUEST_VIDEO_TRIMMER) {
                val selectedUri: Uri? = data!!.data
                if (selectedUri != null) {
                    val path= FileUtils.getPath(this, selectedUri)
                    setVideoMode(path)
                } else {
                    Toast.makeText(
                        this@BaseCameraActivity,
                        R.string.toast_cannot_retrieve_selected_video,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun manageViews(cameraMode:Boolean){
        if (cameraMode){
            layoutCameraMode.visibility=View.VISIBLE
            cameraButton.visibility=View.GONE
            videoView.visibility=View.GONE
        }else{
            layoutCameraMode.visibility=View.GONE
            cameraButton.visibility=View.VISIBLE
            videoView.visibility=View.VISIBLE
        }
    }
    private fun setVideoMode(path:String?){
        manageViews(false)
        videoView.setVideoURI(Uri.parse(path))
        System.out.println("Path is: "+path)
        videoView.start()
    }

}
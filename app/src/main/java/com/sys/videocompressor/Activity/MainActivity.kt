package com.sys.videocompressor.Activity

import ViewAdapter
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.StorageConfiguration
import com.sys.videocompressor.Activity.Utils.getFileSize
import com.sys.videocompressor.Activity.model.VideoDetailsModel
import com.sys.videocompressor.R
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_SELECT_VIDEO = 0
        const val REQUEST_CAPTURE_VIDEO = 1
    }

    private val uris = mutableListOf<Uri>()
    private val data = mutableListOf<VideoDetailsModel>()
    private lateinit var adapter: ViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setReadStoragePermission()

        pickVideo.setOnClickListener {
            pickvideo()
        }

        cancel.setOnClickListener {
            VideoCompressor.cancel()
        }

        val recyclerview = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerview.layoutManager = LinearLayoutManager(this)
        adapter = ViewAdapter(applicationContext, data)
        recyclerview.adapter = adapter
    }

    //Pick a video file from device
    private fun pickvideo() {
        val intent = Intent()
        intent.apply {
            type = "video/*"
            action = Intent.ACTION_PICK
        }
        intent.putExtra(
            Intent.EXTRA_ALLOW_MULTIPLE,
            false
        )
        startActivityForResult(Intent.createChooser(intent, "Select video"), REQUEST_SELECT_VIDEO)
    }


    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {

        reset()

        if (resultCode == Activity.RESULT_OK)
            if (requestCode == REQUEST_SELECT_VIDEO || requestCode == REQUEST_CAPTURE_VIDEO) {
                handleResult(intent)
            }

        super.onActivityResult(requestCode, resultCode, intent)
    }

    private fun handleResult(data: Intent?) {
        val clipData: ClipData? = data?.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val videoItem = clipData.getItemAt(i)
                uris.add(videoItem.uri)
            }
            processVideo()
        } else if (data != null && data.data != null) {
            val uri = data.data
            uris.add(uri!!)
            processVideo()
        }
    }


    private fun setReadStoragePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (!ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1
                )
            }
        }
    }
    private fun reset() {
        uris.clear()
        mainContents.visibility = View.GONE
        data.clear()
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("SetTextI18n")
    private fun processVideo() {
        mainContents.visibility = View.VISIBLE

        GlobalScope.launch {
            VideoCompressor.start(
                context = applicationContext,
                uris,
                isStreamable = true,
                storageConfiguration = StorageConfiguration(
                    saveAt = Environment.DIRECTORY_MOVIES,
                    isExternal = true,
                ),
                configureWith = Configuration(
                    quality = VideoQuality.LOW,
                    isMinBitrateCheckEnabled = true,
                ),
                listener = object : CompressionListener {
                    override fun onProgress(index: Int, percent: Float) {
                        //Update UI
                        if (percent <= 100 && percent.toInt() % 5 == 0)
                            runOnUiThread {
                                data[index] = VideoDetailsModel(
                                    "",
                                    uris[index],
                                    "",
                                    percent
                                )
                                adapter.notifyDataSetChanged()
                            }
                    }

                    override fun onStart(index: Int) {
                        data.add(
                            index,
                            VideoDetailsModel("", uris[index], "")
                        )
                        adapter.notifyDataSetChanged()
                    }

                    override fun onSuccess(index: Int, size: Long, path: String?) {
                        data[index] = VideoDetailsModel(
                            path,
                            uris[index],
                            getFileSize(size),
                            100F
                        )
                        adapter.notifyDataSetChanged()
                    }

                    override fun onFailure(index: Int, failureMessage: String) {
                        Log.wtf("failureMessage", failureMessage)
                    }

                    override fun onCancelled(index: Int) {
                        Log.wtf("TAG", "compression has been cancelled")
                        // make UI changes, cleanup, etc
                    }
                },
            )
        }
    }

}
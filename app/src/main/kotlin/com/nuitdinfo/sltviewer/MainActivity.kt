package com.nuitdinfo.sltviewer

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.nuitdinfo.sltviewer.databinding.ActivityMainBinding
import com.nuitdinfo.sltviewer.model.Model
import com.nuitdinfo.sltviewer.model.StlModel
import com.nuitdinfo.sltviewer.util.Util.closeSilently
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sampleModels: List<String>
    private var sampleModelIndex = 0
    private var modelView: ModelSurfaceView? = null

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK && it.data?.data != null) {
            val uri = it.data?.data
            grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            beginLoadModel(uri!!)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            beginOpenModel()
        } else {
            Toast.makeText(this, R.string.read_permission_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.enableEdgeToEdge(window)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.mainToolbar)

        binding.progressBar.isVisible = false

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val newStatusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val newNavBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val newCaptionBarInsets = insets.getInsets(WindowInsetsCompat.Type.captionBar())
            val newSystemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val topInset = max(max(max(newStatusBarInsets.top, newCaptionBarInsets.top), newSystemBarInsets.top), newNavBarInsets.top)
            val bottomInset = max(max(max(newStatusBarInsets.bottom, newCaptionBarInsets.bottom), newSystemBarInsets.bottom), newNavBarInsets.bottom)
            binding.mainToolbarContainer.updatePadding(top = topInset)

            binding.progressBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bottomInset
                leftMargin = newNavBarInsets.left
                rightMargin = newNavBarInsets.right
            }
            insets
        }

        sampleModels = assets.list("")!!.filter { it.endsWith(".stl") }

        if (intent.data != null && savedInstanceState == null) { beginLoadModel(intent.data!!)
        }
    }

    override fun onStart() {
        super.onStart()
        if (ModelViewerApplication.currentModel == null) {
            loadSampleModel()
        } else {
            createNewModelView(ModelViewerApplication.currentModel)
            ModelViewerApplication.currentModel?.let { title = it.title }
        }
    }

    override fun onPause() {
        super.onPause()
        modelView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        modelView?.onResume()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_open_model -> {
                checkReadPermissionThenOpen()
                true
            }
            R.id.menu_load_sample -> {
                loadSampleModel()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkReadPermissionThenOpen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            beginOpenModel()
        }
    }

    private fun beginOpenModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
        openDocumentLauncher.launch(intent)
    }

    private fun createNewModelView(model: Model?) {
        if (modelView != null) {
            binding.containerView.removeView(modelView)
        }
        modelView = ModelSurfaceView(this, model)
        binding.containerView.addView(modelView, 0)
    }

    private fun beginLoadModel(uri: Uri) {
        lifecycleScope.launch(CoroutineExceptionHandler { _, throwable ->
            throwable.printStackTrace()
            Toast.makeText(applicationContext, getString(R.string.open_model_error, throwable.message), Toast.LENGTH_SHORT).show()
        }) {
            binding.progressBar.isVisible = true

            var model: Model? = null
            withContext(Dispatchers.IO) {
                var stream: InputStream? = null
                try {
                    val cr = applicationContext.contentResolver
                    val fileName = getFileName(cr, uri)
                    stream = if ("http" == uri.scheme || "https" == uri.scheme) {
                        val client = OkHttpClient()
                        val request: Request = Request.Builder().url(uri.toString()).build()
                        val response = client.newCall(request).execute()

                        // TODO: figure out how to NOT need to read the whole file at once.
                        ByteArrayInputStream(response.body.bytes())
                    } else {
                        cr.openInputStream(uri)
                    }
                    if (stream != null) {
                        model = StlModel(stream)
                        model.title = fileName ?: "NIRD Model"
                    }
                    model
                } finally {
                    closeSilently(stream)
                }
            }
            model?.let {
                setCurrentModel(it)
            }
            binding.progressBar.isVisible = false
        }
    }

    private fun getFileName(cr: ContentResolver, uri: Uri): String? {
        if ("content" == uri.scheme) {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            ContentResolverCompat.query(cr, uri, projection, null, null, null, null as CancellationSignal?)?.use { metaCursor ->
                if (metaCursor.moveToFirst()) {
                    return metaCursor.getString(0)
                }
            }
        }
        return uri.lastPathSegment
    }

    private fun setCurrentModel(model: Model) {
        ModelViewerApplication.currentModel = model
        createNewModelView(model)
        title = model.title.ifEmpty {
            "NIRD Model"
        }
        binding.progressBar.isVisible = false

        val dimensions = "width %.1fcm height %.1fcm depth %.1fcm volume %.1fcm^3 ".format(model.width, model.height, model.depth, model.volume.div(1000).plus(1.2))

        Toast.makeText(applicationContext, dimensions, Toast.LENGTH_LONG).show()
    }

    private fun loadSampleModel() {
        try {
            val stream = assets.open(sampleModels.first())
            setCurrentModel(StlModel(stream))
            stream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}

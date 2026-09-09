package dev.ttshare

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ShareActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var detailsView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var primaryButton: Button
    private lateinit var secondaryButton: Button

    private var currentUrl: String? = null
    private var currentTaskId: String? = null
    private var currentJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)
        statusView = findViewById(R.id.status)
        detailsView = findViewById(R.id.details)
        progressBar = findViewById(R.id.progress)
        primaryButton = findViewById(R.id.primaryButton)
        secondaryButton = findViewById(R.id.secondaryButton)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val url: String? = when (intent.action) {
            Intent.ACTION_SEND -> Downloader.extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        val isShare: Boolean = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW

        if (url != null) {
            currentUrl = url
            startDownload(url)
        } else if (isShare) {
            Toast.makeText(this, R.string.status_no_link, Toast.LENGTH_LONG).show()
            finish()
        } else {
            showIdle()
        }
    }

    private fun showIdle() {
        statusView.setText(R.string.status_ready)
        progressBar.visibility = View.GONE
        detailsView.visibility = View.VISIBLE
        detailsView.text = getString(R.string.ytdlp_version, Downloader.engineVersion(this))
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.setText(R.string.btn_update)
        secondaryButton.setOnClickListener { runUpdate(thenRetry = false) }
        primaryButton.setText(R.string.btn_close)
        primaryButton.setOnClickListener { finish() }
    }

    private fun startDownload(url: String) {
        val taskId: String = UUID.randomUUID().toString()
        currentTaskId = taskId
        showDownloading(taskId)

        currentJob = lifecycleScope.launch {
            val app = application as TTShareApp
            val ready: Result<Unit> = app.engineReady.await()
            val outcome: Result<File> = ready.mapCatching {
                withContext(Dispatchers.IO) {
                    Downloader.download(url, Downloader.sharedDir(this@ShareActivity), taskId) { pct, line ->
                        lifecycleScope.launch { showProgress(pct, line) }
                    }
                }
            }
            outcome
                .onSuccess { file -> shareFile(file) }
                .onFailure { error -> showFailure(error) }
        }
    }

    private fun showDownloading(taskId: String) {
        statusView.setText(R.string.status_downloading)
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        detailsView.visibility = View.GONE
        secondaryButton.visibility = View.GONE
        primaryButton.setText(R.string.btn_cancel)
        primaryButton.setOnClickListener {
            currentJob?.cancel()
            Downloader.cancel(taskId)
            finish()
        }
    }

    private fun showProgress(percent: Float, line: String) {
        if (percent >= 0f) {
            progressBar.isIndeterminate = false
            progressBar.progress = percent.toInt()
        }
        detailsView.visibility = View.VISIBLE
        detailsView.text = line.trim()
    }

    private fun shareFile(file: File) {
        statusView.setText(R.string.status_done)
        val uri: Uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_MP4
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
        finish()
    }

    private fun showFailure(error: Throwable) {
        val isCancel: Boolean = error is YoutubeDL.CanceledException
        if (!isCancel) {
            statusView.setText(R.string.status_failed)
            progressBar.visibility = View.GONE
            detailsView.visibility = View.VISIBLE
            detailsView.text = Downloader.summarize(error.message ?: error.toString())
            secondaryButton.visibility = View.VISIBLE
            secondaryButton.setText(R.string.btn_update_retry)
            secondaryButton.setOnClickListener { runUpdate(thenRetry = true) }
            primaryButton.setText(R.string.btn_close)
            primaryButton.setOnClickListener { finish() }
        }
    }

    private fun runUpdate(thenRetry: Boolean) {
        statusView.setText(R.string.status_updating)
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        secondaryButton.visibility = View.GONE
        primaryButton.setText(R.string.btn_close)
        primaryButton.setOnClickListener { finish() }

        lifecycleScope.launch {
            val result: Result<String> = runCatching {
                withContext(Dispatchers.IO) { Downloader.updateEngine(this@ShareActivity) }
            }
            val url: String? = currentUrl
            result.onSuccess { version ->
                Toast.makeText(this@ShareActivity, getString(R.string.ytdlp_version, version), Toast.LENGTH_SHORT).show()
                if (thenRetry && url != null) {
                    startDownload(url)
                } else {
                    showIdle()
                }
            }.onFailure { error ->
                showFailure(error)
            }
        }
    }

    private companion object {
        const val MIME_MP4: String = "video/mp4"
    }
}

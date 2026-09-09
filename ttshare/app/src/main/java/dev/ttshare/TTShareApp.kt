package dev.ttshare

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TTShareApp : Application() {

    /** Completes once the bundled yt-dlp and FFmpeg binaries are unpacked and usable. */
    val engineReady: CompletableDeferred<Result<Unit>> = CompletableDeferred()

    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val result: Result<Unit> = runCatching {
                YoutubeDL.getInstance().init(this@TTShareApp)
                FFmpeg.getInstance().init(this@TTShareApp)
            }
            engineReady.complete(result)
        }
        appScope.launch {
            Downloader.pruneOldFiles(Downloader.sharedDir(this@TTShareApp))
        }
    }
}

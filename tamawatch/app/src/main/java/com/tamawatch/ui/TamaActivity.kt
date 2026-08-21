package com.tamawatch.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tamawatch.background.TickWorker
import com.tamawatch.tama
import com.tamawatch.ui.common.ProvidePixel

class TamaActivity : ComponentActivity() {

    private lateinit var vm: TamaViewModel

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = tama
        c.soundBank.preload()
        requestPerms()
        TickWorker.schedule(this)

        vm = ViewModelProvider(this, TamaViewModel.Factory(c.repository, c.settings))[TamaViewModel::class.java]

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            c.soundBank.enabled = settings.soundOn

            LaunchedEffect(Unit) {
                vm.repository.events.collect { e ->
                    c.soundBank.onEvent(e)
                    c.haptics.onEvent(e)
                }
            }

            // Keep the attention notification in step with the live pet state, so it
            // clears the instant a need is met (not just on the next background tick).
            LaunchedEffect(Unit) {
                vm.pet.collect { p -> p?.let { c.attentionNotifier.refresh(it) } }
            }

            ProvidePixel(c.spriteBank, settings.reduceMotion) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    WearApp(vm)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.tick()
        val c = tama
        if (c.stepSource.available) {
            c.stepSource.start { total -> vm.onStepTotal(total) }
        }
    }

    override fun onPause() {
        super.onPause()
        tama.stepSource.stop()
    }

    private fun requestPerms() {
        val list = buildList {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(list.toTypedArray())
    }
}

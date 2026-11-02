package com.example.listacompras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.listacompras.domain.repository.NewItemsTracker
import com.example.listacompras.domain.repository.RealtimeSync
import com.example.listacompras.ui.navigation.AppNavHost
import com.example.listacompras.ui.theme.ListaComprasTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var realtime: RealtimeSync

    @Inject
    lateinit var newItemsTracker: NewItemsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The live connection only runs while the app is visible; in the background the sync worker
        // takes over, and the next start pulls what was missed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { realtime.run() }
        }
        // What arrives while the app is on screen is seen; mark it so it is never notified afterwards.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                newItemsTracker.onAppVisible()
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { newItemsTracker.onAppHidden() }
                }
            }
        }
        setContent {
            ListaComprasTheme {
                AppNavHost()
            }
        }
    }
}
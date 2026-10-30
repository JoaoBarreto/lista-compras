package com.example.listacompras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.util.Log
import com.example.listacompras.domain.repository.RealtimeSync
import com.example.listacompras.domain.repository.SyncStatusProvider
import com.example.listacompras.ui.navigation.AppNavHost
import com.example.listacompras.ui.theme.ListaComprasTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var realtime: RealtimeSync

    @Inject
    lateinit var syncStatus: SyncStatusProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The live connection only runs while the app is visible; in the background the sync worker
        // takes over, and the next start pulls what was missed.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // run() never returns, so the two must be siblings.
                coroutineScope {
                    launch { realtime.run() }
                    // Temporary: logs the sync status until the list screen shows it (5.3).
                    if (BuildConfig.DEBUG) launch { syncStatus.status.collect { Log.d("SyncStatus", it.name) } }
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
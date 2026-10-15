package com.kidlearn.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.IntentCompat
import com.kidlearn.ui.blocks.LocalBlockRenderers
import com.kidlearn.ui.theme.KidLearnTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as KidLearnApplication).container
        val sharedPack = if (savedInstanceState == null) sharedPackUri(intent) else null
        setContent {
            KidLearnTheme {
                CompositionLocalProvider(LocalBlockRenderers provides container.blockRenderers) {
                    KidLearnNavHost(container, sharedPack)
                }
            }
        }
    }

    /** A pack opened from a file manager / chat app ("Open with KidLearn"). */
    private fun sharedPackUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}

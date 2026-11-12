package dev.ijemwohajo.signalsupervisor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ijemwohajo.signalsupervisor.ui.AppScaffold

class MainActivity : ComponentActivity() {
    private val app get() = application as SignalApp
    private val graph get() = app.graph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent { AppScaffold(graph, app.scenarios) }
    }

    override fun onStart() { super.onStart(); graph.appInForeground.value = true }
    override fun onStop() { super.onStop(); graph.appInForeground.value = false }
}

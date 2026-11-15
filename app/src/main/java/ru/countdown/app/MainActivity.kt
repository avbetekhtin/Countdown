package ru.countdown.app

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var remainingMs by mutableLongStateOf(0L)
    private var running by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val events = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                SleepTimerService.ACTION_TICK -> { remainingMs = intent.getLongExtra(SleepTimerService.EXTRA_REMAINING_MS, 0); running = true }
                SleepTimerService.ACTION_FINISHED -> { remainingMs = 0; running = false }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(events, IntentFilter().apply {
            addAction(SleepTimerService.ACTION_TICK)
            addAction(SleepTimerService.ACTION_FINISHED)
        }, RECEIVER_NOT_EXPORTED)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { CountdownTheme { CountdownScreen() } }
    }

    override fun onDestroy() { unregisterReceiver(events); super.onDestroy() }

    private fun startTimer(hours: Int, minutes: Int, seconds: Int) {
        val duration = (hours * 3_600L + minutes * 60L + seconds) * 1_000L
        if (duration <= 0) return
        remainingMs = duration
        running = true
        ContextCompat.startForegroundService(this, Intent(this, SleepTimerService::class.java).apply {
            action = SleepTimerService.ACTION_START
            putExtra(SleepTimerService.EXTRA_DURATION_MS, duration)
        })
    }

    private fun cancelTimer() { startService(Intent(this, SleepTimerService::class.java).setAction(SleepTimerService.ACTION_CANCEL)); running = false; remainingMs = 0 }

    private fun requestAdmin() {
        val component = ComponentName(this, SleepDeviceAdminReceiver::class.java)
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component))
    }

    @androidx.compose.runtime.Composable
    private fun CountdownScreen() {
        var hours by remember { mutableStateOf(0) }
        var minutes by remember { mutableStateOf(30) }
        var seconds by remember { mutableStateOf(0) }
        val isAdmin = getSystemService(DevicePolicyManager::class.java).isAdminActive(ComponentName(this, SleepDeviceAdminReceiver::class.java))
        val shown = if (running) SleepTimerService.format(remainingMs) else String.format("%02d:%02d:%02d", hours, minutes, seconds)
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF10111A)) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("COUNTDOWN", color = Color(0xFFA9AAFF), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(20.dp))
                Text("Таймер сна", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(44.dp))
                Text(shown, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
                if (!running) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeUnitControl("Часы", hours, 0..99) { hours = (hours + it).coerceIn(0, 99) }
                        TimeUnitControl("Минуты", minutes, 0..59) { minutes = (minutes + it).coerceIn(0, 59) }
                        TimeUnitControl("Секунды", seconds, 0..59) { seconds = (seconds + it).coerceIn(0, 59) }
                    }
                }
                Spacer(Modifier.height(42.dp))
                if (!isAdmin) {
                    OutlinedButton(onClick = ::requestAdmin, modifier = Modifier.fillMaxWidth()) { Text("Разрешить блокировку экрана") }
                    Spacer(Modifier.height(12.dp))
                }
                Button(onClick = { if (running) cancelTimer() else startTimer(hours, minutes, seconds) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6869F2))) {
                    Text(if (running) "Остановить таймер" else "Запустить таймер", fontSize = 17.sp)
                }
                if (running) TextButton(onClick = ::cancelTimer) { Text("Сбросить", color = Color(0xFFBFC0CE)) }
                Spacer(Modifier.height(18.dp))
                Text(if (isAdmin) "Экран будет заблокирован по окончании" else "Включите разрешение для блокировки экрана", color = Color(0xFF8D8E9E), fontSize = 13.sp)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun NumberButton(label: String, onClick: () -> Unit) { TextButton(onClick = onClick) { Text(label, color = Color.White, fontSize = 28.sp) } }

    @androidx.compose.runtime.Composable
    private fun TimeUnitControl(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFF8D8E9E), fontSize = 13.sp)
            NumberButton("+") { if (value < range.last) onChange(1) }
            Text(String.format("%02d", value), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            NumberButton("−") { if (value > range.first) onChange(-1) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun CountdownTheme(content: @androidx.compose.runtime.Composable () -> Unit) {
    MaterialTheme(content = content)
}

package app.zelgray.pills_in_time

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.zelgray.pills_in_time.notification.GroupRequest
import app.zelgray.pills_in_time.notification.OccurrenceRequest
import app.zelgray.pills_in_time.notification.StockRequest
import app.zelgray.pills_in_time.notification.toGroupRequestOrNull
import app.zelgray.pills_in_time.notification.toOccurrenceRequestOrNull
import app.zelgray.pills_in_time.notification.toStockRequestOrNull
import app.zelgray.pills_in_time.ui.navigation.MedTrackerNavHost
import app.zelgray.pills_in_time.ui.theme.MedTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not plain ComponentActivity) so AppCompatDelegate's
// per-app language override applies its base-context locale wrapping and
// auto-recreates on change — required for the compat path on API < 33.
// launchMode="singleTask" (manifest) keeps this the only instance, so a
// notification tap while the app is already running calls onNewIntent
// instead of stacking a duplicate activity.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var pendingOccurrenceRequest by mutableStateOf<OccurrenceRequest?>(null)
    private var pendingStockRequest by mutableStateOf<StockRequest?>(null)
    private var pendingGroupRequest by mutableStateOf<GroupRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOccurrenceRequest = intent?.toOccurrenceRequestOrNull()
        pendingStockRequest = intent?.toStockRequestOrNull()
        pendingGroupRequest = intent?.toGroupRequestOrNull()
        setContent {
            MedTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RequestNotificationPermission()
                    MedTrackerNavHost(
                        pendingOccurrenceRequest = pendingOccurrenceRequest,
                        onPendingOccurrenceConsumed = { pendingOccurrenceRequest = null },
                        pendingStockRequest = pendingStockRequest,
                        onPendingStockConsumed = { pendingStockRequest = null },
                        pendingGroupRequest = pendingGroupRequest,
                        onPendingGroupConsumed = { pendingGroupRequest = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOccurrenceRequest = intent.toOccurrenceRequestOrNull()
        pendingStockRequest = intent.toStockRequestOrNull()
        pendingGroupRequest = intent.toGroupRequestOrNull()
    }
}

@Composable
private fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

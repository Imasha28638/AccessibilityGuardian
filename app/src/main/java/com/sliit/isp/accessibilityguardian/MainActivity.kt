package com.sliit.isp.accessibilityguardian

import android.graphics.Typeface
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sliit.isp.accessibilityguardian.core.monitor.DeviceIntegrityMonitor
import com.sliit.isp.accessibilityguardian.databinding.ActivityMainBinding
import com.sliit.isp.accessibilityguardian.util.IntegrityBlocker
import com.sliit.isp.accessibilityguardian.util.PermissionUtils

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DESTINATION_ID = "destination_id"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ALERT_ID = "alert_id"
        private const val PREFS_NAME = "guardian_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private lateinit var binding: ActivityMainBinding

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val integrityResult = DeviceIntegrityMonitor(applicationContext).checkIntegrity()
        if (integrityResult.shouldBlock) {
            IntegrityBlocker.enforce(this, integrityResult)
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        // SAFER: get NavController from NavHostFragment (not Activity.findNavController)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Custom fixed nav clicks
        binding.navMonitor.setOnClickListener {
            navigateSafe(navController, R.id.navigation_monitor)
        }
        binding.navLogs.setOnClickListener {
            navigateSafe(navController, R.id.navigation_logs)
        }
        binding.navInspect.setOnClickListener {
            showLatestInspection(navController)
        }
        binding.navSettings.setOnClickListener {
            navigateSafe(navController, R.id.navigation_settings)
        }

        // Sync selected state
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateCustomNavSelection(destination.id)
        }

        // Initial selected state
        updateCustomNavSelection(navController.currentDestination?.id ?: R.id.navigation_monitor)
        handleIntentNavigation(navController, intent)

        if (isFirstLaunch()) {
            showWelcomePermissionDialog()
        }
    }

    private fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    private fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }

    private fun showWelcomePermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Welcome to Access Guard")
            .setMessage(
                "To detect accessibility abuse and suspicious behavior, this app needs several permissions such as Accessibility, Notification Access, Usage Access, and others."
            )
            .setCancelable(false)
            .setPositiveButton("Continue") { _, _ ->
                showAccessibilityDialog()
            }
            .setNegativeButton("Later") { _, _ ->
                setFirstLaunchCompleted()
            }
            .show()
    }

    private fun showAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Accessibility Protection")
            .setMessage(
                "Please enable the Access Guard accessibility service so the app can detect misuse of accessibility features."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionUtils.openAccessibilitySettings(this)
                showNotificationAccessDialog()
            }
            .setNegativeButton("Skip") { _, _ ->
                showNotificationAccessDialog()
            }
            .show()
    }

    private fun showNotificationAccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Notification Access")
            .setMessage(
                "Notification access helps correlate suspicious OTP, banking, and alert activity."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionUtils.openNotificationListenerSettings(this)
                showUsageAccessDialog()
            }
            .setNegativeButton("Skip") { _, _ ->
                showUsageAccessDialog()
            }
            .show()
    }

    private fun showUsageAccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Usage Access")
            .setMessage(
                "Usage access helps monitor foreground app behavior and identify risky app activity."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionUtils.openUsageAccessSettings(this)
                showOverlayDialog()
            }
            .setNegativeButton("Skip") { _, _ ->
                showOverlayDialog()
            }
            .show()
    }

    private fun showOverlayDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow Overlay Check")
            .setMessage(
                "Overlay permission helps identify apps drawing over other apps, which is commonly abused in fraud and malware attacks."
            )
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                PermissionUtils.openOverlaySettings(this)
                showPostNotificationsDialog()
            }
            .setNegativeButton("Skip") { _, _ ->
                showPostNotificationsDialog()
            }
            .show()
    }

    private fun showPostNotificationsDialog() {
        if (PermissionUtils.requiresPostNotificationPermission()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Allow Notifications")
                .setMessage(
                    "Enable notifications so Access Guard can warn you immediately when suspicious activity is detected."
                )
                .setCancelable(false)
                .setPositiveButton("Allow") { _, _ ->
                    PermissionUtils.requestPostNotifications(this)
                    setFirstLaunchCompleted()
                }
                .setNegativeButton("Skip") { _, _ ->
                    setFirstLaunchCompleted()
                }
                .show()
        } else {
            setFirstLaunchCompleted()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        handleIntentNavigation(navHostFragment.navController, intent)
    }

    private fun applySystemBarInsets() {
        val initialBottomPadding = binding.customBottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.customBottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                initialBottomPadding + systemBars.bottom
            )
            insets
        }
    }

    private fun navigateSafe(navController: NavController, destinationId: Int) {
        val currentId = navController.currentDestination?.id
        if (currentId == destinationId) return
        try {
            val startDestinationId = navController.graph.startDestinationId
            navController.navigate(
                destinationId,
                null,
                NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(startDestinationId, false, true)
                    .build()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showLatestInspection(navController: NavController) {
        navigateSafe(navController, R.id.navigation_inspect)
        supportFragmentManager.setFragmentResult(
            "inspect_request",
            Bundle().apply {
                putLong("selected_log_id", -1L)
            }
        )
    }

    private fun updateCustomNavSelection(destinationId: Int) {
        val active = ContextCompat.getColor(this, R.color.ag_accent)
        val inactive = ContextCompat.getColor(this, R.color.ag_text_secondary)

        setNavItemState(binding.iconMonitor, binding.textMonitor, false, inactive)
        setNavItemState(binding.iconLogs, binding.textLogs, false, inactive)
        setNavItemState(binding.iconInspect, binding.textInspect, false, inactive)
        setNavItemState(binding.iconSettings, binding.textSettings, false, inactive)

        when (destinationId) {
            R.id.navigation_monitor -> setNavItemState(binding.iconMonitor, binding.textMonitor, true, active)
            R.id.navigation_logs -> setNavItemState(binding.iconLogs, binding.textLogs, true, active)
            R.id.navigation_inspect -> setNavItemState(binding.iconInspect, binding.textInspect, true, active)
            R.id.navigation_settings -> setNavItemState(binding.iconSettings, binding.textSettings, true, active)
        }
    }

    private fun handleIntentNavigation(navController: NavController, launchIntent: android.content.Intent?) {
        val currentIntent = launchIntent ?: return
        val destinationId = currentIntent.getIntExtra(EXTRA_DESTINATION_ID, 0)
        if (destinationId == 0) return

        val args = Bundle().apply {
            currentIntent.getStringExtra(EXTRA_PACKAGE_NAME)?.let { putString(EXTRA_PACKAGE_NAME, it) }
            if (currentIntent.hasExtra(EXTRA_ALERT_ID)) {
                putLong(EXTRA_ALERT_ID, currentIntent.getLongExtra(EXTRA_ALERT_ID, 0L))
            }
        }

        navigateSafe(navController, destinationId)
        if (destinationId == R.id.navigation_inspect) {
            supportFragmentManager.setFragmentResult("inspect_request", args)
        }
        currentIntent.removeExtra(EXTRA_DESTINATION_ID)
    }

    private fun setNavItemState(
        icon: ImageView,
        label: TextView,
        selected: Boolean,
        color: Int
    ) {
        icon.setColorFilter(color)
        label.setTextColor(color)
        label.setTypeface(label.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }
}

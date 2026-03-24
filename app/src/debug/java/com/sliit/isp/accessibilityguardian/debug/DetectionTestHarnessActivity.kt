package com.sliit.isp.accessibilityguardian.debug

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sliit.isp.accessibilityguardian.R
import kotlinx.coroutines.launch

class DetectionTestHarnessActivity : AppCompatActivity() {

    private val viewModel: DetectionTestHarnessViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detection_test_harness)

        val btnBenignInteractionTest: MaterialButton = findViewById(R.id.btnBenignInteractionTest)
        val btnShowEnabledServices: MaterialButton = findViewById(R.id.btnShowEnabledServices)
        val btnRecentInstallAccessibility: MaterialButton = findViewById(R.id.btnRecentInstallAccessibility)
        val btnRapidUiAutomationTest: MaterialButton = findViewById(R.id.btnRapidUiAutomationTest)
        val btnOverlayTest: MaterialButton = findViewById(R.id.btnOverlayTest)
        val btnOtpContextTest: MaterialButton = findViewById(R.id.btnOtpContextTest)
        val btnRecalculateRiskTest: MaterialButton = findViewById(R.id.btnRecalculateRiskTest)
        val btnClearDetectionDataTest: MaterialButton = findViewById(R.id.btnClearDetectionDataTest)
        val btnBenignTapTarget: MaterialButton = findViewById(R.id.btnBenignTapTarget)
        val btnBenignDialog: MaterialButton = findViewById(R.id.btnBenignDialog)

        btnBenignInteractionTest.setOnClickListener { viewModel.runBenignInteractionTest() }
        btnShowEnabledServices.setOnClickListener { viewModel.showEnabledAccessibilityServices() }
        btnRecentInstallAccessibility.setOnClickListener { viewModel.runRecentInstallAccessibilityTest() }
        btnRapidUiAutomationTest.setOnClickListener { viewModel.runRapidUiAutomationTest() }
        btnOverlayTest.setOnClickListener { viewModel.runOverlayTest() }
        btnOtpContextTest.setOnClickListener { viewModel.runOtpContextTest() }
        btnRecalculateRiskTest.setOnClickListener { viewModel.runRecalculateRiskTest() }
        btnClearDetectionDataTest.setOnClickListener { viewModel.runClearDetectionDataTest() }
        btnBenignTapTarget.setOnClickListener { viewModel.onBenignTap() }
        btnBenignDialog.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Safe Debug Dialog")
                .setMessage("This benign dialog helps generate ordinary window changes without simulating abuse.")
                .setPositiveButton("Close", null)
                .show()
        }

        observeUiState()
    }

    private fun observeUiState() {
        val tvStageTitle: TextView = findViewById(R.id.tvStageTitle)
        val tvStageDescription: TextView = findViewById(R.id.tvStageDescription)
        val benignControlsContainer: LinearLayout = findViewById(R.id.benignControlsContainer)
        val otpStageContainer: LinearLayout = findViewById(R.id.otpStageContainer)
        val tvBenignInteractionStatus: TextView = findViewById(R.id.tvBenignInteractionStatus)

        val tvLatestDetectedPackage: TextView = findViewById(R.id.tvLatestDetectedPackage)
        val tvLatestRiskScore: TextView = findViewById(R.id.tvLatestRiskScore)
        val tvLatestSeverity: TextView = findViewById(R.id.tvLatestSeverity)
        val tvMatchedRules: TextView = findViewById(R.id.tvMatchedRules)
        val tvPackageOwnsService: TextView = findViewById(R.id.tvPackageOwnsService)
        val tvEnabledServices: TextView = findViewById(R.id.tvEnabledServices)
        val tvNewlyEnabledObserved: TextView = findViewById(R.id.tvNewlyEnabledObserved)
        val tvHarnessNotes: TextView = findViewById(R.id.tvHarnessNotes)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    tvStageTitle.text = state.stageTitle
                    tvStageDescription.text = state.stageDescription
                    benignControlsContainer.visibility = if (state.showBenignControls) android.view.View.VISIBLE else android.view.View.GONE
                    otpStageContainer.visibility = if (state.showOtpStage) android.view.View.VISIBLE else android.view.View.GONE
                    tvBenignInteractionStatus.text =
                        "Tap the controls and scroll this screen. Benign tap count: ${state.benignTapCount}"

                    tvLatestDetectedPackage.text = "Latest detected package: ${state.latestDetectedPackage}"
                    tvLatestRiskScore.text = "Latest risk score: ${state.latestRiskScore}"
                    tvLatestSeverity.text = "Latest severity: ${state.latestSeverity}"
                    tvMatchedRules.text = "Matched rules: ${state.matchedRules}"
                    tvPackageOwnsService.text =
                        "Package owns enabled accessibility service: ${state.packageOwnsAccessibilityService}"
                    tvEnabledServices.text =
                        "Enabled accessibility services: ${state.enabledAccessibilityServices}"
                    tvNewlyEnabledObserved.text =
                        "Newly enabled packages observed: ${state.newlyEnabledPackagesObserved}"
                    tvHarnessNotes.text = state.notes
                }
            }
        }
    }
}

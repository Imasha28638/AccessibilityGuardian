package com.sliit.isp.accessibilityguardian

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.sliit.isp.accessibilityguardian.databinding.ActivityBlockedBinding
import com.sliit.isp.accessibilityguardian.util.AppTermination

class BlockedActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reasonText = intent.getStringExtra(EXTRA_REASON_TEXT).orEmpty()
        bindReason(binding.tvReasonDetails, reasonText)

        binding.btnCloseApp.setOnClickListener {
            AppTermination.close(this)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AppTermination.close(this@BlockedActivity)
            }
        })
    }

    private fun bindReason(reasonView: TextView, reasonText: String) {
        if (reasonText.isBlank()) {
            reasonView.visibility = View.GONE
            return
        }
        reasonView.visibility = View.VISIBLE
        reasonView.text = reasonText
    }

    companion object {
        private const val EXTRA_REASON_TEXT = "reason_text"

        fun createIntent(context: Context, detailText: String): Intent {
            return Intent(context, BlockedActivity::class.java).apply {
                putExtra(EXTRA_REASON_TEXT, detailText)
            }
        }

        fun createIntent(context: Context, result: com.sliit.isp.accessibilityguardian.core.monitor.DeviceIntegrityResult): Intent {
            return createIntent(context, result.detailText)
        }
    }
}

package com.rsgd.cvrlogger

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.rsgd.cvrlogger.databinding.FragmentFirstBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        startBootAnimation()
        startTechInfoAnimation()
    }

    private fun startBootAnimation() {
        val bootSequence = listOf(
            ">>> INITIALIZING CVR-CORE...",
            ">>> LOADING APPLICATION MODULES...",
            ">>> SCANNING LOCAL STORAGE...",
            ">>> SYNCING DATABASE...",
            ">>> FETCHING SESSION LOGS...",
            ">>> DECRYPTING ENTITY DATA...",
            ">>> UPLINK STABLE. SYSTEM READY."
        )
        
        var seqIndex = 0
        val fullText = StringBuilder()
        
        val sequenceRunnable = object : Runnable {
            override fun run() {
                if (seqIndex < bootSequence.size) {
                    if (fullText.isNotEmpty()) fullText.append("\n")
                    fullText.append(bootSequence[seqIndex])
                    binding.tvTerminal.text = fullText.toString()
                    seqIndex++
                    handler.postDelayed(this, 300)
                }
            }
        }
        handler.post(sequenceRunnable)

        val progressAnimator = ObjectAnimator.ofInt(binding.progressLoading, "progress", 0, 100).apply {
            duration = 2400 
            interpolator = DecelerateInterpolator()
        }

        progressAnimator.addUpdateListener {
            val progress = it.animatedValue as Int
            binding.tvProgressPercent.text = "$progress%"
        }

        progressAnimator.start()

        handler.postDelayed({
            if (isAdded) {
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
        }, 3000)
    }

    private fun startTechInfoAnimation() {
        val techInfoRunnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                
                // Update UTC Time
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val utcTime = sdf.format(Date())
                
                // Randomized Coordinates (Simulating lock-on)
                val lat = String.format(Locale.US, "%.3f", Random.nextDouble(20.0, 50.0))
                val lon = String.format(Locale.US, "%.3f", Random.nextDouble(-120.0, 120.0))
                
                val status = if (binding.progressLoading.progress < 100) "INITIALIZING" else "STABLE"
                
                binding.tvTechInfo.text = "UTC: $utcTime | LOC: $lat, $lon | STATUS: $status"
                
                // Fast flicker/update effect
                handler.postDelayed(this, 100)
            }
        }
        handler.post(techInfoRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

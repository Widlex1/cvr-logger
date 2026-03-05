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
    }

    private fun startBootAnimation() {
        val bootSequence = listOf(
            ">>> INITIALIZING KERNEL...",
            ">>> LOADING NEURAL MODULES...",
            ">>> DECRYPTING FILE SYSTEM...",
            ">>> LOCAL STORAGE READY...",
            ">>> WAKING GEMMA...",
            ">>> SYSTEM READY."
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
                    handler.postDelayed(this, 350)
                }
            }
        }
        handler.post(sequenceRunnable)

        val progressBar = binding.progressLoading
        val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = 2400 
            interpolator = DecelerateInterpolator()
        }

        val percentageText = binding.tvProgressPercent
        progressAnimator.addUpdateListener {
            val progress = it.animatedValue as Int
            percentageText.text = "$progress%"
        }

        progressAnimator.start()

        handler.postDelayed({
            if (isAdded) {
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
        }, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

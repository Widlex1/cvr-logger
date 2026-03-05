package com.rsgd.cuteeediary

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
import com.rsgd.cuteeediary.databinding.FragmentFirstBinding

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
        val progressBar = binding.progressLoading
        val progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100).apply {
            duration = 1800 
            interpolator = DecelerateInterpolator()
        }

        val percentageText = binding.tvProgressPercent
        progressAnimator.addUpdateListener {
            val progress = it.animatedValue as Int
            percentageText.text = "$progress%"
        }

        progressAnimator.start()

        // Automatically navigate to the Second screen (Log List) after boot
        handler.postDelayed({
            if (isAdded) {
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
            }
        }, 2100)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}

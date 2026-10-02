package com.shinydiscoballsdev.kifossk

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private lateinit var editTextUrl: EditText
    private lateinit var switchScreenOn: SwitchCompat
    private lateinit var switchKeepAlive: SwitchCompat
    private lateinit var switchShowNotification: SwitchCompat
    private lateinit var spinnerOrientation: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnReload: Button

    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private val swipeThreshold by lazy { resources.displayMetrics.widthPixels * 0.25f }

    var onDismiss: (() -> Unit)? = null
    var onReload: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editTextUrl = view.findViewById(R.id.editTextUrl)
        switchScreenOn = view.findViewById(R.id.switchScreenOn)
        switchKeepAlive = view.findViewById(R.id.switchKeepAlive)
        switchShowNotification = view.findViewById(R.id.switchShowNotification)
        spinnerOrientation = view.findViewById(R.id.spinnerOrientation)
        spinnerTheme = view.findViewById(R.id.spinnerTheme)
        btnSave = view.findViewById(R.id.buttonSave)
        btnReload = view.findViewById(R.id.buttonReload)

        val ctx = requireContext()
        editTextUrl.setText(KioskPrefs.getUrl(ctx))
        switchScreenOn.isChecked = KioskPrefs.getScreenOn(ctx)
        switchKeepAlive.isChecked = KioskPrefs.getKeepAlive(ctx)
        switchShowNotification.isChecked = KioskPrefs.getShowNotification(ctx)

        switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            KioskPrefs.setKeepAlive(ctx, isChecked)
            if (isChecked) {
                KeepAliveService.start(ctx)
            } else {
                KeepAliveService.stop(ctx)
            }
        }

        switchShowNotification.setOnCheckedChangeListener { _, isChecked ->
            KioskPrefs.setShowNotification(ctx, isChecked)
            if (isChecked && KioskPrefs.getKeepAlive(ctx)) {
                KeepAliveService.start(ctx)
            } else {
                KeepAliveService.stop(ctx)
            }
        }

        ArrayAdapter.createFromResource(
            ctx,
            R.array.orientation_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerOrientation.adapter = adapter
            val savedOrientation = KioskPrefs.getOrientation(ctx)
            val orientationMap = mapOf("landscape" to 0, "portrait" to 1, "auto" to 2)
            spinnerOrientation.setSelection(orientationMap[savedOrientation] ?: 2)
        }

        ArrayAdapter.createFromResource(
            ctx,
            R.array.theme_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTheme.adapter = adapter
            val savedTheme = KioskPrefs.getTheme(ctx)
            val themeMap = mapOf("dark" to 0, "light" to 1)
            spinnerTheme.setSelection(themeMap[savedTheme] ?: 0)
        }

        spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val themeMap = mapOf(0 to "dark", 1 to "light")
                val selectedTheme = themeMap[position] ?: "dark"
                val currentTheme = KioskPrefs.getTheme(ctx)
                if (selectedTheme != currentTheme) {
                    KioskPrefs.setTheme(ctx, selectedTheme)
                    activity?.recreate()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(ctx, "Settings saved!", Toast.LENGTH_SHORT).show()
        }

        btnReload.setOnClickListener {
            saveSettings()
            onReload?.invoke()
            dismiss()
        }

        view.setBackgroundColor(
            if (KioskPrefs.getTheme(ctx) == "light") 0xFFF5F5F5.toInt() else 0xFF1A1A2E.toInt()
        )
    }

    fun onBackPressed(): Boolean {
        saveSettings()
        dismiss()
        return true
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                isDragging = false
                view?.animate()?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - startX
                val deltaY = event.rawY - startY
                if (!isDragging && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 20) {
                    isDragging = true
                }
                if (isDragging) {
                    view?.translationX = deltaX
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val deltaX = event.rawX - startX
                    val v = view ?: return false
                    if (kotlin.math.abs(deltaX) > swipeThreshold) {
                        val endX = if (deltaX > 0) v.width.toFloat() else -v.width.toFloat()
                        v.animate()
                            .translationX(endX)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                saveSettings()
                                dismiss()
                            }
                            .start()
                    } else {
                        v.animate()
                            .translationX(0f)
                            .setDuration(150)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }
                    isDragging = false
                    return true
                }
            }
        }
        return false
    }

    private fun dismiss() {
        onDismiss?.invoke()
    }

    private fun saveSettings() {
        val ctx = requireContext()
        KioskPrefs.setUrl(ctx, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(ctx, switchScreenOn.isChecked)
        KioskPrefs.setKeepAlive(ctx, switchKeepAlive.isChecked)
        KioskPrefs.setShowNotification(ctx, switchShowNotification.isChecked)
        val orientationMap = mapOf(0 to "landscape", 1 to "portrait", 2 to "auto")
        KioskPrefs.setOrientation(ctx, orientationMap[spinnerOrientation.selectedItemPosition] ?: "auto")
        val themeMap = mapOf(0 to "dark", 1 to "light")
        KioskPrefs.setTheme(ctx, themeMap[spinnerTheme.selectedItemPosition] ?: "dark")
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
package com.shinydiscoballsdev.kifossk

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var editTextUrl: EditText
    private lateinit var switchScreenOn: SwitchCompat
    private lateinit var spinnerOrientation: Spinner
    private lateinit var spinnerTheme: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnReload: Button

    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private val swipeThreshold by lazy { resources.displayMetrics.widthPixels * 0.25f }
    private val contentView by lazy { window.decorView.findViewById<View>(android.R.id.content) }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editTextUrl = findViewById(R.id.editTextUrl)
        switchScreenOn = findViewById(R.id.switchScreenOn)
        spinnerOrientation = findViewById(R.id.spinnerOrientation)
        spinnerTheme = findViewById(R.id.spinnerTheme)
        btnSave = findViewById(R.id.buttonSave)
        btnReload = findViewById(R.id.buttonReload)

        editTextUrl.setText(KioskPrefs.getUrl(this))
        switchScreenOn.isChecked = KioskPrefs.getScreenOn(this)

        ArrayAdapter.createFromResource(
            this,
            R.array.orientation_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerOrientation.adapter = adapter
            val savedOrientation = KioskPrefs.getOrientation(this)
            val orientationMap = mapOf("landscape" to 0, "portrait" to 1, "auto" to 2)
            spinnerOrientation.setSelection(orientationMap[savedOrientation] ?: 2)
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.theme_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTheme.adapter = adapter
            val savedTheme = KioskPrefs.getTheme(this)
            val themeMap = mapOf("dark" to 0, "light" to 1)
            spinnerTheme.setSelection(themeMap[savedTheme] ?: 0)
        }

        spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val themeMap = mapOf(0 to "dark", 1 to "light")
                val selectedTheme = themeMap[position] ?: "dark"
                val currentTheme = KioskPrefs.getTheme(this@SettingsActivity)
                if (selectedTheme != currentTheme) {
                    KioskPrefs.setTheme(this@SettingsActivity, selectedTheme)
                    recreate()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        }

        btnReload.setOnClickListener {
            saveSettings()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra(MainActivity.EXTRA_RELOAD, true)
            startActivity(intent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveSettings()
                finish()
            }
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                isDragging = false
                contentView.animate().cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - startX
                val deltaY = event.rawY - startY
                if (!isDragging && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 20) {
                    isDragging = true
                }
                if (isDragging) {
                    contentView.translationX = deltaX
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val deltaX = event.rawX - startX
                    if (kotlin.math.abs(deltaX) > swipeThreshold) {
                        val endX = if (deltaX > 0) contentView.width.toFloat() else -contentView.width.toFloat()
                        contentView.animate()
                            .translationX(endX)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                saveSettings()
                                finish()
                                overridePendingTransition(0, 0)
                            }
                            .start()
                    } else {
                        contentView.animate()
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
        return super.dispatchTouchEvent(event)
    }

    private fun applyTheme() {
        val theme = KioskPrefs.getTheme(this)
        setTheme(if (theme == "light") R.style.Theme_KioskViewer_Light else R.style.Theme_KioskViewer)
    }

    private fun saveSettings() {
        KioskPrefs.setUrl(this, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(this, switchScreenOn.isChecked)
        val orientationMap = mapOf(0 to "landscape", 1 to "portrait", 2 to "auto")
        KioskPrefs.setOrientation(this, orientationMap[spinnerOrientation.selectedItemPosition] ?: "auto")
        val themeMap = mapOf(0 to "dark", 1 to "light")
        KioskPrefs.setTheme(this, themeMap[spinnerTheme.selectedItemPosition] ?: "dark")
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
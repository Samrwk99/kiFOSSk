package com.shinydiscoballsdev.kifossk

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    private lateinit var btnSave: Button
    private lateinit var btnReload: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editTextUrl = findViewById(R.id.editTextUrl)
        switchScreenOn = findViewById(R.id.switchScreenOn)
        spinnerOrientation = findViewById(R.id.spinnerOrientation)
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

        // Back press on Settings → move app to background, preserve state
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveSettings()
                moveTaskToBack(false)
            }
        })
    }

    private fun saveSettings() {
        KioskPrefs.setUrl(this, editTextUrl.text.toString())
        KioskPrefs.setScreenOn(this, switchScreenOn.isChecked)
        val orientationMap = mapOf(0 to "landscape", 1 to "portrait", 2 to "auto")
        KioskPrefs.setOrientation(this, orientationMap[spinnerOrientation.selectedItemPosition] ?: "auto")
    }

    override fun onPause() {
        super.onPause()
        saveSettings()
    }
}
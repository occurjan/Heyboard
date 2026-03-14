package com.heyboard.teachingassistant

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {

    private val languageCodes = arrayOf("zh", "en")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // 版本号
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            findViewById<TextView>(R.id.tvVersion).text = pInfo.versionName
        } catch (_: Exception) {
            findViewById<TextView>(R.id.tvVersion).text = "1.0"
        }

        // 语言下拉列表
        val languageNames = arrayOf(getString(R.string.lang_zh), getString(R.string.lang_en))
        val spinner = findViewById<Spinner>(R.id.spinnerLanguage)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, languageNames) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).textSize = 15f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).textSize = 15f
                view.setPadding(32, 24, 32, 24)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // 设置当前语言选中项
        val currentLocale = AppCompatDelegate.getApplicationLocales()
        val currentLang = if (currentLocale.isEmpty) {
            resources.configuration.locales[0].language
        } else {
            currentLocale[0]?.language ?: "zh"
        }
        val currentIndex = languageCodes.indexOf(currentLang)
        if (currentIndex >= 0) {
            spinner.setSelection(currentIndex)
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = languageCodes[position]
                val appLocale = LocaleListCompat.forLanguageTags(selectedLang)
                val current = AppCompatDelegate.getApplicationLocales()
                if (current.isEmpty || current[0]?.language != selectedLang) {
                    AppCompatDelegate.setApplicationLocales(appLocale)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}

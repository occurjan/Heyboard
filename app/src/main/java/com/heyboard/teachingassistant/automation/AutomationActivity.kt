package com.heyboard.teachingassistant.automation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.heyboard.teachingassistant.R

class AutomationActivity : AppCompatActivity() {

    private lateinit var rvScenarios: RecyclerView
    private lateinit var tvEmpty: TextView
    private var scenarios = mutableListOf<AutomationScenario>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_automation)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        rvScenarios = findViewById(R.id.rvScenarios)
        tvEmpty = findViewById(R.id.tvEmpty)
        rvScenarios.layoutManager = LinearLayoutManager(this)
        rvScenarios.adapter = ScenarioAdapter()

        findViewById<MaterialButton>(R.id.btnAddScenario).setOnClickListener {
            startActivity(Intent(this, ScenarioDetailActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadScenarios()
    }

    private fun loadScenarios() {
        scenarios = AutomationRepository.getScenarios(this)
        rvScenarios.adapter?.notifyDataSetChanged()
        tvEmpty.visibility = if (scenarios.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class ScenarioAdapter : RecyclerView.Adapter<ScenarioAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvScenarioName)
            val tvTrigger: TextView = view.findViewById(R.id.tvTrigger)
            val tvActionCount: TextView = view.findViewById(R.id.tvActionCount)
            val btnEdit: MaterialButton = view.findViewById(R.id.btnEdit)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scenario, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val scenario = scenarios[position]
            holder.tvName.text = scenario.name.ifEmpty { getString(R.string.unnamed_scenario) }
            holder.tvTrigger.text = if (scenario.trigger == "on_start")
                getString(R.string.trigger_on_start) else getString(R.string.trigger_on_close)
            holder.tvActionCount.text = getString(R.string.action_count, scenario.actions.size)
            holder.btnEdit.setOnClickListener {
                val intent = Intent(this@AutomationActivity, ScenarioDetailActivity::class.java)
                intent.putExtra("scenario_id", scenario.id)
                startActivity(intent)
            }
        }

        override fun getItemCount() = scenarios.size
    }
}

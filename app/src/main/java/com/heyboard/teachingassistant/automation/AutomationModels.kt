package com.heyboard.teachingassistant.automation

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SerialConfig(
    var baudRate: Int = 9600,
    var dataBits: Int = 8,
    var stopBits: String = "1",
    var parity: String = "None",
    var flowControl: String = "None",
    var serialData: String = "",
    var dataFormat: String = "HEX"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("baudRate", baudRate)
        put("dataBits", dataBits)
        put("stopBits", stopBits)
        put("parity", parity)
        put("flowControl", flowControl)
        put("serialData", serialData)
        put("dataFormat", dataFormat)
    }

    companion object {
        fun fromJson(json: JSONObject): SerialConfig = SerialConfig(
            baudRate = json.optInt("baudRate", 9600),
            dataBits = json.optInt("dataBits", 8),
            stopBits = json.optString("stopBits", "1"),
            parity = json.optString("parity", "None"),
            flowControl = json.optString("flowControl", "None"),
            serialData = json.optString("serialData", ""),
            dataFormat = json.optString("dataFormat", "HEX")
        )
    }
}

data class ScenarioAction(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var type: String = "serial_command",
    var serialConfig: SerialConfig = SerialConfig()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("serialConfig", serialConfig.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): ScenarioAction = ScenarioAction(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            type = json.optString("type", "serial_command"),
            serialConfig = SerialConfig.fromJson(json.optJSONObject("serialConfig") ?: JSONObject())
        )
    }
}

data class AutomationScenario(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var trigger: String = "on_start",
    var actions: MutableList<ScenarioAction> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("trigger", trigger)
        put("actions", JSONArray().apply {
            actions.forEach { put(it.toJson()) }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): AutomationScenario = AutomationScenario(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", ""),
            trigger = json.optString("trigger", "on_start"),
            actions = mutableListOf<ScenarioAction>().apply {
                val arr = json.optJSONArray("actions") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    add(ScenarioAction.fromJson(arr.getJSONObject(i)))
                }
            }
        )
    }
}

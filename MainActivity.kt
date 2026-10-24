package com.example.artemismobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var instruction: EditText
    private lateinit var screenConsent: CheckBox
    private lateinit var status: TextView
    private lateinit var proposedAction: TextView
    private var pendingAction: JSONObject? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = "Artemis Mobile"
            textSize = 28f
        })
        content.addView(TextView(this).apply {
            text = "Prototipo de automatización asistida por IA · Android 11+"
            textSize = 14f
            setPadding(0, 6, 0, 20)
        })
        content.addView(label("Proveedor compatible con OpenAI API"))
        apiKey = EditText(this).apply {
            hint = "API key (no se guarda)"
            inputType = 129 // textPassword
            singleLine = true
        }
        content.addView(apiKey, matchWrap())
        content.addView(label("Modelo"))
        model = EditText(this).apply { setText("gpt-4o-mini"); singleLine = true }
        content.addView(model, matchWrap())
        content.addView(label("Instrucción"))
        instruction = EditText(this).apply {
            hint = "Ej.: abre Ajustes y busca Wi‑Fi"
            minLines = 2
            gravity = Gravity.TOP
        }
        content.addView(instruction, matchWrap())
        screenConsent = CheckBox(this).apply {
            text = "Acepto enviar al proveedor de IA el texto visible de pantalla. Puede contener datos privados."
            isChecked = false
        }
        content.addView(screenConsent)
        val analyze = Button(this).apply {
            text = "Analizar y proponer una acción"
            setOnClickListener { analyzeInstruction() }
        }
        content.addView(analyze)
        val settings = Button(this).apply {
            text = "Abrir ajustes de accesibilidad"
            setOnClickListener { startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        content.addView(settings)
        proposedAction = TextView(this).apply {
            textSize = 16f
            setPadding(0, 18, 0, 6)
        }
        content.addView(proposedAction)
        val execute = Button(this).apply {
            text = "Confirmar y ejecutar acción"
            setOnClickListener { confirmAction() }
        }
        content.addView(execute)
        status = TextView(this).apply {
            text = "Estado: listo. No se ejecuta ninguna acción sin tu confirmación."
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "\nPrivacidad y límites: la API key se usa solo en memoria durante esta sesión. El texto visible solo se envía si marcas la casilla. Cada respuesta de IA se trata como una propuesta, no como una orden automática. No uses esta app para contraseñas, pagos, banca ni acciones irreversibles."
            textSize = 13f
        })
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, 10, 0, 2)
    }

    private fun matchWrap() = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun analyzeInstruction() {
        val key = apiKey.text.toString().trim()
        val task = instruction.text.toString().trim()
        val modelName = model.text.toString().trim().ifBlank { "gpt-4o-mini" }
        if (key.isBlank() || task.isBlank()) {
            status.text = "Introduce una API key y una instrucción. La app no proporciona una API key."
            return
        }
        val screen = if (screenConsent.isChecked) ArtemisAccessibilityService.currentScreenText() else "No autorizado: no enviar texto de pantalla."
        status.text = "Consultando IA…"
        proposedAction.text = ""
        pendingAction = null
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(instruction.windowToken, 0)
        worker.execute {
            try {
                val action = requestAction(key, modelName, task, screen)
                runOnUiThread {
                    pendingAction = action
                    proposedAction.text = "Propuesta de IA:\n" + action.toString(2)
                    status.text = "Revisa la acción. No se ha ejecutado todavía."
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error al consultar IA: ${e.message ?: "error desconocido"}" }
            }
        }
    }

    private fun requestAction(key: String, modelName: String, task: String, screen: String): JSONObject {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val system = """Eres un planificador conservador de automatización Android. Devuelve SOLO un objeto JSON con campos: action, target, text, reason. action debe ser exactamente una de: tap_text, type_text, back, home, scroll_forward, none. target es un texto visible exacto para tap_text o el campo a enfocar para type_text; text es el texto a escribir en type_text. Propón solo UN paso por respuesta. No ejecutes ni propongas pagos, compras, envío de mensajes, cambios de seguridad, contraseñas, eliminación de datos o acciones irreversibles; en esos casos usa action=none. Si falta contexto usa action=none y explica en reason. Nunca afirmes que una acción ya se realizó."""
        val user = "Instrucción del usuario: $task\nTexto visible de pantalla (solo si fue autorizado):\n${screen.take(9000)}"
        val body = JSONObject()
            .put("model", modelName)
            .put("temperature", 0.1)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${response.take(500)}")
            val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val action = JSONObject(content)
            val allowed = setOf("tap_text", "type_text", "back", "home", "scroll_forward", "none")
            if (action.optString("action") !in allowed) throw IllegalArgumentException("La IA devolvió una acción no permitida")
            return action
        } finally { conn.disconnect() }
    }

    private fun confirmAction() {
        val action = pendingAction ?: run { status.text = "Primero analiza una instrucción."; return }
        if (action.optString("action") == "none") {
            status.text = "No hay acción ejecutable: ${action.optString("reason", "contexto insuficiente")}"; return
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmar acción")
            .setMessage("Se propone:\n${action.toString(2)}\n\n¿Quieres ejecutarla ahora?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Ejecutar") { _, _ ->
                val service = ArtemisAccessibilityService.instance
                if (service == null) status.text = "Activa Artemis Mobile en Ajustes > Accesibilidad y vuelve a intentarlo."
                else {
                    val result = service.executeApprovedAction(action)
                    status.text = result
                    pendingAction = null
                }
            }.show()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}

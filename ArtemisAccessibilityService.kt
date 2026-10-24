package com.example.artemismobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject

/** Servicio local. El texto de pantalla permanece en memoria y solo se envía tras consentimiento explícito. */
class ArtemisAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: ArtemisAccessibilityService? = null
            private set
        @Volatile private var screenText: String = ""
        fun currentScreenText(): String = screenText
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        refreshScreenText()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        refreshScreenText()
    }

    private fun refreshScreenText() {
        val root = rootInActiveWindow ?: return
        val out = StringBuilder()
        collectText(root, out, 0)
        screenText = out.toString().take(12000)
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int) {
        if (node == null || depth > 30 || out.length > 12000) return
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out.append(text).append('\n')
        if (desc.isNotEmpty() && desc != text) out.append(desc).append('\n')
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
    }

    fun executeApprovedAction(action: JSONObject): String {
        return try {
            when (action.optString("action")) {
                "tap_text" -> {
                    val target = action.optString("target").trim()
                    if (target.isBlank()) return "Acción cancelada: falta el texto objetivo."
                    val node = findNode(rootInActiveWindow, target)
                        ?: return "No encontré el texto '$target' en la pantalla actual. No se ejecutó nada."
                    val clickable = findClickableAncestor(node) ?: node
                    val ok = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (ok) "Pulsación ejecutada sobre '$target'. Revisa el resultado antes de continuar." else "Android rechazó la pulsación sobre '$target'."
                }
                "type_text" -> {
                    val target = action.optString("target").trim()
                    val value = action.optString("text")
                    if (value.isBlank()) return "Acción cancelada: no hay texto para escribir."
                    val root = rootInActiveWindow ?: return "No hay una ventana activa."
                    val field = if (target.isBlank()) findFocusedEditable(root) else findEditableByText(root, target)
                    if (field == null) return "No encontré un campo de texto editable. No se escribió nada."
                    field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
                    if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Texto escrito. Verifica el contenido antes de continuar." else "Android no permitió escribir en ese campo."
                }
                "back" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "Atrás ejecutado." else "No se pudo ejecutar Atrás."
                "home" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "Inicio ejecutado." else "No se pudo ir a Inicio."
                "scroll_forward" -> {
                    val root = rootInActiveWindow ?: return "No hay una ventana activa."
                    if (root.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) "Desplazamiento ejecutado." else "No se encontró un área desplazable compatible."
                }
                else -> "Acción no permitida."
            }
        } catch (e: Exception) { "Error al ejecutar la acción: ${e.message ?: "desconocido"}" }
    }

    private fun findNode(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.equals(target, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.equals(target, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findNode(node.getChild(i), target)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(8) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    private fun findEditableByText(node: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val matches = node.text?.toString()?.contains(target, ignoreCase = true) == true ||
            node.contentDescription?.toString()?.contains(target, ignoreCase = true) == true ||
            node.hintText?.toString()?.contains(target, ignoreCase = true) == true
        if (node.isEditable && (target.isBlank() || matches)) return node
        for (i in 0 until node.childCount) {
            val found = findEditableByText(node.getChild(i), target)
            if (found != null) return found
        }
        return null
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) return node
        for (i in 0 until node.childCount) {
            val found = findFocusedEditable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
}

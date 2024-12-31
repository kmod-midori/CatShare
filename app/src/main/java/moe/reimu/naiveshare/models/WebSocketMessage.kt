package moe.reimu.naiveshare.models

import org.json.JSONObject
import java.util.regex.Pattern

data class WebSocketMessage(
    val type: String, val id: Int, val name: String, val payload: JSONObject?
) {
    fun toText(newId: Int? = null): String {
        val sb = StringBuilder()
        sb.append(type)
        sb.append(':')
        if (newId != null) {
            sb.append(newId)
        } else {
            sb.append(id)
        }
        sb.append(':')
        sb.append(name)
        if (payload != null) {
            sb.append('?')
            sb.append(payload)
        }
        return sb.toString()
    }

    companion object {
        private val REQ_PATTERN = Pattern.compile("^(\\w+):(\\d+):(\\w+)(\\?(.*))?$")


        fun fromText(text: String): WebSocketMessage? {
            val matcher = REQ_PATTERN.matcher(text)
            if (!matcher.matches()) {
                return null
            }

            val jsonText = matcher.group(5)
            val jsonObj = if (jsonText != null && jsonText.isNotEmpty()) {
                JSONObject(jsonText)
            } else null

            return WebSocketMessage(
                type = matcher.group(1) ?: return null,
                id = (matcher.group(2) ?: return null).toInt(),
                name = matcher.group(3) ?: return null,
                payload = jsonObj
            )
        }

        fun makeStatus(id: Int, taskId: String, type: Int, reason: String) = WebSocketMessage(
            "action",
            id,
            "status",
            JSONObject().put("taskId", taskId).put("type", type).put("reason", reason)
        )
    }
}

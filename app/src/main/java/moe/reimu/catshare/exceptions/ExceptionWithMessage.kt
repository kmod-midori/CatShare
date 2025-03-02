package moe.reimu.catshare.exceptions

import android.content.Context
import androidx.annotation.StringRes

class ExceptionWithMessage : Exception {
    private val messageContent: String?
    private val messageId: Int?

    constructor(message: String, cause: Throwable, @StringRes userFacingMessage: Int) : super(
        message,
        cause
    ) {
        messageId = userFacingMessage
        messageContent = null
    }

    constructor(message: String, cause: Throwable, userFacingMessage: String) : super(
        message,
        cause
    ) {
        messageId = null
        messageContent = userFacingMessage
    }

    fun getMessage(context: Context): String {
        if (messageId != null) {
            return context.getString(messageId)
        }
        if (messageContent != null) {
            return messageContent
        }
        return ""
    }
}
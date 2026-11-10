package com.example.ricochet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Listens for incoming SMS broadcasts (android.provider.Telephony.SMS_RECEIVED),
 * reassembles multi-part incoming PDUs per originating sender, and hands them
 * to [SmsForwarderEngine].
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group segments by sender address so a multi-part incoming SMS is evaluated as a single message
        val groupedBySender = linkedMapOf<String, StringBuilder>()
        for (sms in messages) {
            val sender = sms.displayOriginatingAddress ?: "Unknown"
            val bodyPart = sms.displayMessageBody ?: ""
            groupedBySender.getOrPut(sender) { StringBuilder() }.append(bodyPart)
        }

        for ((sender, fullBodyBuilder) in groupedBySender) {
            val fullBody = fullBodyBuilder.toString()
            if (fullBody.isNotBlank()) {
                SmsForwarderEngine.processIncomingSms(
                    context = context.applicationContext,
                    sender = sender,
                    messageBody = fullBody
                )
            }
        }
    }
}

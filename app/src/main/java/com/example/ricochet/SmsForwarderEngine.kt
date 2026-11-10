package com.example.ricochet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Evaluates incoming SMS messages against active Ricochet rules and dispatches
 * forwarded SMS messages using the system's default outbound SMS SIM selection.
 */
object SmsForwarderEngine {

    private const val NOTIFICATION_CHANNEL_ID = "ricochet_activity_channel"
    const val FORWARD_PREFIX_TAG = "[Ricochet from:"

    /**
     * Checks if a rule matches an incoming [sender] and [messageBody].
     * - Uses Regex with IGNORE_CASE and DOT_MATCHES_ALL (so multi-line SMS messages work cleanly).
     * - If BOTH senderRegex and messageRegex are specified, BOTH must match (AND logic).
     */
    fun matchesRule(rule: ForwardingRule, sender: String, messageBody: String): Boolean {
        val hasSenderPattern = rule.senderRegex.isNotBlank()
        val hasMessagePattern = rule.messageRegex.isNotBlank()

        if (!hasSenderPattern && !hasMessagePattern) return false

        val regexOptions = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

        val senderMatches = if (hasSenderPattern) {
            runCatching {
                Regex(rule.senderRegex.trim(), regexOptions).containsMatchIn(sender)
            }.getOrDefault(false)
        } else {
            true
        }

        val messageMatches = if (hasMessagePattern) {
            runCatching {
                Regex(rule.messageRegex.trim(), regexOptions).containsMatchIn(messageBody)
            }.getOrDefault(false)
        } else {
            true
        }

        return senderMatches && messageMatches
    }

    /**
     * Validates a user-entered regex pattern in the UI before saving.
     */
    fun validateRegex(pattern: String): String? {
        if (pattern.isBlank()) return null
        return try {
            Regex(pattern.trim())
            null
        } catch (e: Exception) {
            e.localizedMessage ?: "Invalid regular expression"
        }
    }

    /**
     * Processes a newly received SMS message.
     */
    fun processIncomingSms(context: Context, sender: String, messageBody: String) {
        val repository = ForwarderRepository.getInstance(context)

        // 1. Respect Master Pause Switch
        if (!repository.isMasterEnabled()) return

        // 2. Prevent forwarding loops if message already carries our forwarded header
        if (messageBody.trimStart().startsWith(FORWARD_PREFIX_TAG)) return

        val activeRules = repository.getActiveRules()
        if (activeRules.isEmpty()) return

        for (rule in activeRules) {
            // Avoid circular loop if sender is the same phone number as the rule's recipient
            if (isSamePhoneNumber(sender, rule.recipientNumber)) continue

            if (matchesRule(rule, sender, messageBody)) {
                val formattedText = "$FORWARD_PREFIX_TAG $sender]\n$messageBody"
                val sendResult = sendSmsUsingDefaultSim(context, rule.recipientNumber, formattedText)

                val logEntry = ForwardingLogEntry(
                    ruleName = rule.name,
                    originalSender = sender,
                    recipientNumber = rule.recipientNumber,
                    forwardedBody = formattedText,
                    status = sendResult
                )
                repository.addHistoryEntry(logEntry)

                postForwardNotification(
                    context = context,
                    ruleName = rule.name,
                    sender = sender,
                    recipient = rule.recipientNumber,
                    status = sendResult
                )
            }
        }
    }

    /**
     * Sends an SMS (automatically splitting into multipart SMS if longer than 160 chars)
     * using the user's default SIM selection on Android 10+ (including dual-SIM devices).
     */
    private fun sendSmsUsingDefaultSim(
        context: Context,
        recipientNumber: String,
        fullText: String
    ): String {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Failed: SEND_SMS permission not granted"
        }

        return runCatching {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(fullText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    recipientNumber,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    recipientNumber,
                    null,
                    fullText,
                    null,
                    null
                )
            }
            "Sent (${parts.size} part${if (parts.size > 1) "s" else ""})"
        }.fold(
            onSuccess = { it },
            onFailure = { err -> "Failed: ${err.localizedMessage ?: "SMS error"}" }
        )
    }

    private fun isSamePhoneNumber(a: String, b: String): Boolean {
        val digitsA = a.filter { it.isDigit() }
        val digitsB = b.filter { it.isDigit() }
        if (digitsA.length < 7 || digitsB.length < 7) return false
        return digitsA.takeLast(10) == digitsB.takeLast(10)
    }

    private fun postForwardNotification(
        context: Context,
        ruleName: String,
        sender: String,
        recipient: String,
        status: String
    ) {
        createNotificationChannelIfNeeded(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("Ricochet: \"$ruleName\" triggered")
            .setContentText("From $sender -> $recipient ($status)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun createNotificationChannelIfNeeded(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ricochet Forwarded Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows a notification whenever Ricochet forwards a matching SMS"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

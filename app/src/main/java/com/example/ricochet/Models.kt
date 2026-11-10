package com.example.ricochet

import java.util.UUID

/**
 * Represents a single SMS forwarding rule in Ricochet.
 *
 * Matching behavior:
 * - Both [senderRegex] and [messageRegex] use case-insensitive Regex.
 * - If BOTH [senderRegex] and [messageRegex] are non-blank, BOTH must match (AND logic).
 * - If only one is non-blank, only that pattern is evaluated.
 */
data class ForwardingRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val recipientNumber: String,
    val senderRegex: String = "",
    val messageRegex: String = "",
    val isEnabled: Boolean = true
)

/**
 * Represents an entry in the local Ricochet History tab.
 */
data class ForwardingLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val ruleName: String,
    val originalSender: String,
    val recipientNumber: String,
    val forwardedBody: String,
    val status: String
)

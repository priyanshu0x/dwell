package com.droidslife.screensaver.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException

enum class NetworkFailureKind {
    Timeout,
    Dns,
    Connection,
    Io,
}

fun Throwable.transientNetworkFailureKind(): NetworkFailureKind? {
    if (this is CancellationException && this !is TimeoutCancellationException) return null
    directNetworkFailureKind()?.let { return it }
    return cause?.transientNetworkFailureKind()
}

fun Throwable.isTransientNetworkFailure(): Boolean =
    transientNetworkFailureKind() != null

fun Throwable.networkFailureSummary(service: String? = null): String {
    val prefix = service?.trim()?.takeIf { it.isNotBlank() }?.let { "$it " }.orEmpty()
    return when (transientNetworkFailureKind()) {
        NetworkFailureKind.Timeout -> "${prefix}request timed out"
        NetworkFailureKind.Dns -> "${prefix}host could not be resolved"
        NetworkFailureKind.Connection -> "${prefix}connection failed"
        NetworkFailureKind.Io -> "${prefix}network request failed"
        null -> message?.takeIf { it.isNotBlank() } ?: "${prefix}request failed"
    }
}

fun Throwable.networkRetryMessage(service: String): String =
    "${networkFailureSummary(service)} - retrying"

private fun Throwable.directNetworkFailureKind(): NetworkFailureKind? {
    val className = this::class.simpleName.orEmpty()
    val message = message.orEmpty().lowercase()
    return when {
        this is TimeoutCancellationException -> NetworkFailureKind.Timeout
        "Timeout" in className || "timed out" in message || "timeout" in message -> NetworkFailureKind.Timeout
        "UnknownHost" in className || "could not resolve" in message || "name or service not known" in message ->
            NetworkFailureKind.Dns
        "NoRouteToHost" in className ||
            "ConnectException" in className ||
            "connection refused" in message ||
            "failed to connect" in message -> NetworkFailureKind.Connection
        this is IOException -> NetworkFailureKind.Io
        else -> null
    }
}

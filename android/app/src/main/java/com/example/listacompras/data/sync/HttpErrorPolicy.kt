package com.example.listacompras.data.sync

/** How a failed HTTP call should affect the sync run. Shared by the processor and the reconciler. */
internal enum class HttpVerdict {
    /** Keep the data and try again later (backoff). */
    Retry,

    /** The session ended; retrying is pointless until the next login. */
    AuthLost,

    /** The request can never succeed (400/403/404...). */
    Discard,
}

/**
 * [hasToken] tells whether a token still exists after a 401: the authenticator already tried to
 * refresh, so a surviving token means the refresh failed for network reasons.
 */
internal fun classifyHttp(code: Int, hasToken: Boolean): HttpVerdict = when {
    code == 401 -> if (hasToken) HttpVerdict.Retry else HttpVerdict.AuthLost
    code == 408 || code == 429 || code >= 500 -> HttpVerdict.Retry
    else -> HttpVerdict.Discard
}

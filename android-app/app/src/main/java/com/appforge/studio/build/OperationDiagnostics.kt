package com.appforge.studio.build

/** One allowlist for persistent diagnostics. Never serialize free-form errors,
 * source URLs, credentials, paths or shell output, even if they look like a code. */
internal object OperationDiagnostics {
    private enum class Diagnostic { DRIVER_FAILED, NETWORK_WAIT, CONSENT_REQUIRED, USER_STOP, PROGRESS, TRANSFER_RETRY, NETWORK_POLICY, APT_DOWNLOAD_CONSENT, IO_FAILURE, TLS_FAILURE, CANCELLED, REAUTHORIZE_REQUIRED, JOURNAL_RECONCILIATION, MODULE_JOURNAL_RECONCILIATION, TRANSFER_INTERRUPTED }
    fun correlation(value:String):String = if(value.matches(Regex("id_[a-f0-9]{64}")))value
        else "id_"+java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun diagnostic(phase:ModulePhase):String=when(phase) {
        ModulePhase.ERROR->Diagnostic.DRIVER_FAILED
        ModulePhase.WAITING_CONNECTION,ModulePhase.RECONNECTING->Diagnostic.NETWORK_WAIT
        ModulePhase.AWAITING_CONSENT->Diagnostic.CONSENT_REQUIRED
        ModulePhase.CANCELED,ModulePhase.PAUSED->Diagnostic.USER_STOP
        else->Diagnostic.PROGRESS
    }.name
    fun errorCode(event:ModuleProgress):String=when(event.errorCode) {
        "TRANSFER_RETRY"->Diagnostic.TRANSFER_RETRY
        "NETWORK_POLICY"->Diagnostic.NETWORK_POLICY
        "APT_DOWNLOAD_CONSENT"->Diagnostic.APT_DOWNLOAD_CONSENT
        "IOException","SocketTimeoutException","UnknownHostException"->Diagnostic.IO_FAILURE
        "SSLHandshakeException","SSLPeerUnverifiedException"->Diagnostic.TLS_FAILURE
        "CancellationException"->Diagnostic.CANCELLED
        else->null
    }?.name?:diagnostic(event.phase)
    fun safeCode(value:String,phase:ModulePhase):String=Diagnostic.entries.firstOrNull{it.name==value}?.name?:diagnostic(phase)
    fun persistentError(value:String?):String? = value?.let {
        Diagnostic.entries.firstOrNull{code->code.name==it}?.name?:when(it) {
            "IOException","SocketTimeoutException","UnknownHostException"->Diagnostic.IO_FAILURE.name
            "SSLHandshakeException","SSLPeerUnverifiedException"->Diagnostic.TLS_FAILURE.name
            "CancellationException"->Diagnostic.CANCELLED.name
            else->Diagnostic.DRIVER_FAILED.name
        }
    }
}

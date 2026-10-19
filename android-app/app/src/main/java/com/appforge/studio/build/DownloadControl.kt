package com.appforge.studio.build

/** Cooperative stop: never terminate a package manager while its transaction is mutating staging. */
internal class DownloadControl {
    enum class Request { NONE, PAUSE, CANCEL }
    @Volatile var request=Request.NONE
        private set
    fun requestPause(){request=Request.PAUSE}
    fun requestCancel(){request=Request.CANCEL}
    fun checkpoint() {
        if(request!=Request.NONE)throw java.util.concurrent.CancellationException("Güvenli kontrol noktasında durduruldu")
    }
}

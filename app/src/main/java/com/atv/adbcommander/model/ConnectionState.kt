package com.atv.adbcommander.model

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val message: String = "Connecting...") : ConnectionState()
    data class Authenticating(val message: String = "Please approve debugging popup on your TV...") : ConnectionState()
    data class Connected(val host: String, val port: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

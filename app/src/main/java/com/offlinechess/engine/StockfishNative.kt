package com.offlinechess.engine

internal object StockfishNative {
    init {
        System.loadLibrary("stockfish")
    }

    external fun nativeInitialize(executablePath: String): Boolean

    external fun nativeSendCommand(command: String)

    external fun nativeReadOutput(): String?

    external fun nativeShutdown()
}

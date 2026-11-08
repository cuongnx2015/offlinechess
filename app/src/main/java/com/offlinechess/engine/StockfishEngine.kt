package com.offlinechess.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

class StockfishEngine(
    context: Context,
    private val onOutput: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private var outputJob: Job? = null
    private var started = false

    suspend fun start() = withContext(Dispatchers.IO) {
        try {
            val engineDirectory = File(appContext.filesDir, "stockfish")
            if (!engineDirectory.exists() && !engineDirectory.mkdirs()) {
                error("Could not create Stockfish data directory: $engineDirectory")
            }
            val network = File(engineDirectory, NETWORK_NAME)
            if (!network.isFile || network.length() != NETWORK_SIZE) {
                appContext.assets.open(NETWORK_NAME).use { input ->
                    FileOutputStream(network).use { output -> input.copyTo(output) }
                }
                check(network.length() == NETWORK_SIZE) { "Stockfish NNUE network size is invalid" }
            }
            val executablePath = File(engineDirectory, "stockfish").absolutePath
            check(StockfishNative.nativeInitialize(executablePath)) {
                "Stockfish could not initialize its evaluation network"
            }
            started = true
            StockfishNative.nativeSendCommand("uci")
            awaitOutput("uciok")
            StockfishNative.nativeSendCommand("isready")
            awaitOutput("readyok")
            outputJob = scope.launch {
                while (true) {
                    val line = StockfishNative.nativeReadOutput() ?: break
                    onOutput(line)
                }
            }
        } catch (failure: Throwable) {
            if (started) StockfishNative.nativeShutdown()
            started = false
            throw failure
        }
    }

    suspend fun send(command: String) = withContext(Dispatchers.IO) {
        sendCommandsLocked(listOf(command))
    }

    suspend fun send(commands: List<String>) = withContext(Dispatchers.IO) {
        sendCommandsLocked(commands)
    }

    private suspend fun sendCommandsLocked(commands: List<String>) {
        commandMutex.withLock {
            check(started) { "Stockfish is not initialized" }
            commands.forEach(StockfishNative::nativeSendCommand)
        }
    }

    suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            if (started) StockfishNative.nativeShutdown()
            started = false
            outputJob?.cancelAndJoin()
            outputJob = null
            scope.cancel()
        }
    }

    private fun awaitOutput(expected: String) {
        while (true) {
            val line = StockfishNative.nativeReadOutput()
                ?: error("Stockfish stopped before responding with $expected")
            if (line == expected) return
            if (line.startsWith("info string CRITICAL ERROR")) {
                error("Stockfish initialization failed: $line")
            }
        }
    }

    private companion object {
        const val NETWORK_NAME = "nn-1a298aa575a0.nnue"
        const val NETWORK_SIZE = 98_511_183L
    }
}

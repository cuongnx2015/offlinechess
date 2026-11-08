package com.offlinechess.chess

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.offlinechess.data.database.ChessGameEntity
import com.offlinechess.data.database.GameRepository
import com.offlinechess.engine.StockfishEngine
import com.offlinechess.engine.UciOutput
import com.offlinechess.engine.UciParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collect
import java.util.UUID

class ChessGameViewModel : ViewModel() {
    enum class EngineStatus {
        NOT_STARTED,
        STARTING,
        READY,
        THINKING,
        ANALYZING,
        HINTING,
        REVIEWING,
        ERROR,
    }

    enum class SearchPurpose {
        GAME,
        ANALYSIS,
        HINT,
        REVIEW,
    }

    data class AnalysisLine(
        val variation: Int,
        val depth: Int?,
        val score: UciOutput.Score?,
        val moves: List<String>,
    )

    data class MoveReview(
        val ply: Int,
        val san: String,
        val classification: MoveClassification?,
        val centipawnLoss: Int?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: StockfishEngine? = null
    private var repository: GameRepository? = null
    private var persistenceJob: Job? = null
    private var gameGeneration = 0
    private var activeSearchGeneration = -1
    private var activeSearchPurpose = SearchPurpose.GAME
    private var pendingSearchPurpose: SearchPurpose? = null
    private val analysisByVariation = mutableMapOf<Int, AnalysisLine>()
    private var currentGameId = UUID.randomUUID().toString()
    private var gameStartedAtMillis = System.currentTimeMillis()
    private var skillLevel = 10
    private var playerSide = Side.WHITE
    private var whiteTimeMillis = INITIAL_CLOCK_MILLIS
    private var blackTimeMillis = INITIAL_CLOCK_MILLIS
    private var clockUpdatedAtMillis = System.currentTimeMillis()
    private var timedOutSideInternal: Side? = null
    private var lastClockSaveMillis = 0L
    private var reviewJob: Job? = null
    private var pendingReviewPositions: List<ChessPosition>? = null
    private var activeReviewScore: UciOutput.Score? = null
    private var reviewCompletion: CompletableDeferred<UciOutput.Score?>? = null

    var position by mutableStateOf(ChessPosition.initial())
        private set

    var isFlipped by mutableStateOf(false)
        private set

    var engineStatus by mutableStateOf(EngineStatus.NOT_STARTED)
        private set

    var engineError by mutableStateOf<String?>(null)
        private set

    var engineDepth by mutableStateOf<Int?>(null)
        private set

    var analysisLines by mutableStateOf<List<AnalysisLine>>(emptyList())
        private set

    var bestMoveHint by mutableStateOf<String?>(null)
        private set

    var canContinue by mutableStateOf(false)
        private set

    var games by mutableStateOf<List<ChessGameEntity>>(emptyList())
        private set

    var importError by mutableStateOf<String?>(null)
        private set

    var reviewPositions by mutableStateOf<List<ChessPosition>>(emptyList())
        private set

    var reviewMoves by mutableStateOf<List<MoveReview>>(emptyList())
        private set

    var reviewProgress by mutableStateOf(0)
        private set

    var reviewIndex by mutableStateOf(0)
        private set

    var whiteClockMillis by mutableStateOf(INITIAL_CLOCK_MILLIS)
        private set

    var blackClockMillis by mutableStateOf(INITIAL_CLOCK_MILLIS)
        private set

    var isReviewing by mutableStateOf(false)
        private set

    val timedOutSide: Side?
        get() = timedOutSideInternal

    fun attach(context: Context) {
        if (repository != null) return
        val gameRepository = GameRepository(context.applicationContext)
        repository = gameRepository
        scope.launch {
            gameRepository.observeGames().collect { games = it }
        }
        scope.launch {
            val generation = gameGeneration
            val savedGame = withContext(Dispatchers.IO) { gameRepository.latestIncomplete() }
            if (savedGame != null && gameGeneration == generation && !canContinue) {
                try {
                    restore(savedGame)
                    initializeEngine(context.applicationContext)
                } catch (error: Exception) {
                    engineStatus = EngineStatus.ERROR
                    engineError = error.message ?: error.javaClass.simpleName
                }
            }
        }
    }

    fun startNewGame(
        context: Context,
        configuredSkillLevel: Int = skillLevel,
        configuredPlayerSide: Side = playerSide,
    ) {
        gameGeneration++
        position = ChessPosition.initial()
        playerSide = configuredPlayerSide
        isFlipped = playerSide == Side.BLACK
        canContinue = true
        whiteTimeMillis = INITIAL_CLOCK_MILLIS
        blackTimeMillis = INITIAL_CLOCK_MILLIS
        timedOutSideInternal = null
        clockUpdatedAtMillis = System.currentTimeMillis()
        syncClockState()
        engineDepth = null
        analysisLines = emptyList()
        bestMoveHint = null
        pendingSearchPurpose = null
        engineError = null
        importError = null
        currentGameId = UUID.randomUUID().toString()
        gameStartedAtMillis = System.currentTimeMillis()
        skillLevel = configuredSkillLevel.coerceIn(0, 20)
        persistCurrentGame()
        val activeEngine = engine
        if (activeEngine != null && engineStatus != EngineStatus.ERROR) {
            resetEngineForCurrentGame(activeEngine, gameGeneration)
        } else if (activeEngine == null || engineStatus == EngineStatus.ERROR) {
            initializeEngine(context.applicationContext)
        }
    }

    fun play(move: ChessMove) {
        updateClock()
        if (engineStatus != EngineStatus.READY || position.sideToMove != playerSide ||
            position.isGameOver || timedOutSideInternal != null
        ) return
        position = position.play(move)
        canContinue = !position.isGameOver
        bestMoveHint = null
        persistCurrentGame()
        if (position.sideToMove != playerSide) scope.launch { requestEngineMove() }
    }

    fun undo() {
        updateClock()
        if (engineStatus != EngineStatus.READY || timedOutSideInternal != null) return
        var previous = position.undo() ?: return
        if (previous.sideToMove != playerSide) {
            previous = previous.undo() ?: previous
        }
        gameGeneration++
        position = previous
        timedOutSideInternal = null
        clockUpdatedAtMillis = System.currentTimeMillis()
        canContinue = !position.isGameOver
        bestMoveHint = null
        persistCurrentGame()
    }

    fun toggleOrientation() {
        isFlipped = !isFlipped
    }

    fun tickClock() {
        updateClock()
    }

    fun reviewGame(context: Context, game: ChessGameEntity) {
        val moves = game.movesUci.split(" ").filter(String::isNotBlank)
        val positions = buildList {
            var replay = ChessPosition.initial()
            add(replay)
            moves.forEach { uci ->
                val move = replay.legalMoves().firstOrNull { it.toUci() == uci }
                    ?: error("Invalid saved game move: $uci")
                replay = replay.play(move)
                add(replay)
            }
        }
        gameGeneration++
        pendingReviewPositions = positions
        reviewPositions = positions
        reviewMoves = emptyList()
        reviewProgress = 0
        reviewIndex = 0
        isReviewing = true
        val activeEngine = engine
        if (activeEngine == null || engineStatus == EngineStatus.ERROR) {
            initializeEngine(context.applicationContext)
        } else if (engineStatus == EngineStatus.STARTING) {
            return
        } else {
            resetEngineForCurrentGame(activeEngine, gameGeneration, requestGameMove = false)
        }
    }

    fun selectReviewPosition(index: Int) {
        reviewIndex = index.coerceIn(0, (reviewPositions.size - 1).coerceAtLeast(0))
    }

    fun stopReview() {
        if (!isReviewing) return
        gameGeneration++
        isReviewing = false
        pendingReviewPositions = null
        reviewJob?.cancel()
        reviewJob = null
        reviewCompletion?.cancel()
        reviewCompletion = null
        val activeEngine = engine
        val hadEngineError = engineStatus == EngineStatus.ERROR
        engineStatus = when {
            activeEngine == null -> EngineStatus.NOT_STARTED
            hadEngineError -> EngineStatus.ERROR
            else -> EngineStatus.READY
        }
        scope.launch {
            try {
                activeEngine?.send("stop")
                activeEngine?.send("setoption name MultiPV value 1")
                if (!hadEngineError && position.sideToMove != playerSide &&
                    !position.isGameOver && timedOutSideInternal == null
                ) {
                    requestEngineMove()
                }
            } catch (error: Exception) {
                reportEngineError(error)
            }
        }
    }

    fun isBoardInputEnabled(): Boolean =
        engineStatus == EngineStatus.READY && position.sideToMove == playerSide &&
            !position.isGameOver && timedOutSideInternal == null

    fun moveForUci(uci: String): ChessMove? =
        position.legalMoves().firstOrNull { it.toUci() == uci }

    fun restoreGame(context: Context, game: ChessGameEntity) {
        restore(game)
        val activeEngine = engine
        if (activeEngine == null || engineStatus == EngineStatus.ERROR) {
            initializeEngine(context.applicationContext)
        } else {
            resetEngineForCurrentGame(activeEngine, gameGeneration)
        }
    }

    fun deleteGame(id: String) {
        val gameRepository = repository ?: return
        scope.launch {
            withContext(Dispatchers.IO) { gameRepository.delete(id) }
            if (currentGameId == id) canContinue = false
        }
    }

    fun clearHistory() {
        val gameRepository = repository ?: return
        scope.launch {
            withContext(Dispatchers.IO) { gameRepository.deleteAll() }
            games = emptyList()
            canContinue = false
        }
    }

    fun importPgn(context: Context, pgn: String) {
        val gameRepository = repository ?: return
        scope.launch {
            try {
                val imported = withContext(Dispatchers.Default) { PgnParser.parse(pgn) }
                gameGeneration++
                currentGameId = UUID.randomUUID().toString()
                gameStartedAtMillis = System.currentTimeMillis()
                position = imported
                playerSide = imported.sideToMove
                isFlipped = playerSide == Side.BLACK
                canContinue = !imported.isGameOver
                whiteTimeMillis = INITIAL_CLOCK_MILLIS
                blackTimeMillis = INITIAL_CLOCK_MILLIS
                timedOutSideInternal = null
                clockUpdatedAtMillis = System.currentTimeMillis()
                syncClockState()
                engineDepth = null
                analysisLines = emptyList()
                analysisByVariation.clear()
                bestMoveHint = null
                persistCurrentGame()
                val activeEngine = engine
                if (activeEngine == null || engineStatus == EngineStatus.ERROR) {
                    initializeEngine(context.applicationContext)
                } else {
                    resetEngineForCurrentGame(activeEngine, gameGeneration)
                }
                importError = null
            } catch (error: Exception) {
                importError = error.message ?: error.javaClass.simpleName
            }
        }
    }

    fun importPgnFile(context: Context, uri: Uri) {
        scope.launch {
            try {
                val pgn = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("Could not open the selected PGN file")
                }
                importPgn(context.applicationContext, pgn)
            } catch (error: Exception) {
                importError = error.message ?: error.javaClass.simpleName
            }
        }
    }

    fun requestHint() {
        if (engineStatus == EngineStatus.READY && !position.isGameOver) {
            startSearch(SearchPurpose.HINT)
        }
    }

    fun analyze(context: Context) {
        if (engineStatus == EngineStatus.READY) {
            startSearch(SearchPurpose.ANALYSIS)
        } else if (engineStatus == EngineStatus.NOT_STARTED || engineStatus == EngineStatus.STARTING) {
            pendingSearchPurpose = SearchPurpose.ANALYSIS
            if (engineStatus == EngineStatus.NOT_STARTED) initializeEngine(context.applicationContext)
        }
    }

    private fun initializeEngine(context: Context) {
        engineStatus = EngineStatus.STARTING
        val newEngine = StockfishEngine(context) { line ->
            scope.launch { handleEngineOutput(line) }
        }
        engine = newEngine
        scope.launch {
            try {
                newEngine.start()
                newEngine.send("setoption name Skill Level value $skillLevel")
                engineStatus = EngineStatus.READY
                val positionsToReview = pendingReviewPositions
                if (positionsToReview != null) {
                    pendingReviewPositions = null
                    runReview(positionsToReview)
                } else {
                    val pending = pendingSearchPurpose
                    if (pending != null) {
                        pendingSearchPurpose = null
                        startSearch(pending)
                    } else if (position.sideToMove != playerSide && !position.isGameOver) {
                        requestEngineMove()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                engineStatus = EngineStatus.ERROR
                engineError = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private suspend fun requestEngineMove() {
        startSearch(SearchPurpose.GAME)
    }

    private fun runReview(positions: List<ChessPosition>) {
        val activeEngine = engine ?: return
        reviewJob?.cancel()
        reviewJob = scope.launch {
            try {
                engineStatus = EngineStatus.REVIEWING
                val scores = mutableListOf<UciOutput.Score?>()
                positions.forEachIndexed { index, reviewPosition ->
                    activeSearchGeneration = gameGeneration
                    activeSearchPurpose = SearchPurpose.REVIEW
                    activeReviewScore = null
                    val completed = CompletableDeferred<UciOutput.Score?>()
                    reviewCompletion = completed
                    activeEngine.send(
                        listOf(
                            "setoption name MultiPV value 1",
                            "position fen ${reviewPosition.fen}",
                            "go depth 10",
                        ),
                    )
                    scores += withTimeout(REVIEW_SEARCH_TIMEOUT_MILLIS) { completed.await() }
                    reviewProgress = index + 1
                }
                reviewMoves = positions.dropLast(1).mapIndexed { index, before ->
                    val loss = scores.getOrNull(index)?.let { beforeScore ->
                        scores.getOrNull(index + 1)?.let { afterScore ->
                            (scoreToCentipawns(beforeScore) -
                                scoreToCentipawns(afterScore).unaryMinus()).coerceAtLeast(0)
                        }
                    }
                    MoveReview(
                        ply = index,
                        san = positions[index + 1].sanMoves.last(),
                        classification = loss?.let(::classifyCentipawnLoss),
                        centipawnLoss = loss,
                    )
                }
                engineStatus = EngineStatus.READY
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportEngineError(error)
            } finally {
                reviewCompletion = null
            }
        }
    }

    private fun scoreToCentipawns(score: UciOutput.Score): Int =
        if (score.isMate) {
            val distance = score.value.coerceIn(-1000, 1000)
            if (distance > 0) 100_000 - distance * 100 else -100_000 - distance * 100
        } else {
            score.value.coerceIn(-100_000, 100_000)
        }

    private fun resetEngineForCurrentGame(
        activeEngine: StockfishEngine,
        generation: Int,
        requestGameMove: Boolean = true,
    ) {
        engineStatus = EngineStatus.STARTING
        engineDepth = null
        analysisLines = emptyList()
        analysisByVariation.clear()
        bestMoveHint = null
        pendingSearchPurpose = null
        scope.launch {
            try {
                activeEngine.send(
                    listOf(
                        "stop",
                        "ucinewgame",
                        "setoption name Skill Level value $skillLevel",
                    ),
                )
                if (generation == gameGeneration) {
                    engineStatus = EngineStatus.READY
                    val positionsToReview = pendingReviewPositions
                    if (positionsToReview != null) {
                        pendingReviewPositions = null
                        runReview(positionsToReview)
                    } else if (requestGameMove && position.sideToMove != playerSide && !position.isGameOver) {
                        requestEngineMove()
                    }
                }
            } catch (error: Exception) {
                reportEngineError(error)
            }
        }
    }

    private fun startSearch(purpose: SearchPurpose) {
        val activeEngine = engine ?: return
        val generation = gameGeneration
        val searchPosition = position
        activeSearchGeneration = generation
        activeSearchPurpose = purpose
        engineDepth = null
        analysisByVariation.clear()
        analysisLines = emptyList()
        bestMoveHint = null
        engineStatus = when (purpose) {
            SearchPurpose.GAME -> EngineStatus.THINKING
            SearchPurpose.ANALYSIS -> EngineStatus.ANALYZING
            SearchPurpose.HINT -> EngineStatus.HINTING
            SearchPurpose.REVIEW -> EngineStatus.REVIEWING
        }
        scope.launch {
            try {
                val goCommand = when (purpose) {
                    SearchPurpose.GAME -> "go movetime 650"
                    SearchPurpose.ANALYSIS -> "go depth 14"
                    SearchPurpose.HINT -> "go depth 12"
                    SearchPurpose.REVIEW -> "go depth 10"
                }
                activeEngine.send(
                    listOf(
                        "setoption name MultiPV value ${if (purpose == SearchPurpose.ANALYSIS) 3 else 1}",
                        "position fen ${searchPosition.fen}",
                        goCommand,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportEngineError(error)
            }
        }
    }

    private fun handleEngineOutput(line: String) {
        when (val output = UciParser.parse(line)) {
            is UciOutput.Info -> {
                if (activeSearchGeneration != gameGeneration) return
                if (activeSearchPurpose == SearchPurpose.REVIEW) {
                    activeReviewScore = output.score
                    return
                }
                engineDepth = output.depth
                val variation = output.multiPv ?: 1
                analysisByVariation[variation] = AnalysisLine(
                    variation = variation,
                    depth = output.depth,
                    score = output.score,
                    moves = output.principalVariation,
                )
                analysisLines = analysisByVariation.values.sortedBy { it.variation }
            }
            is UciOutput.BestMove -> {
                if (activeSearchGeneration != gameGeneration) return
                if (activeSearchPurpose == SearchPurpose.REVIEW) {
                    reviewCompletion?.complete(activeReviewScore)
                    return
                }
                if (engineStatus !in setOf(
                        EngineStatus.THINKING,
                        EngineStatus.ANALYZING,
                        EngineStatus.HINTING,
                    )
                ) return
                val purpose = activeSearchPurpose
                engineStatus = EngineStatus.READY
                val uci = output.move ?: return
                if (purpose == SearchPurpose.GAME) {
                    updateClock()
                    if (timedOutSideInternal != null) return
                    val move = moveForUci(uci)
                    if (move == null) {
                        engineStatus = EngineStatus.ERROR
                        engineError = "Stockfish returned an illegal move: $uci for ${position.fen}"
                    } else {
                        position = position.play(move)
                        canContinue = !position.isGameOver
                        persistCurrentGame()
                    }
                } else {
                    bestMoveHint = uci
                    scope.launch {
                        engine?.send("setoption name MultiPV value 1")
                    }
                }
            }

            is UciOutput.Message -> {
                if (output.text.startsWith("CRITICAL ERROR")) {
                    val error = IllegalStateException(output.text)
                    reviewCompletion?.completeExceptionally(error)
                    reportEngineError(error)
                }
            }
            null -> Unit
        }
    }

    private fun reportEngineError(error: Exception) {
        engineStatus = EngineStatus.ERROR
        engineError = error.message ?: error.javaClass.simpleName
    }

    private fun restore(game: ChessGameEntity) {
        gameGeneration++
        currentGameId = game.id
        gameStartedAtMillis = game.startedAtMillis
        skillLevel = game.skillLevel.coerceIn(0, 20)
        playerSide = if (game.playerColor == "black") Side.BLACK else Side.WHITE
        isFlipped = playerSide == Side.BLACK
        position = ChessPosition.replay(game.movesUci.split(" ").filter(String::isNotBlank))
        canContinue = !position.isGameOver
        whiteTimeMillis = game.whiteTimeMillis.coerceAtLeast(0)
        blackTimeMillis = game.blackTimeMillis.coerceAtLeast(0)
        clockUpdatedAtMillis = game.clockUpdatedAtMillis.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        timedOutSideInternal = game.timedOutSide?.let { if (it == "black") Side.BLACK else Side.WHITE }
        syncClockState()
        updateClock()
        engineDepth = null
        analysisLines = emptyList()
        analysisByVariation.clear()
        bestMoveHint = null
    }

    private fun persistCurrentGame() {
        val gameRepository = repository ?: return
        val now = System.currentTimeMillis()
        val saved = ChessGameEntity(
            id = currentGameId,
            startedAtMillis = gameStartedAtMillis,
            updatedAtMillis = now,
            durationMillis = (now - gameStartedAtMillis).coerceAtLeast(0),
            playerColor = if (playerSide == Side.WHITE) "white" else "black",
            skillLevel = skillLevel,
            result = gameResultToken(),
            fen = position.fen,
            movesUci = position.uciMoves.joinToString(" "),
            pgn = position.toPgn(gameResultToken()),
            isComplete = position.isGameOver || timedOutSideInternal != null,
            whiteTimeMillis = whiteTimeMillis,
            blackTimeMillis = blackTimeMillis,
            clockUpdatedAtMillis = clockUpdatedAtMillis,
            timedOutSide = timedOutSideInternal?.let { if (it == Side.WHITE) "white" else "black" },
        )
        val previousSave = persistenceJob
        persistenceJob = scope.launch {
            previousSave?.join()
            withContext(Dispatchers.IO) { gameRepository.save(saved) }
        }
    }

    override fun onCleared() {
        val activeEngine = engine
        scope.launch {
            withContext(Dispatchers.IO) {
                activeEngine?.shutdown()
            }
            scope.cancel()
        }
    }

    private fun updateClock(nowMillis: Long = System.currentTimeMillis()) {
        if (timedOutSideInternal != null || position.isGameOver) return
        val elapsed = (nowMillis - clockUpdatedAtMillis).coerceAtLeast(0L)
        if (elapsed == 0L) return
        when (position.sideToMove) {
            Side.WHITE -> whiteTimeMillis = (whiteTimeMillis - elapsed).coerceAtLeast(0L)
            Side.BLACK -> blackTimeMillis = (blackTimeMillis - elapsed).coerceAtLeast(0L)
        }
        clockUpdatedAtMillis = nowMillis
        syncClockState()
        if (whiteTimeMillis == 0L || blackTimeMillis == 0L) {
            timedOutSideInternal = if (whiteTimeMillis == 0L) Side.WHITE else Side.BLACK
            canContinue = false
            persistCurrentGame()
        } else if (nowMillis - lastClockSaveMillis >= CLOCK_SAVE_INTERVAL_MILLIS) {
            lastClockSaveMillis = nowMillis
            persistCurrentGame()
        }
    }

    private fun syncClockState() {
        whiteClockMillis = whiteTimeMillis
        blackClockMillis = blackTimeMillis
    }

    private fun gameResultToken(): String = when (timedOutSideInternal) {
        Side.WHITE -> "0-1"
        Side.BLACK -> "1-0"
        null -> position.resultToken()
    }

    companion object {
        const val INITIAL_CLOCK_MILLIS = 10 * 60 * 1000L
        const val REVIEW_SEARCH_TIMEOUT_MILLIS = 30_000L
        const val CLOCK_SAVE_INTERVAL_MILLIS = 5_000L
    }
}

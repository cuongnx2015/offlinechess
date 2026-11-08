package com.offlinechess.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlinechess.R
import com.offlinechess.chess.ChessGameViewModel
import com.offlinechess.chess.ChessMove
import com.offlinechess.chess.ChessPosition
import com.offlinechess.chess.ChessPiece
import com.offlinechess.chess.Side
import com.offlinechess.data.preferences.AppPreferences
import com.offlinechess.data.preferences.PreferencesRepository
import com.offlinechess.data.preferences.ThemeMode
import com.offlinechess.ui.theme.OfflineChessTheme
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private enum class AppScreen {
    HOME,
    GAME,
    SETTINGS,
    HISTORY,
    ANALYSIS,
    TRAINING,
    REVIEW,
}

@Composable
fun OfflineChessApp(viewModel: ChessGameViewModel) {
    var screen by remember { mutableStateOf(AppScreen.HOME) }
    var gameReturnScreen by remember { mutableStateOf(AppScreen.HOME) }
    var analysisReturnScreen by remember { mutableStateOf(AppScreen.HOME) }
    val context = LocalContext.current
    val settings = remember(context) { PreferencesRepository(context) }
    val loadedPreferences by settings.preferences.collectAsState(initial = null)
    val preferences = loadedPreferences ?: AppPreferences()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel, context) {
        viewModel.attach(context)
    }
    LaunchedEffect(loadedPreferences?.languageTag) {
        loadedPreferences?.languageTag?.let { languageTag ->
            val locales = if (languageTag.isBlank()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    BackHandler(enabled = screen != AppScreen.HOME) {
        when (screen) {
            AppScreen.HOME -> Unit
            AppScreen.GAME -> screen = gameReturnScreen
            AppScreen.SETTINGS, AppScreen.HISTORY, AppScreen.TRAINING -> screen = AppScreen.HOME
            AppScreen.ANALYSIS -> screen = analysisReturnScreen
            AppScreen.REVIEW -> {
                viewModel.stopReview()
                screen = AppScreen.HISTORY
            }
        }
    }

    val darkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    OfflineChessTheme(darkTheme = darkTheme) {
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                onNewGame = {
                    gameReturnScreen = AppScreen.HOME
                    viewModel.startNewGame(
                        context,
                        preferences.skillLevel,
                        if (preferences.playerColor == "black") Side.BLACK else Side.WHITE,
                    )
                    screen = AppScreen.GAME
                },
                onContinue = {
                    gameReturnScreen = AppScreen.HOME
                    screen = AppScreen.GAME
                },
                onHistory = { screen = AppScreen.HISTORY },
                onSettings = { screen = AppScreen.SETTINGS },
                onAnalysis = {
                    analysisReturnScreen = AppScreen.HOME
                    viewModel.analyze(context)
                    screen = AppScreen.ANALYSIS
                },
                onTraining = { screen = AppScreen.TRAINING },
                canContinue = viewModel.canContinue,
            )

            AppScreen.GAME -> GameScreen(
                viewModel = viewModel,
                onBack = { screen = gameReturnScreen },
                onNewGame = {
                    viewModel.startNewGame(
                        context,
                        preferences.skillLevel,
                        if (preferences.playerColor == "black") Side.BLACK else Side.WHITE,
                    )
                },
                onAnalysis = {
                    analysisReturnScreen = AppScreen.GAME
                    viewModel.analyze(context)
                    screen = AppScreen.ANALYSIS
                },
            )

            AppScreen.SETTINGS -> SettingsScreen(
                preferences = preferences,
                onBack = { screen = AppScreen.HOME },
                onThemeChange = { mode ->
                    coroutineScope.launch { settings.setThemeMode(mode) }
                },
                onLanguageChange = { tag ->
                    coroutineScope.launch {
                        if (tag.isBlank()) settings.useSystemLanguage() else settings.setLanguageTag(tag)
                    }
                },
                onSkillChange = { level ->
                    coroutineScope.launch { settings.setSkillLevel(level) }
                },
                onPlayerColorChange = { color ->
                    coroutineScope.launch { settings.setPlayerColor(color) }
                },
            )

            AppScreen.HISTORY -> HistoryScreen(
                games = viewModel.games,
                importError = viewModel.importError,
                onBack = { screen = AppScreen.HOME },
                onOpen = { game ->
                    viewModel.restoreGame(context, game)
                    gameReturnScreen = AppScreen.HISTORY
                    screen = AppScreen.GAME
                },
                onReview = { game ->
                    viewModel.reviewGame(context, game)
                    screen = AppScreen.REVIEW
                },
                onImport = { uri -> viewModel.importPgnFile(context, uri) },
                onDelete = viewModel::deleteGame,
                onClear = viewModel::clearHistory,
            )

            AppScreen.ANALYSIS -> AnalysisScreen(
                viewModel = viewModel,
                context = context,
                onBack = { screen = analysisReturnScreen },
            )

            AppScreen.TRAINING -> TrainingScreen(onBack = { screen = AppScreen.HOME })

            AppScreen.REVIEW -> GameReviewScreen(
                viewModel = viewModel,
                onBack = {
                    viewModel.stopReview()
                    screen = AppScreen.HISTORY
                },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onAnalysis: () -> Unit,
    onTraining: () -> Unit,
    canContinue: Boolean,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "♞",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 54.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.home_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.home_description),
                modifier = Modifier.width(320.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onNewGame,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(stringResource(R.string.new_game))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onContinue,
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(stringResource(R.string.continue_game))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onAnalysis, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.analysis))
                }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.history))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onTraining, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.training))
                }
                TextButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.offline_private),
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Developed by NXC",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun GameScreen(
    viewModel: ChessGameViewModel,
    onBack: () -> Unit,
    onNewGame: () -> Unit,
    onAnalysis: () -> Unit,
) {
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.tickClock()
            delay(1_000)
        }
    }
    val position = viewModel.position
    var promotionMoves by remember { mutableStateOf<List<ChessMove>>(emptyList()) }
    val whitePlayer = stringResource(R.string.white)
    val blackPlayer = stringResource(R.string.black)
    val turn = if (position.sideToMove == Side.WHITE) whitePlayer else blackPlayer

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    text = stringResource(R.string.play_computer),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = viewModel::toggleOrientation) {
                    Text(stringResource(R.string.flip_board))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(turn, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when {
                            viewModel.engineStatus == ChessGameViewModel.EngineStatus.STARTING ->
                                stringResource(R.string.engine_starting)
                            viewModel.engineStatus == ChessGameViewModel.EngineStatus.THINKING ->
                                stringResource(R.string.engine_thinking)
                            viewModel.engineStatus == ChessGameViewModel.EngineStatus.ANALYZING ->
                                stringResource(R.string.analysis_in_progress)
                            viewModel.engineStatus == ChessGameViewModel.EngineStatus.HINTING ->
                                stringResource(R.string.hint_in_progress)
                            viewModel.timedOutSide != null ->
                                stringResource(R.string.time_out)
                            viewModel.engineStatus == ChessGameViewModel.EngineStatus.ERROR ->
                                viewModel.engineError ?: stringResource(R.string.engine_error)
                            position.isCheckmate -> stringResource(R.string.checkmate)
                            position.isStalemate -> stringResource(R.string.stalemate)
                            position.isDraw -> stringResource(R.string.draw)
                            position.isCheck -> stringResource(R.string.check)
                            else -> stringResource(R.string.your_turn)
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClockCard(
                    label = whitePlayer,
                    millis = viewModel.whiteClockMillis,
                    active = position.sideToMove == Side.WHITE && viewModel.timedOutSide == null,
                    modifier = Modifier.weight(1f),
                )
                ClockCard(
                    label = blackPlayer,
                    millis = viewModel.blackClockMillis,
                    active = position.sideToMove == Side.BLACK && viewModel.timedOutSide == null,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Board(
                position = position,
                flipped = viewModel.isFlipped,
                inputEnabled = viewModel.isBoardInputEnabled(),
                hintMove = viewModel.bestMoveHint,
            ) { from, to ->
                val moves = position.legalMoves(from).filter { it.to == to }
                if (moves.any { it.promotion != null }) {
                    promotionMoves = moves
                } else {
                    moves.firstOrNull()?.let(viewModel::play)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::undo,
                    enabled = position.uciMoves.isNotEmpty() &&
                        viewModel.engineStatus == ChessGameViewModel.EngineStatus.READY,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.undo))
                }
                OutlinedButton(
                    onClick = onNewGame,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.new_game))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::requestHint,
                    enabled = viewModel.engineStatus == ChessGameViewModel.EngineStatus.READY,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.hint))
                }
                OutlinedButton(
                    onClick = onAnalysis,
                    enabled = viewModel.engineStatus == ChessGameViewModel.EngineStatus.READY,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.analysis))
                }
            }
            viewModel.bestMoveHint?.let { hint ->
                Text(
                    text = stringResource(R.string.best_move_format, prettyUci(hint)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.moves),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            MoveList(position)
        }
    }

    if (promotionMoves.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { promotionMoves = emptyList() },
            title = { Text(stringResource(R.string.promote_pawn)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    promotionMoves.forEach { move ->
                        TextButton(onClick = {
                            viewModel.play(move)
                            promotionMoves = emptyList()
                        }) {
                            Text(pieceGlyph(ChessPiece(move.promotion!!, position.sideToMove)), fontSize = 28.sp)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun ClockCard(label: String, millis: Long, active: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatClock(millis),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun GameReviewScreen(viewModel: ChessGameViewModel, onBack: () -> Unit) {
    val position = viewModel.reviewPositions.getOrNull(viewModel.reviewIndex) ?: ChessPosition.initial()
    val currentMove = viewModel.reviewMoves.getOrNull(viewModel.reviewIndex)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.game_review), fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(64.dp))
            }
            if (viewModel.engineStatus == ChessGameViewModel.EngineStatus.REVIEWING) {
                Text(
                    stringResource(
                        R.string.review_progress,
                        viewModel.reviewProgress,
                        viewModel.reviewPositions.size,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (viewModel.engineStatus == ChessGameViewModel.EngineStatus.ERROR) {
                Text(
                    viewModel.engineError ?: stringResource(R.string.engine_error),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Board(position = position, flipped = false, inputEnabled = false) { _, _ -> }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.selectReviewPosition(viewModel.reviewIndex - 1) },
                    enabled = viewModel.reviewIndex > 0,
                ) { Text(stringResource(R.string.previous_move)) }
                Text(
                    text = currentMove?.let {
                        "${it.san} · ${it.classification?.let { grade -> classificationText(grade) } ?: "—"}"
                    }
                        ?: stringResource(R.string.start_position),
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    onClick = { viewModel.selectReviewPosition(viewModel.reviewIndex + 1) },
                    enabled = viewModel.reviewIndex < viewModel.reviewPositions.lastIndex,
                ) { Text(stringResource(R.string.next_move)) }
            }
            currentMove?.centipawnLoss?.let {
                Text(
                    stringResource(R.string.centipawn_loss, it),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(viewModel.reviewMoves) { index, move ->
                    TextButton(
                        onClick = { viewModel.selectReviewPosition(index) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${index / 2 + 1}${if (index % 2 == 0) "." else "..."} ${move.san}  " +
                                "${move.classification?.let { grade -> classificationText(grade) } ?: "—"}" +
                                (move.centipawnLoss?.let { "  -$it cp" } ?: ""),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            if (viewModel.reviewMoves.isEmpty() &&
                viewModel.engineStatus != ChessGameViewModel.EngineStatus.REVIEWING
            ) {
                Text(stringResource(R.string.review_no_moves))
            }
        }
    }
}

@Composable
private fun classificationText(classification: com.offlinechess.chess.MoveClassification): String =
    stringResource(
        when (classification) {
            com.offlinechess.chess.MoveClassification.BEST -> R.string.grade_best
            com.offlinechess.chess.MoveClassification.GOOD -> R.string.grade_good
            com.offlinechess.chess.MoveClassification.INACCURACY -> R.string.grade_inaccuracy
            com.offlinechess.chess.MoveClassification.MISTAKE -> R.string.grade_mistake
            com.offlinechess.chess.MoveClassification.BLUNDER -> R.string.grade_blunder
        },
    )

private fun formatClock(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L) + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun Board(
    position: ChessPosition,
    flipped: Boolean,
    inputEnabled: Boolean = true,
    hintMove: String? = null,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var selected by remember(position) { mutableStateOf<Int?>(null) }
    val legalMoves = selected?.let { position.legalMoves(it) }.orEmpty()
    val legalTargets = legalMoves.map { it.to }.toSet()
    val lastMove = position.uciMoves.lastOrNull()?.let {
        ChessPosition.parseSquare(it.take(2)) to ChessPosition.parseSquare(it.substring(2, 4))
    }
    val lightSquare = Color(0xFFE9E4D7)
    val darkSquare = Color(0xFF779078)
    val selectedSquare = Color(0xFFBECD6A)
    val previousSquare = Color(0xFFD8C766)

    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (displayRank in 0..7) {
                val rank = if (flipped) displayRank else 7 - displayRank
                Row(modifier = Modifier.weight(1f)) {
                    for (displayFile in 0..7) {
                        val file = if (flipped) 7 - displayFile else displayFile
                        val square = rank * 8 + file
                        val piece = position.pieceAt(square)
                        val isSelected = selected == square
                        val wasLastMove = lastMove?.let { square == it.first || square == it.second } == true
                        val base = if ((rank + file) % 2 == 0) darkSquare else lightSquare
                        val background = when {
                            isSelected -> selectedSquare
                            wasLastMove -> previousSquare
                            else -> base
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(background)
                                .clickable(enabled = inputEnabled) {
                                    when {
                                        selected != null && square in legalTargets -> {
                                            onMove(selected!!, square)
                                            selected = null
                                        }

                                        piece?.side == position.sideToMove -> selected = square
                                        else -> selected = null
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (square in legalTargets) {
                                Box(
                                    modifier = Modifier
                                        .size(if (piece == null) 12.dp else 30.dp)
                                        .background(
                                            Color(0x663B4A3F),
                                            if (piece == null) CircleShape else RoundedCornerShape(50),
                                        ),
                                )
                            }
                            if (piece != null) {
                                Text(
                                    text = pieceGlyph(piece),
                                    fontSize = 34.sp,
                                    color = if (piece.side == Side.WHITE) Color.White else Color(0xFF262924),
                                )
                            }
                            if (file == if (flipped) 7 else 0) {
                                Text(
                                    text = (rank + 1).toString(),
                                    modifier = Modifier.align(Alignment.TopStart).padding(3.dp),
                                    color = if ((rank + file) % 2 == 0) lightSquare else darkSquare,
                                    fontSize = 9.sp,
                                )
                            }
                            if ((flipped && rank == 7 || !flipped && rank == 0) &&
                                file == if (flipped) 7 else 0
                            ) {
                                Text(
                                    text = ('a'.code + file).toChar().toString(),
                                    modifier = Modifier
                                        .align(if (flipped) Alignment.BottomEnd else Alignment.BottomStart)
                                        .padding(3.dp),
                                    color = if ((rank + file) % 2 == 0) lightSquare else darkSquare,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
        hintMove?.takeIf { it.length >= 4 }?.let { uci ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val squareSize = size.width / 8f
                fun center(square: Int): Offset {
                    val file = square % 8
                    val rank = square / 8
                    val displayFile = if (flipped) 7 - file else file
                    val displayRank = if (flipped) rank else 7 - rank
                    return Offset(
                        (displayFile + 0.5f) * squareSize,
                        (displayRank + 0.5f) * squareSize,
                    )
                }

                val from = center(ChessPosition.parseSquare(uci.take(2)))
                val to = center(ChessPosition.parseSquare(uci.substring(2, 4)))
                val dx = to.x - from.x
                val dy = to.y - from.y
                val length = sqrt(dx * dx + dy * dy)
                if (length > 0f) {
                    val ux = dx / length
                    val uy = dy / length
                    val start = Offset(from.x + ux * squareSize * 0.22f, from.y + uy * squareSize * 0.22f)
                    val end = Offset(to.x - ux * squareSize * 0.24f, to.y - uy * squareSize * 0.24f)
                    val arrowColor = Color(0xCC315C46)
                    drawLine(
                        color = arrowColor,
                        start = start,
                        end = end,
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    val headLength = squareSize * 0.23f
                    val headAngle = 0.55f
                    val arrowHead = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(
                            end.x - headLength * cos(angle - headAngle),
                            end.y - headLength * sin(angle - headAngle),
                        )
                        lineTo(
                            end.x - headLength * cos(angle + headAngle),
                            end.y - headLength * sin(angle + headAngle),
                        )
                        close()
                    }
                    drawPath(arrowHead, arrowColor)
                }
            }
        }
    }
}

@Composable
private fun MoveList(position: ChessPosition) {
    if (position.sanMoves.isEmpty()) {
        Text(
            text = stringResource(R.string.no_moves),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(position.sanMoves.chunked(2)) { index, pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${index + 1}.",
                    modifier = Modifier.width(34.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(pair[0], modifier = Modifier.weight(1f))
                Text(pair.getOrNull(1).orEmpty(), modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun pieceGlyph(piece: ChessPiece): String = when (piece.side) {
    Side.WHITE -> when (piece.type) {
        'K' -> "♔"
        'Q' -> "♕"
        'R' -> "♖"
        'B' -> "♗"
        'N' -> "♘"
        else -> "♙"
    }

    Side.BLACK -> when (piece.type) {
        'K' -> "♚"
        'Q' -> "♛"
        'R' -> "♜"
        'B' -> "♝"
        'N' -> "♞"
        else -> "♟"
    }
}

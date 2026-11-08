package com.offlinechess.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinechess.R
import com.offlinechess.chess.ChessGameViewModel
import com.offlinechess.engine.UciOutput

@Composable
fun AnalysisScreen(
    viewModel: ChessGameViewModel,
    context: Context,
    onBack: () -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.analysis_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            when (viewModel.engineStatus) {
                ChessGameViewModel.EngineStatus.STARTING ->
                    Text(stringResource(R.string.engine_starting))
                ChessGameViewModel.EngineStatus.ANALYZING ->
                    Text(stringResource(R.string.analysis_in_progress))
                ChessGameViewModel.EngineStatus.ERROR ->
                    Text(
                        viewModel.engineError ?: stringResource(R.string.engine_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                else -> Unit
            }
            viewModel.bestMoveHint?.let { bestMove ->
                Text(
                    text = stringResource(R.string.best_move_format, prettyUci(bestMove)),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (viewModel.analysisLines.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.analysisLines, key = { it.variation }) { line ->
                        AnalysisVariationCard(line)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.analysis_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.analyze(context) },
                enabled = viewModel.engineStatus == ChessGameViewModel.EngineStatus.READY,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.analyze_position))
            }
        }
    }
}

@Composable
private fun AnalysisVariationCard(line: ChessGameViewModel.AnalysisLine) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.variation_format, line.variation),
                    fontWeight = FontWeight.SemiBold,
                )
                line.score?.let { score ->
                    Text(
                        text = stringResource(R.string.evaluation_format, formatScore(score)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                line.depth?.let { depth ->
                    Text(
                        text = stringResource(R.string.depth_format, depth),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = line.moves.joinToString(" ") { prettyUci(it) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun formatScore(score: UciOutput.Score): String =
    if (score.isMate) {
        "M${if (score.value > 0) "+" else ""}${score.value}"
    } else {
        val pawns = score.value / 100.0
        "%+.2f".format(java.util.Locale.ROOT, pawns)
    }

internal fun prettyUci(uci: String): String =
    if (uci.length >= 4) {
        "${uci.substring(0, 2)}–${uci.substring(2, 4)}" +
            uci.getOrNull(4)?.let { "=${it.uppercaseChar()}" }.orEmpty()
    } else {
        uci
    }

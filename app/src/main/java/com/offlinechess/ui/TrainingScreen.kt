package com.offlinechess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinechess.R
import com.offlinechess.chess.ChessPosition
import com.offlinechess.chess.trainingPuzzles

@Composable
fun TrainingScreen(onBack: () -> Unit) {
    var puzzleIndex by remember { mutableIntStateOf(0) }
    var position by remember(puzzleIndex) {
        mutableStateOf(ChessPosition.fromFen(trainingPuzzles[puzzleIndex].fen))
    }
    var solved by remember(puzzleIndex) { mutableStateOf(false) }
    var hintShown by remember(puzzleIndex) { mutableStateOf(false) }
    var wrongAttempt by remember(puzzleIndex) { mutableStateOf(false) }
    var solvedPuzzles by remember { mutableStateOf(setOf<Int>()) }
    val puzzle = trainingPuzzles[puzzleIndex]
    val solutionMove = puzzle.solution
    val solutionNotation = remember(puzzleIndex) {
        val initialPosition = ChessPosition.fromFen(puzzle.fen)
        initialPosition.play(initialPosition.legalMoves().single { it.toUci() == solutionMove })
            .sanMoves.last()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(
                    text = stringResource(R.string.training),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.training_puzzle_progress, puzzleIndex + 1, trainingPuzzles.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.training_mate_in_one),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Board(
                position = position,
                flipped = false,
                inputEnabled = !solved,
                hintMove = if (hintShown) solutionMove else null,
            ) { from, to ->
                val move = position.legalMoves(from).firstOrNull { it.to == to }
                if (move != null) {
                    wrongAttempt = false
                    val nextPosition = position.play(move)
                    if (move.toUci() == solutionMove && nextPosition.isCheckmate) {
                        position = nextPosition
                        solved = true
                        solvedPuzzles = solvedPuzzles + puzzleIndex
                    } else {
                        wrongAttempt = true
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    solved -> stringResource(R.string.training_correct)
                    hintShown -> stringResource(R.string.training_hint_format, solutionNotation)
                    wrongAttempt -> stringResource(R.string.training_try_again)
                    else -> stringResource(R.string.training_prompt)
                },
                modifier = Modifier.fillMaxWidth(),
                color = if (solved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (solved) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.training_score_format, solvedPuzzles.size, trainingPuzzles.size),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { hintShown = true },
                    enabled = !solved && !hintShown,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.training_hint))
                }
                OutlinedButton(
                    onClick = { puzzleIndex = (puzzleIndex + 1) % trainingPuzzles.size },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.training_next))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

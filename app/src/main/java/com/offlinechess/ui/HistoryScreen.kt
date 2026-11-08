package com.offlinechess.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinechess.R
import com.offlinechess.data.database.ChessGameEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    games: List<ChessGameEntity>,
    importError: String?,
    onBack: () -> Unit,
    onOpen: (ChessGameEntity) -> Unit,
    onReview: (ChessGameEntity) -> Unit,
    onImport: (Uri) -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(onImport) },
    )
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(stringResource(R.string.history), fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { confirmClear = true }, enabled = games.isNotEmpty()) {
                    Text(stringResource(R.string.clear_history))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.import_pgn))
                }
            }
            importError?.let {
                Text(
                    text = stringResource(R.string.import_failed, it),
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (games.isEmpty()) {
                Text(
                    text = stringResource(R.string.saved_games_empty),
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(games, key = { it.id }) { game ->
                        GameHistoryCard(
                            game = game,
                            context = context,
                            onOpen = { onOpen(game) },
                            onReview = { onReview(game) },
                            onDelete = { onDelete(game.id) },
                        )
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_history)) },
            text = { Text(stringResource(R.string.clear_history_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.clear_history))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun GameHistoryCard(
    game: ChessGameEntity,
    context: Context,
    onOpen: () -> Unit,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = DateFormat.getDateTimeInstance().format(Date(game.updatedAtMillis)),
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${game.result} · ${game.movesUci.split(" ").count(String::isNotBlank)} ${stringResource(R.string.moves).lowercase()}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onReview) {
                    Text(stringResource(R.string.review))
                }
                TextButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/x-chess-pgn"
                        putExtra(Intent.EXTRA_TEXT, game.pgn)
                    }
                    context.startActivity(Intent.createChooser(share, null))
                }) {
                    Text(stringResource(R.string.share))
                }
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

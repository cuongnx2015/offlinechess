package com.offlinechess

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.offlinechess.chess.ChessGameViewModel
import com.offlinechess.ui.OfflineChessApp

class MainActivity : AppCompatActivity() {
    private val chessGameViewModel: ChessGameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineChessApp(chessGameViewModel)
        }
    }
}

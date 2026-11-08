package com.offlinechess.chess

data class TrainingPuzzle(
    val fen: String,
    val solution: String,
)

val trainingPuzzles = listOf(
    TrainingPuzzle("7k/8/5KQ1/8/8/8/8/8 w - - 0 1", "g6g7"),
    TrainingPuzzle("k7/8/1QK5/8/8/8/8/8 w - - 0 1", "b6b7"),
    TrainingPuzzle("7k/5K2/8/6Q1/8/8/8/8 w - - 0 1", "g5g7"),
)

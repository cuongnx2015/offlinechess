package com.offlinechess.chess

enum class MoveClassification {
    BEST,
    GOOD,
    INACCURACY,
    MISTAKE,
    BLUNDER,
}

fun classifyCentipawnLoss(loss: Int): MoveClassification {
    require(loss >= 0) { "Centipawn loss must not be negative" }
    return when {
        loss <= 20 -> MoveClassification.BEST
        loss <= 50 -> MoveClassification.GOOD
        loss <= 100 -> MoveClassification.INACCURACY
        loss <= 200 -> MoveClassification.MISTAKE
        else -> MoveClassification.BLUNDER
    }
}

package com.offlinechess.chess

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveClassificationTest {
    @Test
    fun classifiesCentipawnLossAtBoundaries() {
        assertEquals(MoveClassification.BEST, classifyCentipawnLoss(20))
        assertEquals(MoveClassification.GOOD, classifyCentipawnLoss(21))
        assertEquals(MoveClassification.INACCURACY, classifyCentipawnLoss(51))
        assertEquals(MoveClassification.MISTAKE, classifyCentipawnLoss(101))
        assertEquals(MoveClassification.BLUNDER, classifyCentipawnLoss(201))
    }
}

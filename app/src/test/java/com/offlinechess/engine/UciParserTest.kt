package com.offlinechess.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UciParserTest {
    @Test
    fun parsesCentipawnAndMateScoresAndPrincipalVariation() {
        assertEquals(
            UciOutput.Info(
                depth = 14,
                selectiveDepth = 22,
                multiPv = null,
                score = UciOutput.Score(36, false),
                principalVariation = listOf("e2e4", "e7e5"),
            ),
            UciParser.parse("info depth 14 seldepth 22 score cp 36 nodes 456 pv e2e4 e7e5"),
        )
        assertEquals(
            UciOutput.Score(-3, true),
            (UciParser.parse("info multipv 2 score mate -3 pv a2a4") as UciOutput.Info).score,
        )
    }

    @Test
    fun parsesBestMoveAndOptionalPonderMove() {
        assertEquals(
            UciOutput.BestMove("e2e4", "e7e5"),
            UciParser.parse("bestmove e2e4 ponder e7e5"),
        )
        assertEquals(UciOutput.BestMove(null, null), UciParser.parse("bestmove (none)"))
    }

    @Test
    fun parsesInfoStringMessages() {
        assertEquals(
            UciOutput.Message("CRITICAL ERROR position FEN is invalid"),
            UciParser.parse("info string CRITICAL ERROR position FEN is invalid"),
        )
    }

    @Test
    fun ignoresUnrelatedAndMalformedLines() {
        assertNull(UciParser.parse(""))
        assertNull(UciParser.parse("readyok"))
        assertEquals(
            null,
            (UciParser.parse("info score cp not-a-number pv e2e4") as UciOutput.Info).score,
        )
    }
}

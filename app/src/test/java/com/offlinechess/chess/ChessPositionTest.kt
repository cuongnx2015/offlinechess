package com.offlinechess.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessPositionTest {
    @Test
    fun initialPositionMatchesStandardPerft() {
        val position = ChessPosition.initial()

        assertEquals(20, position.legalMoves().size)
        assertEquals(400, position.perft(2))
        assertEquals(8_902, position.perft(3))
    }

    @Test
    fun castlingAndEnPassantAreLegal() {
        val castling = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val castle = castling.legalMoves().first { it.toUci() == "e1g1" }
        val afterCastle = castling.play(castle)
        assertEquals(ChessPiece('R', Side.WHITE), afterCastle.pieceAt(ChessPosition.parseSquare("f1")))
        assertEquals("kq", afterCastle.fen.split(" ")[2])
        assertEquals("O-O", afterCastle.sanMoves.single())

        val enPassant = ChessPosition.initial()
            .play(ChessMove(ChessPosition.parseSquare("e2"), ChessPosition.parseSquare("e4")))
            .play(ChessMove(ChessPosition.parseSquare("a7"), ChessPosition.parseSquare("a6")))
            .play(ChessMove(ChessPosition.parseSquare("e4"), ChessPosition.parseSquare("e5")))
            .play(ChessMove(ChessPosition.parseSquare("d7"), ChessPosition.parseSquare("d5")))
        val epCapture = enPassant.legalMoves().first { it.toUci() == "e5d6" }
        val afterCapture = enPassant.play(epCapture)
        assertNull(afterCapture.pieceAt(ChessPosition.parseSquare("d5")))
        assertEquals(0, afterCapture.halfmoveClock)
    }

    @Test
    fun promotionCreatesFourLegalChoices() {
        val position = ChessPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1")
        val promotions = position.legalMoves(ChessPosition.parseSquare("a7"))
        assertEquals(setOf('Q', 'R', 'B', 'N'), promotions.mapNotNull { it.promotion }.toSet())
        assertEquals(
            ChessPiece('Q', Side.WHITE),
            position.play(promotions.first { it.promotion == 'Q' })
                .pieceAt(ChessPosition.parseSquare("a8")),
        )
    }

    @Test
    fun detectsCheckmateAndStalemate() {
        val checkmate = ChessPosition.fromFen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1")
        assertTrue(checkmate.isCheck)
        assertTrue(checkmate.isCheckmate)
        assertEquals("1-0", checkmate.resultToken())

        val stalemate = ChessPosition.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(stalemate.isCheck)
        assertTrue(stalemate.isStalemate)
        assertEquals("1/2-1/2", stalemate.resultToken())
    }

    @Test
    fun detectsFiftyMoveRuleInsufficientMaterialAndUndo() {
        assertTrue(ChessPosition.fromFen("7k/8/8/8/8/8/8/K6R w - - 100 1").isFiftyMoveDraw)
        assertTrue(ChessPosition.fromFen("7k/8/8/8/8/8/8/K7 w - - 0 1").isInsufficientMaterial)

        val opening = ChessPosition.initial()
            .play(ChessMove(ChessPosition.parseSquare("e2"), ChessPosition.parseSquare("e4")))
        assertNotNull(opening.undo())
        assertEquals(ChessPosition.START_FEN, opening.undo()?.fen)
        assertNull(ChessPosition.initial().undo())
    }

    @Test
    fun detectsThreefoldRepetition() {
        var position = ChessPosition.initial()
        val moves = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        repeat(2) {
            for (uci in moves) {
                position = position.play(position.legalMoves().first { it.toUci() == uci })
            }
        }
        assertTrue(position.isThreefoldRepetition)
    }

    @Test
    fun exportsAndImportsPgnMainline() {
        val position = ChessPosition.replay(listOf("e2e4", "e7e5", "g1f3", "b8c6"))
        val imported = PgnParser.parse(position.toPgn())

        assertEquals(position.fen, imported.fen)
        assertEquals(position.sanMoves, imported.sanMoves)
        assertEquals(ChessPosition.initial().playSan("e4").playSan("e5").fen,
            ChessPosition.replay(listOf("e2e4", "e7e5")).fen)
    }

    @Test
    fun rejectsMovesThatExposeTheKing() {
        val position = ChessPosition.fromFen("k3r3/8/8/8/8/8/4R3/4K3 w - - 0 1")
        assertFalse(
            position.legalMoves(ChessPosition.parseSquare("e2"))
                .any { it.toUci() == "e2a2" },
        )
    }

    @Test
    fun pawnCannotCaptureTheEnemyKing() {
        val position = ChessPosition.fromFen("7k/6P1/8/8/8/8/8/7K w - - 0 1")

        assertFalse(position.legalMoves().any { it.toUci() == "g7h8" })
    }

    @Test
    fun trainingPuzzleSolutionsAreLegalCheckmates() {
        trainingPuzzles.forEach { puzzle ->
            val position = ChessPosition.fromFen(puzzle.fen)
            val solution = position.legalMoves().single { it.toUci() == puzzle.solution }

            assertTrue(position.play(solution).isCheckmate)
        }
    }
}

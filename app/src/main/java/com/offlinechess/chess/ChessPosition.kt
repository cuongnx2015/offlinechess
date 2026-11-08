package com.offlinechess.chess

enum class Side(val pawnStep: Int, val homeRank: Int, val promotionRank: Int) {
    WHITE(1, 0, 7),
    BLACK(-1, 7, 0);

    val opposite: Side
        get() = if (this == WHITE) BLACK else WHITE
}

data class ChessMove(
    val from: Int,
    val to: Int,
    val promotion: Char? = null,
) {
    fun toUci(): String = squareName(from) + squareName(to) + (promotion?.lowercaseChar() ?: "")
}

data class ChessPiece(val type: Char, val side: Side) {
    fun toFen(): Char = if (side == Side.WHITE) type.uppercaseChar() else type.lowercaseChar()
}

class ChessPosition private constructor(
    private val squares: Array<ChessPiece?>,
    val sideToMove: Side,
    private val castlingRights: String,
    private val enPassantSquare: Int?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    val sanMoves: List<String>,
    val uciMoves: List<String>,
    private val positionCounts: Map<String, Int>,
) {
    val fen: String
        get() {
            val ranks = (7 downTo 0).joinToString("/") { rank ->
                var empty = 0
                buildString {
                    for (file in 0..7) {
                        val piece = squares[rank * 8 + file]
                        if (piece == null) {
                            empty++
                        } else {
                            if (empty > 0) {
                                append(empty)
                                empty = 0
                            }
                            append(piece.toFen())
                        }
                    }
                    if (empty > 0) append(empty)
                }
            }
            return "$ranks ${if (sideToMove == Side.WHITE) "w" else "b"} " +
                "${castlingRights.ifEmpty { "-" }} " +
                "${enPassantSquare?.let(::squareName) ?: "-"} $halfmoveClock $fullmoveNumber"
        }

    val isCheck: Boolean
        get() = isKingAttacked(sideToMove, squares)

    val isCheckmate: Boolean
        get() = isCheck && legalMoves().isEmpty()

    val isStalemate: Boolean
        get() = !isCheck && legalMoves().isEmpty()

    val isThreefoldRepetition: Boolean
        get() = positionCounts[positionKey()]?.let { it >= 3 } == true

    val isFiftyMoveDraw: Boolean
        get() = halfmoveClock >= 100

    val isInsufficientMaterial: Boolean
        get() {
            val remaining = squares.filterNotNull().filter { it.type != 'K' }
            if (remaining.isEmpty()) return true
            if (remaining.any { it.type == 'P' || it.type == 'R' || it.type == 'Q' }) return false
            if (remaining.size == 1) return true
            return remaining.all { it.type == 'B' } &&
                squares.indices.filter { squares[it]?.type == 'B' }
                    .map { (it % 8 + it / 8) % 2 }
                    .distinct().size == 1
        }

    val isDraw: Boolean
        get() = isStalemate || isThreefoldRepetition || isFiftyMoveDraw || isInsufficientMaterial

    val isGameOver: Boolean
        get() = isCheckmate || isDraw

    fun pieceAt(square: Int): ChessPiece? {
        require(square in 0..63)
        return squares[square]
    }

    fun legalMoves(from: Int? = null): List<ChessMove> =
        pseudoLegalMoves(from).filter { move ->
            val next = applyUnchecked(move)
            !isKingAttacked(sideToMove, next.squares)
        }

    fun play(move: ChessMove): ChessPosition {
        require(move in legalMoves(move.from)) { "Illegal move: ${move.toUci()}" }
        val movedPiece = squares[move.from] ?: error("No piece on ${squareName(move.from)}")
        val san = toSan(move, movedPiece)
        val next = applyUnchecked(move)
        val nextKey = next.positionKey()
        val nextCounts = positionCounts.toMutableMap()
        nextCounts[nextKey] = (nextCounts[nextKey] ?: 0) + 1
        val nextSan = san + when {
            next.isCheckmate -> "#"
            next.isCheck -> "+"
            else -> ""
        }
        return ChessPosition(
            squares = next.squares,
            sideToMove = next.sideToMove,
            castlingRights = next.castlingRights,
            enPassantSquare = next.enPassantSquare,
            halfmoveClock = next.halfmoveClock,
            fullmoveNumber = next.fullmoveNumber,
            sanMoves = sanMoves + nextSan,
            uciMoves = uciMoves + move.toUci(),
            positionCounts = nextCounts,
        )
    }

    fun playSan(notation: String): ChessPosition {
        val san = notation.trim().replace('0', 'O').replace(Regex("[!?]+$"), "")
        val canonical = san.removeSuffix("+").removeSuffix("#")
        val suppliedCheckSuffix = san.endsWith("+") || san.endsWith("#")
        val matches = legalMoves().filter { move ->
            val actual = play(move).sanMoves.last()
            if (suppliedCheckSuffix) actual == san else actual.removeSuffix("+").removeSuffix("#") == canonical
        }
        require(matches.size == 1) { "Invalid or ambiguous SAN move: $notation" }
        return play(matches.single())
    }

    fun undo(): ChessPosition? {
        if (uciMoves.isEmpty()) return null
        var previous = initial()
        for (uci in uciMoves.dropLast(1)) {
            val move = previous.legalMoves().firstOrNull { it.toUci() == uci }
                ?: error("Invalid move history: $uci")
            previous = previous.play(move)
        }
        return previous
    }

    fun toPgn(result: String = resultToken()): String {
        val date = java.time.LocalDate.now().toString().replace("-", ".")
        val moves = sanMoves.chunked(2).mapIndexed { index, pair ->
            "${index + 1}. ${pair.joinToString(" ")}"
        }.joinToString(" ")
        return """
            |[Event "Offline Chess"]
            |[Site "Offline Chess"]
            |[Date "$date"]
            |[Round "-"]
            |[White "White"]
            |[Black "Black"]
            |[Result "$result"]
            |
            |${listOf(moves, result).filter(String::isNotBlank).joinToString(" ")}
        """.trimMargin()
    }

    fun resultToken(): String = when {
        isCheckmate && sideToMove == Side.WHITE -> "0-1"
        isCheckmate -> "1-0"
        isDraw -> "1/2-1/2"
        else -> "*"
    }

    fun perft(depth: Int): Long {
        require(depth >= 0)
        if (depth == 0) return 1
        return legalMoves().sumOf { play(it).perft(depth - 1) }
    }

    private fun toSan(move: ChessMove, piece: ChessPiece): String {
        if (piece.type == 'K' && kotlin.math.abs(move.to - move.from) == 2) {
            return if (move.to > move.from) "O-O" else "O-O-O"
        }
        val target = squares[move.to]
        val isCapture = target != null || (piece.type == 'P' && move.to == enPassantSquare)
        val notation = buildString {
            if (piece.type != 'P') {
                append(piece.type)
                val contenders = legalMoves().filter {
                    it.to == move.to && it.from != move.from &&
                        squares[it.from]?.let { other ->
                            other.type == piece.type && other.side == piece.side
                        } == true
                }
                if (contenders.isNotEmpty()) {
                    val sharesFile = contenders.any { it.from % 8 == move.from % 8 }
                    val sharesRank = contenders.any { it.from / 8 == move.from / 8 }
                    when {
                        !sharesFile -> append(('a'.code + move.from % 8).toChar())
                        !sharesRank -> append(('1'.code + move.from / 8).toChar())
                        else -> append(squareName(move.from))
                    }
                }
            } else if (isCapture) {
                append(('a'.code + move.from % 8).toChar())
            }
            if (isCapture) append('x')
            append(squareName(move.to))
            if (move.promotion != null) append("=${move.promotion.uppercaseChar()}")
        }
        return notation
    }

    private fun pseudoLegalMoves(from: Int?): List<ChessMove> {
        val result = mutableListOf<ChessMove>()
        for (square in 0..63) {
            if (from != null && square != from) continue
            val piece = squares[square] ?: continue
            if (piece.side != sideToMove) continue
            val file = square % 8
            val rank = square / 8
            when (piece.type) {
                'P' -> {
                    val nextRank = rank + piece.side.pawnStep
                    if (nextRank in 0..7) {
                        val oneAhead = nextRank * 8 + file
                        if (squares[oneAhead] == null) {
                            addPawnMove(result, square, oneAhead, piece.side)
                            val twoRank = rank + 2 * piece.side.pawnStep
                            if (rank == (if (piece.side == Side.WHITE) 1 else 6) &&
                                squares[twoRank * 8 + file] == null
                            ) {
                                result.add(ChessMove(square, twoRank * 8 + file))
                            }
                        }
                        for (captureFile in intArrayOf(file - 1, file + 1)) {
                            if (captureFile !in 0..7) continue
                            val target = nextRank * 8 + captureFile
                            if (squares[target]?.side == piece.side.opposite ||
                                target == enPassantSquare
                            ) {
                                if (squares[target]?.type != 'K') {
                                    addPawnMove(result, square, target, piece.side)
                                }
                            }
                        }
                    }
                }

                'N' -> for ((df, dr) in KNIGHT_STEPS) {
                    addIfAvailable(result, square, file + df, rank + dr)
                }

                'K' -> {
                    for ((df, dr) in KING_STEPS) {
                        addIfAvailable(result, square, file + df, rank + dr)
                    }
                    addCastlingMoves(result, square, piece.side)
                }

                'B', 'R', 'Q' -> {
                    val directions = when (piece.type) {
                        'B' -> DIAGONAL_STEPS
                        'R' -> ORTHOGONAL_STEPS
                        else -> KING_STEPS
                    }
                    for ((df, dr) in directions) {
                        var targetFile = file + df
                        var targetRank = rank + dr
                        while (targetFile in 0..7 && targetRank in 0..7) {
                            val target = targetRank * 8 + targetFile
                            val occupant = squares[target]
                            if (occupant == null) {
                                result.add(ChessMove(square, target))
                            } else {
                                if (occupant.side != piece.side && occupant.type != 'K') {
                                    result.add(ChessMove(square, target))
                                }
                                break
                            }
                            targetFile += df
                            targetRank += dr
                        }
                    }
                }
            }
        }
        return result
    }

    private fun addIfAvailable(
        moves: MutableList<ChessMove>,
        from: Int,
        file: Int,
        rank: Int,
    ) {
        if (file !in 0..7 || rank !in 0..7) return
        val target = rank * 8 + file
        if (squares[target]?.let { it.side != sideToMove && it.type != 'K' } ?: true) {
            moves.add(ChessMove(from, target))
        }
    }

    private fun addPawnMove(
        moves: MutableList<ChessMove>,
        from: Int,
        to: Int,
        side: Side,
    ) {
        if (to / 8 == side.promotionRank) {
            for (piece in PROMOTION_PIECES) moves.add(ChessMove(from, to, piece))
        } else {
            moves.add(ChessMove(from, to))
        }
    }

    private fun addCastlingMoves(moves: MutableList<ChessMove>, king: Int, side: Side) {
        val home = if (side == Side.WHITE) 4 else 60
        if (king != home || isKingAttacked(side, squares)) return
        val opponent = side.opposite
        if ("${if (side == Side.WHITE) "K" else "k"}" in castlingRights &&
            squares[home + 1] == null && squares[home + 2] == null &&
            squares[home + 3] == ChessPiece('R', side) &&
            !isSquareAttacked(home + 1, opponent, squares) &&
            !isSquareAttacked(home + 2, opponent, squares)
        ) {
            moves.add(ChessMove(home, home + 2))
        }
        if ("${if (side == Side.WHITE) "Q" else "q"}" in castlingRights &&
            squares[home - 1] == null && squares[home - 2] == null && squares[home - 3] == null &&
            squares[home - 4] == ChessPiece('R', side) &&
            !isSquareAttacked(home - 1, opponent, squares) &&
            !isSquareAttacked(home - 2, opponent, squares)
        ) {
            moves.add(ChessMove(home, home - 2))
        }
    }

    private fun applyUnchecked(move: ChessMove): ChessPosition {
        val nextSquares = squares.copyOf()
        val moving = nextSquares[move.from] ?: error("No piece on ${squareName(move.from)}")
        val target = nextSquares[move.to]
        nextSquares[move.from] = null
        if (moving.type == 'P' && move.to == enPassantSquare && target == null) {
            nextSquares[move.to - moving.side.pawnStep * 8] = null
        }
        nextSquares[move.to] = ChessPiece(move.promotion ?: moving.type, moving.side)

        if (moving.type == 'K' && kotlin.math.abs(move.to - move.from) == 2) {
            val rookFrom = if (move.to > move.from) move.from + 3 else move.from - 4
            val rookTo = if (move.to > move.from) move.from + 1 else move.from - 1
            nextSquares[rookTo] = nextSquares[rookFrom]
            nextSquares[rookFrom] = null
        }

        var rights = castlingRights
        if (moving.type == 'K') {
            rights = if (moving.side == Side.WHITE) {
                rights.filterNot { it == 'K' || it == 'Q' }
            } else {
                rights.filterNot { it == 'k' || it == 'q' }
            }
        }
        if (move.from == 0 || move.to == 0) rights = rights.replace("Q", "")
        if (move.from == 7 || move.to == 7) rights = rights.replace("K", "")
        if (move.from == 56 || move.to == 56) rights = rights.replace("q", "")
        if (move.from == 63 || move.to == 63) rights = rights.replace("k", "")

        val ep = if (moving.type == 'P' && kotlin.math.abs(move.to - move.from) == 16) {
            (move.from + move.to) / 2
        } else {
            null
        }
        val nextSide = sideToMove.opposite
        return ChessPosition(
            squares = nextSquares,
            sideToMove = nextSide,
            castlingRights = rights,
            enPassantSquare = ep,
            halfmoveClock = if (moving.type == 'P' || target != null || move.to == enPassantSquare) 0 else halfmoveClock + 1,
            fullmoveNumber = fullmoveNumber + if (sideToMove == Side.BLACK) 1 else 0,
            sanMoves = sanMoves,
            uciMoves = uciMoves,
            positionCounts = positionCounts,
        )
    }

    private fun positionKey(): String = fen.split(" ").take(4).joinToString(" ")

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private val KNIGHT_STEPS = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        private val KING_STEPS = arrayOf(1 to 1, 1 to 0, 1 to -1, 0 to 1, 0 to -1, -1 to 1, -1 to 0, -1 to -1)
        private val DIAGONAL_STEPS = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
        private val ORTHOGONAL_STEPS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val PROMOTION_PIECES = charArrayOf('Q', 'R', 'B', 'N')

        fun initial(): ChessPosition = fromFen(START_FEN)

        fun fromFen(fen: String): ChessPosition {
            val fields = fen.trim().split(Regex("\\s+"))
            require(fields.size == 6) { "FEN must contain six fields" }
            val squares = arrayOfNulls<ChessPiece>(64)
            val ranks = fields[0].split("/")
            require(ranks.size == 8) { "FEN must contain eight ranks" }
            ranks.forEachIndexed { fenRank, row ->
                var file = 0
                for (symbol in row) {
                    if (symbol in '1'..'8') {
                        file += symbol.digitToInt()
                    } else {
                        require(symbol.lowercaseChar() in "pnbrqk" && file < 8) { "Invalid FEN piece: $symbol" }
                        val side = if (symbol.isUpperCase()) Side.WHITE else Side.BLACK
                        squares[(7 - fenRank) * 8 + file] = ChessPiece(symbol.uppercaseChar(), side)
                        file++
                    }
                }
                require(file == 8) { "Invalid FEN rank: $row" }
            }
            require(squares.count { it == ChessPiece('K', Side.WHITE) } == 1 &&
                squares.count { it == ChessPiece('K', Side.BLACK) } == 1
            ) { "FEN must contain exactly one king per side" }
            val side = when (fields[1]) {
                "w" -> Side.WHITE
                "b" -> Side.BLACK
                else -> throw IllegalArgumentException("Invalid FEN active color")
            }
            require(fields[2] == "-" || fields[2].all { it in "KQkq" }) { "Invalid FEN castling rights" }
            val ep = if (fields[3] == "-") null else parseSquare(fields[3])
            val halfmove = fields[4].toIntOrNull()?.also { require(it >= 0) }
                ?: throw IllegalArgumentException("Invalid FEN halfmove clock")
            val fullmove = fields[5].toIntOrNull()?.also { require(it > 0) }
                ?: throw IllegalArgumentException("Invalid FEN fullmove number")
            val base = ChessPosition(
                squares = squares,
                sideToMove = side,
                castlingRights = fields[2].replace("-", ""),
                enPassantSquare = ep,
                halfmoveClock = halfmove,
                fullmoveNumber = fullmove,
                sanMoves = emptyList(),
                uciMoves = emptyList(),
                positionCounts = emptyMap(),
            )
            return ChessPosition(
                squares = squares,
                sideToMove = side,
                castlingRights = base.castlingRights,
                enPassantSquare = ep,
                halfmoveClock = halfmove,
                fullmoveNumber = fullmove,
                sanMoves = emptyList(),
                uciMoves = emptyList(),
                positionCounts = mapOf(base.positionKey() to 1),
            )
        }

        fun replay(moves: List<String>): ChessPosition =
            moves.fold(initial()) { position, uci ->
                val move = position.legalMoves().firstOrNull { it.toUci() == uci }
                    ?: throw IllegalArgumentException("Invalid UCI move in game record: $uci")
                position.play(move)
            }

        fun parseSquare(name: String): Int {
            require(name.length == 2 && name[0] in 'a'..'h' && name[1] in '1'..'8') {
                "Invalid chess square: $name"
            }
            return (name[1] - '1') * 8 + (name[0] - 'a')
        }

        fun squareName(square: Int): String {
            require(square in 0..63)
            return "${('a'.code + square % 8).toChar()}${('1'.code + square / 8).toChar()}"
        }

        private fun isKingAttacked(side: Side, board: Array<ChessPiece?>): Boolean {
            val king = board.indexOfFirst { it == ChessPiece('K', side) }
            return king == -1 || isSquareAttacked(king, side.opposite, board)
        }

        private fun isSquareAttacked(
            square: Int,
            bySide: Side,
            board: Array<ChessPiece?>,
        ): Boolean {
            val file = square % 8
            val rank = square / 8
            val pawnSourceRank = rank - bySide.pawnStep
            for (pawnFile in intArrayOf(file - 1, file + 1)) {
                if (pawnFile in 0..7 && pawnSourceRank in 0..7 &&
                    board[pawnSourceRank * 8 + pawnFile] == ChessPiece('P', bySide)
                ) return true
            }
            for ((df, dr) in KNIGHT_STEPS) {
                val f = file + df
                val r = rank + dr
                if (f in 0..7 && r in 0..7 && board[r * 8 + f] == ChessPiece('N', bySide)) {
                    return true
                }
            }
            for ((df, dr) in KING_STEPS) {
                val f = file + df
                val r = rank + dr
                if (f in 0..7 && r in 0..7 && board[r * 8 + f] == ChessPiece('K', bySide)) {
                    return true
                }
            }
            for ((df, dr) in KING_STEPS) {
                val diagonal = df != 0 && dr != 0
                var f = file + df
                var r = rank + dr
                    while (f in 0..7 && r in 0..7) {
                        val piece = board[r * 8 + f]
                        if (piece == null) {
                            f += df
                            r += dr
                            continue
                        }
                    if (piece.side == bySide &&
                        (piece.type == 'Q' || (diagonal && piece.type == 'B') ||
                            (!diagonal && piece.type == 'R'))
                    ) return true
                    break
                }
            }
            return false
        }
    }
}

fun squareName(square: Int): String = ChessPosition.squareName(square)

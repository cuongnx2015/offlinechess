package com.offlinechess.engine

sealed interface UciOutput {
    data class Info(
        val depth: Int?,
        val selectiveDepth: Int?,
        val multiPv: Int?,
        val score: Score?,
        val principalVariation: List<String>,
    ) : UciOutput

    data class BestMove(
        val move: String?,
        val ponder: String?,
    ) : UciOutput

    data class Score(
        val value: Int,
        val isMate: Boolean,
    )

    data class Message(val text: String) : UciOutput
}

object UciParser {
    fun parse(line: String): UciOutput? {
        val tokens = line.trim().split(Regex("\\s+"))
        if (tokens.isEmpty() || tokens.first().isEmpty()) return null
        return when (tokens.first()) {
            "bestmove" -> {
                val move = tokens.getOrNull(1)?.takeUnless { it == "(none)" || it == "0000" }
                val ponderIndex = tokens.indexOf("ponder")
                val ponder = if (ponderIndex >= 0) tokens.getOrNull(ponderIndex + 1) else null
                UciOutput.BestMove(move, ponder)
            }

            "info" -> if (tokens.getOrNull(1) == "string") {
                UciOutput.Message(tokens.drop(2).joinToString(" "))
            } else {
                parseInfo(tokens)
            }
            else -> null
        }
    }

    private fun parseInfo(tokens: List<String>): UciOutput? {
        fun valueAfter(key: String): String? {
            val index = tokens.indexOf(key)
            return tokens.getOrNull(index + 1)
        }

        val scoreIndex = tokens.indexOf("score")
        val score = if (scoreIndex >= 0) {
            val value = tokens.getOrNull(scoreIndex + 2)?.toIntOrNull()
            val type = tokens.getOrNull(scoreIndex + 1)
            if (value != null && (type == "cp" || type == "mate")) {
                UciOutput.Score(value, type == "mate")
            } else {
                null
            }
        } else {
            null
        }
        val pvIndex = tokens.indexOf("pv")
        return UciOutput.Info(
            depth = valueAfter("depth")?.toIntOrNull(),
            selectiveDepth = valueAfter("seldepth")?.toIntOrNull(),
            multiPv = valueAfter("multipv")?.toIntOrNull(),
            score = score,
            principalVariation = if (pvIndex >= 0) tokens.drop(pvIndex + 1) else emptyList(),
        )
    }
}

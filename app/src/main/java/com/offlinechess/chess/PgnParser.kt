package com.offlinechess.chess

object PgnParser {
    fun parse(text: String): ChessPosition {
        require(text.isNotBlank()) { "PGN file is empty" }
        val mainline = removeCommentsAndVariations(text)
            .lineSequence()
            .filterNot { it.trimStart().startsWith("[") }
            .joinToString(" ")
        val tokens = mainline.split(Regex("\\s+"))
            .map { it.trim().replace(Regex("^\\d+\\.(?:\\.\\.)?"), "") }
            .filter(String::isNotBlank)

        var position = ChessPosition.initial()
        for (token in tokens) {
            if (token in RESULTS) break
            if (token.startsWith("$") || token == "e.p." || token == "--") continue
            position = position.playSan(token)
        }
        return position
    }

    private fun removeCommentsAndVariations(pgn: String): String {
        val result = StringBuilder()
        var braceDepth = 0
        var variationDepth = 0
        var semicolonComment = false
        for (character in pgn) {
            when {
                semicolonComment -> {
                    if (character == '\n') {
                        semicolonComment = false
                        result.append(' ')
                    }
                }

                braceDepth > 0 -> {
                    if (character == '{') braceDepth++
                    if (character == '}') braceDepth--
                }

                variationDepth > 0 -> {
                    if (character == '(') variationDepth++
                    if (character == ')') variationDepth--
                }

                character == ';' -> semicolonComment = true
                character == '{' -> braceDepth = 1
                character == '(' -> variationDepth = 1
                else -> result.append(character)
            }
        }
        require(braceDepth == 0) { "Unterminated PGN comment" }
        require(variationDepth == 0) { "Unterminated PGN variation" }
        return result.toString()
    }

    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")
}

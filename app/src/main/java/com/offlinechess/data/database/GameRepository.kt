package com.offlinechess.data.database

import android.content.Context

class GameRepository(context: Context) {
    private val dao = ChessDatabase.get(context).games()

    fun observeGames() = dao.observeAll()

    suspend fun getGame(id: String): ChessGameEntity? = dao.getById(id)

    suspend fun latestIncomplete(): ChessGameEntity? = dao.getLatestIncomplete()

    suspend fun save(game: ChessGameEntity) = dao.save(game)

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()
}

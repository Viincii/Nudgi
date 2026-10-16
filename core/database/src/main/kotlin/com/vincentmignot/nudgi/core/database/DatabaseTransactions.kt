package com.vincentmignot.nudgi.core.database

/**
 * Runs several DAO calls as one SQLite transaction. Room itself stays an implementation detail of
 * this module, so callers that need atomicity, such as an import, go through this instead.
 */
interface DatabaseTransactions {
    suspend fun <R> run(block: suspend () -> R): R
}

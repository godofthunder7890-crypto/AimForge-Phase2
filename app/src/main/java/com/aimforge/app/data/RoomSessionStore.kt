package com.aimforge.app.data

import androidx.room.withTransaction
import com.aimforge.app.domain.SessionStore

class RoomSessionStore(private val db: AppDatabase) : SessionStore {
    override suspend fun insert(session: TestSessionEntity) = db.sessionDao().insert(session)
    override suspend fun get(sessionId: String) = db.sessionDao().get(sessionId)
    override suspend fun update(session: TestSessionEntity) = db.sessionDao().update(session)
    override suspend fun findActive() = db.sessionDao().findActive()
    override suspend fun insertCapture(capture: CaptureEntity) = db.captureDao().insert(capture)
    override suspend fun updateCapture(capture: CaptureEntity) = db.captureDao().update(capture)
    override suspend fun captureForSession(sessionId: String) = db.captureDao().latestForSession(sessionId)

    /** Session, its captures and any diagnostic step link go together. */
    override suspend fun delete(sessionId: String) {
        db.withTransaction {
            db.captureDao().deleteForSession(sessionId)
            db.diagnosticDao().unlinkSession(sessionId)
            db.sessionDao().delete(sessionId)
        }
    }
}

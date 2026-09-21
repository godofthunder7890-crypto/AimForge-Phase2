package com.aimforge.app.domain

import com.aimforge.app.data.TestSessionEntity

/** In-memory store for JVM tests. Starts EMPTY, like a fresh install. */
class FakeSessionStore : SessionStore {
    val rows = linkedMapOf<String, TestSessionEntity>()
    override suspend fun insert(session: TestSessionEntity) { rows[session.sessionId] = session }
    override suspend fun get(sessionId: String) = rows[sessionId]
    override suspend fun update(session: TestSessionEntity) { rows[session.sessionId] = session }
    override suspend fun delete(sessionId: String) { rows.remove(sessionId) }
    override suspend fun findActive() = rows.values.firstOrNull { it.isActive }
}

class FakeCaptureEngine(private val available: Availability) : CaptureEngine {
    override fun availability() = available
    override suspend fun start(sessionId: String) = CaptureOutcome.NotImplemented
    override suspend fun stop(sessionId: String) = CaptureOutcome.NotImplemented
}

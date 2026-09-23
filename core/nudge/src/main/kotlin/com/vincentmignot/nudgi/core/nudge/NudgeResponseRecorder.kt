package com.vincentmignot.nudgi.core.nudge

import com.vincentmignot.nudgi.core.database.EventDao
import javax.inject.Inject

class NudgeResponseRecorder
    @Inject
    constructor(
        private val eventDao: EventDao,
    ) {
        suspend fun record(
            nudgeId: String,
            packageName: String,
            response: NudgeResponse,
            now: Long = System.currentTimeMillis(),
        ) {
            eventDao.insert(responseEvent(nudgeId, packageName, response, now))
        }
    }

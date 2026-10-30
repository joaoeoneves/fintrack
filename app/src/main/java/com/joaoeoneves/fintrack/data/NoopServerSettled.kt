package com.joaoeoneves.fintrack.data

import com.joaoeoneves.fintrack.domain.repository.ServerSettled
import javax.inject.Inject

/** Temporary stub: confirms immediately. */
class NoopServerSettled
    @Inject
    constructor() : ServerSettled {
        override suspend fun awaitPendingWrites() = Unit
    }

package com.joaoeoneves.fintrack.data.session

import android.app.Application
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [resubscribeOnError] keeps a long-lived observer alive across listener errors, backing off in between. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ResubscribeOnErrorTest {
    @Test
    fun errorThenValue_resubscribesAndDeliversTheValue() =
        runTest {
            var subscriptions = 0
            val source =
                flow {
                    subscriptions++
                    if (subscriptions < 3) throw IllegalStateException("listener failed")
                    emit("ok")
                }

            val result = source.resubscribeOnError("test").take(1).toList()

            assertEquals(listOf("ok"), result)
            assertEquals(3, subscriptions)
        }

    @Test
    fun backoff_doublesFromOneSecond() =
        runTest {
            var subscriptions = 0
            val source =
                flow {
                    subscriptions++
                    if (subscriptions < 4) throw IllegalStateException("x")
                    emit(Unit)
                }

            source.resubscribeOnError("test").take(1).toList()

            // 1 s + 2 s + 4 s of virtual time before the fourth subscription.
            assertEquals(7_000L, currentTime)
        }

    @Test
    fun backoff_isCappedAtOneMinute() =
        runTest {
            var subscriptions = 0
            val source =
                flow {
                    subscriptions++
                    if (subscriptions < 10) throw IllegalStateException("x")
                    emit(Unit)
                }

            source.resubscribeOnError("test").take(1).toList()

            // 1+2+4+8+16+32 s, then 60 s (capped) for each of the remaining three retries.
            assertEquals(63_000L + 3 * 60_000L, currentTime)
        }

    @Test
    fun healthyFlow_passesThroughUntouched() =
        runTest {
            val result = flow { emit(1).also { emit(2) } }.resubscribeOnError("test").toList()

            assertEquals(listOf(1, 2), result)
            assertEquals(0L, currentTime)
        }
}

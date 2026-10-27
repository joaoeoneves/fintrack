package com.joaoeoneves.fintrack.data.session

import com.joaoeoneves.fintrack.domain.model.AuthUser
import com.joaoeoneves.fintrack.domain.model.Session
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataSourceTest {
    private fun user(uid: String) = AuthUser(uid = uid, displayName = null, email = null, photoUrl = null)

    @Test
    fun mapsEachSession_skippingLoading() =
        runTest {
            val result =
                flowOf(
                    Session.Loading,
                    Session.Guest,
                    Session.SignedOut,
                    Session.SignedIn(user("a")),
                ).toDataSource().toList()
            assertEquals(
                listOf(DataSource.Local, DataSource.Remote(null), DataSource.Remote("a")),
                result,
            )
        }

    @Test
    fun distinctUntilChanged_collapsesRepeats_andSameUidDifferentProfile() =
        runTest {
            val result =
                flowOf(
                    Session.Guest,
                    Session.Guest,
                    Session.SignedIn(user("a")),
                    Session.SignedIn(user("a").copy(displayName = "Renamed")),
                    Session.SignedIn(user("b")),
                    Session.Loading,
                    Session.SignedIn(user("b")),
                ).toDataSource().toList()
            assertEquals(
                listOf(DataSource.Local, DataSource.Remote("a"), DataSource.Remote("b")),
                result,
            )
        }

    @Test
    fun onlyLoading_emitsNothing() =
        runTest {
            assertEquals(emptyList<DataSource>(), flowOf(Session.Loading).toDataSource().toList())
        }
}

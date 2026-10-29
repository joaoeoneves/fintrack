package com.joaoeoneves.fintrack.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.joaoeoneves.fintrack.MainActivity
import com.joaoeoneves.fintrack.testutil.FirebaseTestApp
import com.joaoeoneves.fintrack.testutil.TransactionGate
import com.joaoeoneves.fintrack.ui.common.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Skeleton loading on real screens (guest mode, fake sign-in). The main clock is paused so the first frame of a
 * freshly navigated screen, which always renders the initial Loading state, can be observed deterministically.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "en-rUS-w411dp-h891dp")
class FinTrackSkeletonScreenTest {
    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    @Inject
    lateinit var gate: TransactionGate

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    private val skeleton = hasTestTag(TestTags.SKELETON)
    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Before
    fun setUp() {
        FirebaseTestApp.ensureInitialized(context)
        hiltRule.inject()
        gate.hold = null
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun count(matcher: SemanticsMatcher) = composeRule.onAllNodes(matcher).fetchSemanticsNodes().size

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(WAIT_MS) { count(hasTestTag(tag)) > 0 }
    }

    private fun awaitNoSkeleton() {
        composeRule.waitUntil(WAIT_MS) { count(skeleton) == 0 }
    }

    private fun continueAsGuest() {
        awaitTag(TestTags.CONTINUE_AS_GUEST)
        composeRule.onNode(hasTestTag(TestTags.CONTINUE_AS_GUEST)).performClick()
    }

    /** Taps [tag] with the clock paused and steps a few frames, returning with the clock still paused. */
    private fun tapPaused(tag: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(hasTestTag(tag)).performClick()
        stepUntilSkeleton()
    }

    /**
     * Steps frames (paused clock) until a skeleton host is composed; fails if none shows within [SKELETON_DEADLINE_MS].
     * Frames alone are not enough: the guest-session switch and navigation hop through real background threads, so
     * a fixed frame count can be exhausted in microseconds before they deliver. Sleeping between frames bounds the
     * wait in wall-clock time instead, independent of runner speed.
     */
    private fun stepUntilSkeleton() {
        val deadline = System.nanoTime() + SKELETON_DEADLINE_MS * NANOS_PER_MS
        while (System.nanoTime() < deadline) {
            if (count(skeleton) > 0) return
            composeRule.mainClock.advanceTimeByFrame()
            Thread.sleep(FRAME_PAUSE_MS)
        }
        throw AssertionError("No skeleton appeared within $SKELETON_DEADLINE_MS ms")
    }

    @Test
    fun spending_showsSkeletonUntilLoaded() {
        continueAsGuest()
        awaitTag(TestTags.NAV_SPENDING)
        awaitNoSkeleton() // the Dashboard's own skeleton must be gone, so the one seen next is Savings'
        tapPaused(TestTags.NAV_SPENDING)
        assertEquals(1, count(skeleton))

        composeRule.mainClock.autoAdvance = true
        awaitNoSkeleton()
    }

    @Test
    fun dashboard_showsSkeletonUntilLoaded() {
        // Hold the dashboard's data so Loading cannot be skipped by a fast guest load.
        val release = CompletableDeferred<Unit>()
        gate.hold = release
        awaitTag(TestTags.CONTINUE_AS_GUEST)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(hasTestTag(TestTags.CONTINUE_AS_GUEST)).performClick()
        stepUntilSkeleton()
        assertEquals(1, count(skeleton))

        release.complete(Unit)
        composeRule.mainClock.autoAdvance = true
        awaitNoSkeleton()
    }

    @Test
    fun addExpenseForm_inLoadingShowsNoProgressIndicator() {
        continueAsGuest()
        awaitTag(TestTags.DASHBOARD_ADD_EXPENSE)
        composeRule.mainClock.autoAdvance = false
        composeRule.onNode(hasTestTag(TestTags.DASHBOARD_ADD_EXPENSE)).performClick()
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals(0, count(progress))
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals(0, count(progress))
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val FRAMES = 3
        const val SKELETON_DEADLINE_MS = 20_000L
        const val FRAME_PAUSE_MS = 5L
        const val NANOS_PER_MS = 1_000_000L
    }
}

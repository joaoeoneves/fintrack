package com.joaoeoneves.fintrack.testutil

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * Minimal stand-in for a real Android `Context`, usable in both plain JVM unit tests and
 * Robolectric-backed ones, whose only job is to make `context.getString(resId)` return a
 * caller-controlled, canned value instead of resolving a real Android resource.
 *
 * Several ViewModels in this app follow the pattern:
 * ```
 * context?.getString(R.string.some_error) ?: "Some hardcoded English fallback"
 * ```
 * where `context` is a nullable `@ApplicationContext Context?` defaulted to `null` specifically so
 * pre-existing unit tests that construct the ViewModel directly (bypassing Hilt) keep compiling and
 * exercise the `null` (fallback-string) branch. This class exists so tests can *also* exercise the
 * other branch -- "a real Context was supplied" -- and prove the ViewModel actually calls
 * `context.getString(...)` and uses its result, without depending on Robolectric's real
 * resource-loading pipeline.
 *
 * That pipeline is currently non-functional in this module: `app/build.gradle.kts` does not set
 * `android.testOptions.unitTests.isIncludeAndroidResources = true`, so Robolectric never loads this
 * app's own resources.arsc into its resource table (confirmed via `No package ID 7f found for ID
 * 0x7f...` errors when calling `RuntimeEnvironment.getApplication().getString(R.string.anything)` --
 * even `R.string.app_name` -- reproducible on `main` before this feature branch, i.e. pre-existing,
 * not a regression). Enabling that flag is a build-config change outside `tester`'s editable scope
 * (`app/build.gradle.kts` is guarded the same as everything under `app/src/main`); see the QA
 * report for the `coder`/build-owning pass this needs. Until then, [FakeStringContext] is what lets
 * the "Context provided" branch get real, working coverage anyway.
 *
 * Implementation note: `Context.getString(int)` is `final` (it delegates internally to
 * `getResources().getString(resId)`), so it can't be overridden directly. Instead, this class
 * overrides the (non-final) `getResources()` to return a [Resources] instance -- constructed via
 * its deprecated `(AssetManager, DisplayMetrics, Configuration)` constructor, fed with
 * [Resources.getSystem]'s own (framework "boot" resources, real under both a plain JVM unit test
 * and Robolectric) `assets`/`displayMetrics`/`configuration` so construction itself doesn't throw --
 * whose own `getString(int)` (also non-final) is overridden to serve [stubs].
 *
 * Every resource id not explicitly stubbed via [stubs] resolves to a value containing
 * [UNSTUBBED_MARKER] plus the id, so a test can assert failure clearly if it forgot to stub an id it
 * cares about, and so [UNSTUBBED_MARKER] itself is always trivially distinguishable from any
 * hardcoded English fallback string used by production code.
 */
class FakeStringContext(
    private val stubs: Map<Int, String>,
    base: Context? = null,
) : ContextWrapper(base) {
    constructor(resId: Int, value: String, base: Context? = null) : this(mapOf(resId to value), base)

    private val fakeResources =
        @Suppress("DEPRECATION")
        object : Resources(
            Resources.getSystem().assets,
            Resources.getSystem().displayMetrics,
            Resources.getSystem().configuration,
        ) {
            override fun getString(id: Int): String = stubs[id] ?: "$UNSTUBBED_MARKER:$id"
        }

    override fun getResources(): Resources = fakeResources

    companion object {
        const val UNSTUBBED_MARKER = "FAKE_STRING_CONTEXT_UNSTUBBED"
    }
}

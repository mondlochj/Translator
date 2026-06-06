package com.arosys.meetingassistant.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 rule that installs a [TestDispatcher] as [Dispatchers.Main].
 * Drop this into any test that touches coroutines or StateFlow collectors.
 *
 * Usage:
 * ```
 * @get:Rule val mainDispatcherRule = MainDispatcherRule()
 * ```
 *
 * Use [UnconfinedTestDispatcher] (default) to run coroutines eagerly in tests.
 * Swap for [StandardTestDispatcher] when you need fine-grained control over
 * coroutine scheduling (advance time manually with [TestScope.advanceUntilIdle]).
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

package app.zelgray.pills_in_time

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Golden-path instrumented test (spec 4.1/4.2/4.3/4.7/4.6): add a drug, give
 * it a stock batch, schedule a period, confirm it shows on Home, mark it
 * taken, and confirm it shows up in History. Runs against a fresh in-memory
 * Room DB (TestDatabaseModule) so it doesn't depend on or pollute any real
 * device data.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HappyPathTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Pre-grants POST_NOTIFICATIONS (API 33+) so MainActivity's own runtime
    // permission prompt never appears — a system dialog would otherwise sit
    // on top of the Compose tree and swallow the test's clicks.
    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @Test
    fun addDrug_addStock_schedulePeriod_showsOnHome_takeIt_showsInHistory() {
        // --- Drugs tab: add a drug ---
        composeRule.onNodeWithText("Drugs").performClick()
        composeRule.onNodeWithContentDescription("Add drug").performClick()
        composeRule.onNodeWithText("Name").performTextInput("Metformin")
        composeRule.onNodeWithText("Save").performClick()

        // --- Drug detail: add a stock batch ---
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Add stock").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Add stock").performClick()
        composeRule.onNodeWithText("Quantity").performTextInput("30")
        composeRule.onNodeWithText("Value").performTextInput("500")
        composeRule.onNodeWithText("Save").performClick()

        // --- Drug detail: add a period (defaults: daily, 7 days, 08:00, dose 1) ---
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Add period").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Add period").performClick()
        composeRule.onNodeWithText("Save").performClick()

        // Back on the drug detail sub-screen now — the bottom nav bar is
        // hidden there, so return to a tab route (Drugs list) before
        // switching tabs.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Back").performClick()

        // --- Home: the scheduled occurrence appears; mark it taken ---
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Metformin").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Took it").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Taken").fetchSemanticsNodes().isNotEmpty()
        }

        // --- History: the taken dose is recorded, tagged as a reminder-sourced action ---
        composeRule.onNodeWithText("History").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Reminder", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

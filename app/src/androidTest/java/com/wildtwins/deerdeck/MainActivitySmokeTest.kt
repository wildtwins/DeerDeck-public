package com.wildtwins.deerdeck

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickWildlifeCountIsVisible() {
        composeRule.onNodeWithText("Start").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Doe").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Doe").performClick()

        composeRule.onNodeWithText("1").assertIsDisplayed()
    }
}

package com.tveaker.app.ui.screens

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tveaker.app.data.model.AppVersionDto
import com.tveaker.app.ui.theme.TVeakerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorialUpdateNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun availableUpdateIsVisibleAndOpensUpdateControls() {
        var opened = false
        val version = AppVersionDto(
            versionCode = 7,
            versionName = "1.5.0",
            apkUrl = "/api/v1/app/download-apk",
            changelog = "A new release"
        )

        composeRule.setContent {
            TVeakerTheme(darkTheme = false) {
                EditorialUpdateNotice(version = version, onOpenUpdates = { opened = true })
            }
        }

        composeRule.onNodeWithText("UPDATE READY").assertIsDisplayed()
        composeRule.onNodeWithText("OPEN OTA").assertHasClickAction().performClick()
        assertTrue(opened)
    }
}

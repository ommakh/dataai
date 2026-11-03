package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.ExtractionResult
import com.example.ui.screens.ExtractionResultCard
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        ExtractionResultCard(
          result = ExtractionResult(
            name = "Jane Doe",
            email = "jane@techcorp.io",
            phone = "+1 (555) 019-2834",
            company = "TechCorp Systems",
            jobTitle = "Chief Executive Officer",
            website = "https://techcorp.io",
            address = "100 Market St, San Francisco, CA",
            notes = "Extracted via DataExtract AI Agent",
            confidence = 94
          ),
          autoSyncActive = true,
          onSaveToLocalDb = {},
          onSaveAndSync = {},
          onExportToExcel = {},
          onDiscard = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

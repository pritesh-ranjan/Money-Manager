package com.moneymanager.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.moneymanager.app.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      Text("Money Manager Ready")
    }
  }

  @Test
  fun placeholder_exists() {
    composeTestRule.onNodeWithText("Money Manager Ready").assertExists()
  }
}

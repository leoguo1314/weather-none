package com.skypulse.weather.ui.agent

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.skypulse.weather.agent.AgentModelConfig
import com.skypulse.weather.agent.AgentModelSettings
import com.skypulse.weather.agent.AgentProviderConfig
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ModelSettingsDialogTest {
    @get:Rule val compose = createComposeRule()

    private val settings = AgentModelSettings(
        providers = listOf(
            AgentProviderConfig("first", name = "厂商一", baseUrl = "https://first.example.com/v1", models = listOf("model-a", "model-b")),
            AgentProviderConfig("second", name = "厂商二", baseUrl = "https://second.example.com/v1", models = listOf("model-c"))
        ),
        activeProviderId = "first",
        activeModel = "model-a",
        externalModelEnabled = true
    )

    private fun show(onSave: (AgentModelSettings) -> Unit = {}, onTest: (AgentModelConfig) -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                ModelSettingsDialog(settings, true, false, null, onDismiss, onSave, onTest)
            }
        }
    }

    @Test
    fun vendorExpandsModelsAndSavesSelectedModelWithoutLosingOtherVendors() {
        var saved: AgentModelSettings? = null
        var tested: AgentModelConfig? = null
        show(onSave = { saved = it }, onTest = { tested = it })
        compose.onNodeWithTag("provider-second").performScrollTo().performClick()
        compose.onNodeWithTag("model-first-model-a").assertDoesNotExist()
        compose.onNodeWithTag("model-second-model-c").performScrollTo().performClick()
        compose.onNodeWithTag("test-second-model-c").performClick()
        compose.runOnIdle {
            assertEquals("second", tested?.providerId)
            assertEquals("model-c", tested?.model)
        }
        screenshot("model-vendors.png")
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(2, saved?.providers?.size)
            assertEquals("second", saved?.activeProviderId)
            assertEquals("model-c", saved?.activeModel)
            assertTrue(saved!!.activeConfig.isUsable)
        }
    }

    @Test
    fun addingProviderPreservesExistingProviders() {
        var saved: AgentModelSettings? = null
        show(onSave = { saved = it })
        compose.onNodeWithText("添加模型厂商").performScrollTo().performClick()
        compose.onNodeWithText("厂商显示名称").performTextReplacement("新厂商")
        compose.onNodeWithText("保存厂商").performClick()
        compose.onNodeWithText("新厂商").assertExists()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(3, saved?.providers?.size)
            assertEquals("first", saved?.activeProviderId)
        }
    }

    @Test
    fun deletingSelectedProviderChoosesRemainingModelOnlyAfterSave() {
        var saved: AgentModelSettings? = null
        show(onSave = { saved = it })
        compose.onNodeWithTag("delete-first").performClick()
        compose.onNodeWithText("保存", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertEquals(1, saved?.providers?.size)
            assertEquals("second", saved?.activeProviderId)
            assertEquals("model-c", saved?.activeModel)
        }
    }

    @Test
    fun cancelDoesNotSaveDraftDeletion() {
        var saved = false
        var dismissed = false
        show(onSave = { saved = true }, onDismiss = { dismissed = true })
        compose.onNodeWithTag("delete-first").performClick()
        compose.onNodeWithText("取消", useUnmergedTree = true).performClick()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertFalse(saved)
            assertEquals(2, settings.providers.size)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "validation").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}

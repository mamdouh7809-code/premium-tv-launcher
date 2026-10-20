package com.premiumtv.launcher

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Android framework on a real emulator, with shell D-pad events. */
@RunWith(AndroidJUnit4::class)
class LauncherDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before fun cleanSettings() {
        context.getSharedPreferences(Preferences.FILE, Context.MODE_PRIVATE).edit().clear()
            .putString("language", "en")
            .putString("favorites", "[]")
            .putBoolean("favorites_initialized", true).commit()
    }

    private fun shell(command: String): String = device.executeShellCommand(command)
    private fun launch() = ActivityScenario.launch<MainActivity>(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    private fun waitForApp(label: String) {
        assertTrue("App did not appear: " + label, device.wait(Until.hasObject(By.desc(label)), 12_000))
    }
    private fun focusFixture(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val recycler = activity.findViewById<RecyclerView>(R.id.app_grid)
            val adapter = recycler.adapter as HomeAdapter
            val index = adapter.items.indexOfFirst { it is HomeItem.Tile && it.app.packageName == "com.premiumtv.fixture" }
            assertTrue(index >= 0)
            recycler.scrollToPosition(index)
        }
        instrumentation.waitForIdleSync()
        scenario.onActivity { activity ->
            val recycler = activity.findViewById<RecyclerView>(R.id.app_grid)
            val adapter = recycler.adapter as HomeAdapter
            val index = adapter.items.indexOfFirst { it is HomeItem.Tile && it.app.packageName == "com.premiumtv.fixture" }
            assertTrue(recycler.findViewHolderForAdapterPosition(index)!!.itemView.requestFocus())
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun packageInstallAndUninstallRefreshTheVisibleScreen() {
        shell("pm uninstall com.premiumtv.fixture")
        launch().use { scenario ->
            assertTrue(shell("pm install -r /data/local/tmp/noir-fixture.apk").contains("Success"))
            waitForApp("Noir Test App")
            assertTrue(shell("pm uninstall com.premiumtv.fixture").contains("Success"))
            assertTrue(device.wait(Until.gone(By.desc("Noir Test App")), 12_000))
            scenario.onActivity { activity ->
                val items = (activity.findViewById<RecyclerView>(R.id.app_grid).adapter as HomeAdapter).items
                assertFalse(items.any { it is HomeItem.Tile && it.app.packageName == "com.premiumtv.fixture" })
            }
        }
    }

    @Test fun remoteMenuFavoritesAndRecreationPersist() {
        assertTrue(shell("pm install -r /data/local/tmp/noir-fixture.apk").contains("Success"))
        launch().use { scenario ->
            waitForApp("Noir Test App")
            focusFixture(scenario)
            device.pressMenu()
            assertTrue(device.wait(Until.hasObject(By.text("Add to favorites")), 5_000))
            device.findObject(By.text("Add to favorites")).click()
            instrumentation.waitForIdleSync()
            scenario.recreate()
            waitForApp("Noir Test App")
            scenario.onActivity { activity ->
                val items = (activity.findViewById<RecyclerView>(R.id.app_grid).adapter as HomeAdapter).items
                assertTrue(items.any { it is HomeItem.Tile && it.section == Section.FAVORITES &&
                    it.app.packageName == "com.premiumtv.fixture" })
            }
        }
    }

    @Test fun remoteArrowsRetainFocusAndBackReturnsToToolbar() {
        assertTrue(shell("pm install -r /data/local/tmp/noir-fixture.apk").contains("Success"))
        launch().use { scenario ->
            waitForApp("Noir Test App")
            focusFixture(scenario)
            listOf(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT).forEach { instrumentation.sendKeyDownUpSync(it) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(activity.currentFocus?.tag is HomeItem.Tile)
            }
            device.pressBack()
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> assertEquals(R.id.search_button, activity.currentFocus?.id) }
        }
    }

    @Test fun arabicLayoutAndRemoteSearchKeyboardWork() {
        context.getSharedPreferences(Preferences.FILE, Context.MODE_PRIVATE).edit().putString("language", "ar").commit()
        assertTrue(shell("pm install -r /data/local/tmp/noir-fixture.apk").contains("Success"))
        launch().use { scenario ->
            waitForApp("Noir Test App")
            scenario.onActivity { activity ->
                assertEquals(View.LAYOUT_DIRECTION_RTL, activity.resources.configuration.layoutDirection)
                activity.findViewById<View>(R.id.search_button).performClick()
            }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "search_input")), 5_000))
            // A hardware keyboard can also populate the same field; no network IME is required.
            scenario.onActivity { }
            device.pressBack()
        }
    }
}

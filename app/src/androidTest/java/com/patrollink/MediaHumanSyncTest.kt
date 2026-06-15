package com.patrollink

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaHumanSyncTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val packageName = "com.patrollink"

    @Test
    fun tapMediaDeviceSyncToPhone() {
        val context = instrumentation.targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        assertNotNull("PatrolLink launch intent missing", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(launchIntent)

        waitForApp()
        loginIfNeeded()
        clickAny(By.text("媒体"), By.desc("媒体"))
        waitForAnyText("图库", "手机端", "设备端")

        clickAny(By.text("设备端"))
        waitForAnyText("设备文件", "同步到手机")

        clickAny(By.text("同步到手机"))
        waitForAnyText("已同步", "无需重复同步", "设备文件同步失败", "媒体文件下载失败", timeoutMillis = 180_000)
    }

    private fun loginIfNeeded() {
        if (device.wait(Until.hasObject(By.textContains("安全登录")), 3_000)) {
            val args = InstrumentationRegistry.getArguments()
            val badge = args.getString("patrol.badge") ?: "POLICE_9527"
            val password = args.getString("patrol.password") ?: "123456"
            setText(By.textContains("请输入警员编号或手机号"), badge)
            setText(By.textContains("请输入登录密码"), password)
            device.findObject(By.textContains("我已阅读"))?.click()
            clickAny(By.text("安全登录"), By.textContains("安全登录"))
            waitForAnyText("ForceLink", "媒体", "设备", "图库", timeoutMillis = 30_000)
        }
    }

    private fun setText(selector: BySelector, value: String) {
        val field = device.wait(Until.findObject(selector), 8_000)
        assertNotNull("Expected input field $selector", field)
        field!!.click()
        field.clear()
        field.text = value
        device.waitForIdle(500)
    }

    private fun waitForApp() {
        device.wait(Until.hasObject(By.pkg(packageName)), 15_000)
    }

    private fun clickAny(vararg selectors: BySelector) {
        val target = selectors.firstNotNullOfOrNull { selector ->
            device.wait(Until.findObject(selector), 8_000)
        }
        assertNotNull("Expected one of ${selectors.joinToString()}", target)
        target!!.click()
        device.waitForIdle(1_000)
    }

    private fun waitForAnyText(vararg texts: String, timeoutMillis: Long = 30_000): UiObject2 {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            texts.firstNotNullOfOrNull { text ->
                device.findObject(By.textContains(text))
            }?.let { return it }
            Thread.sleep(500)
        }
        throw AssertionError("Timed out waiting for one of: ${texts.joinToString()}")
    }
}

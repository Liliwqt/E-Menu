package com.example.androidkiosk.ui.admin
import org.junit.Assert.*
import org.junit.Test
class RecordsBridgeTest {
    @Test fun opensOnlyValidBranchRecordRoutes() {
        val opened = mutableListOf<String>()
        val bridge = DeviceWebBridge(onEnterMenuMode = { _, _ -> }, onOpenRecords = { opened.add(it) })
        for (invalid in listOf("https://evil.test", "../outside", "branch-one?token=secret", "branch-one/other", "")) bridge.openRecordsInBrowser(invalid)
        assertTrue(opened.isEmpty())
        bridge.openRecordsInBrowser("branch-example")
        assertEquals(listOf("branch-example"), opened)
    }
}

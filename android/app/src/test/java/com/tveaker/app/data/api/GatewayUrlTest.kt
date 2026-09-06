package com.tveaker.app.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayUrlTest {
    @Test
    fun physicalPhonesStartUnconfiguredInsteadOfUsingTheOldLanIp() {
        assertEquals(GatewayUrl.UNCONFIGURED_BASE_URL, GatewayUrl.defaultForDevice(isEmulator = false))
        assertFalse(GatewayUrl.isConfigured(GatewayUrl.defaultForDevice(isEmulator = false)))
        assertTrue(GatewayUrl.isLegacyLanUrl("http://192.168.1.33:8000/"))
        assertTrue(GatewayUrl.isLegacyLanUrl("http://192.168.0.2:8000/"))
        assertFalse(GatewayUrl.isConfigured("http://192.168.0.2:8000/"))
    }

    @Test
    fun emulatorRetainsItsExplicitLocalDevelopmentGateway() {
        assertEquals("http://10.0.2.2:8000/", GatewayUrl.defaultForDevice(isEmulator = true))
        assertTrue(GatewayUrl.isConfigured(GatewayUrl.defaultForDevice(isEmulator = true)))
    }
}

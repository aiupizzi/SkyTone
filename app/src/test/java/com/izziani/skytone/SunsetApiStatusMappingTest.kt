package com.izziani.skytone

import org.junit.Assert.assertEquals
import org.junit.Test

class SunsetApiStatusMappingTest {

    @Test
    fun mapSunsetStatusToMessage_returnsGuidanceForInvalidRequest() {
        val result = mapSunsetStatusToMessage("INVALID_REQUEST")

        assertEquals("Invalid location request. Please refresh and try again.", result)
    }

    @Test
    fun mapSunsetStatusToMessage_handlesCaseInsensitiveServiceUnavailableStatus() {
        val result = mapSunsetStatusToMessage("unknown_error")

        assertEquals("Sunset service is temporarily unavailable. Please try again later.", result)
    }

    @Test
    fun mapSunsetStatusToMessage_returnsFallbackForUnexpectedStatus() {
        val result = mapSunsetStatusToMessage("SOMETHING_ELSE")

        assertEquals("Unable to retrieve sunset data at the moment. Please try again later.", result)
    }
}

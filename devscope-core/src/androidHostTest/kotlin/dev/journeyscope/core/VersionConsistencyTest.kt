package dev.journeyscope.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class VersionConsistencyTest {
    @Test
    fun sdkVersionMatchesGradleVersionName() {
        val properties =
            generateSequence(File("").absoluteFile) { it.parentFile }
                .map { File(it, "gradle.properties") }
                .first { it.exists() && it.readText().contains("VERSION_NAME=") }
        val declared = properties.readLines().first { it.startsWith("VERSION_NAME=") }.substringAfter('=')
        assertEquals(declared, JourneyScope.VERSION)
    }
}

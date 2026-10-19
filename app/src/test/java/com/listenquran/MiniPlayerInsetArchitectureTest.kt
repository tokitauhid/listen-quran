package com.listenquran

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MiniPlayerInsetArchitectureTest {

    private val screensDir = File("src/main/java/com/listenquran/ui/screens")

    @Test
    fun testNoScreenContainsMiniPlayerInvocation() {
        assertTrue("Screens dir exists", screensDir.exists())
        val violations = mutableListOf<String>()
        val miniPlayerRegex = Regex("""\bMiniPlayer\s*\(""")

        screensDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                            if (miniPlayerRegex.containsMatchIn(line)) {
                                violations.add("${file.name}:${index + 1} -> $trimmed")
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Individual screens must not render MiniPlayer directly. Found:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testNoHardcodedBottomPaddingInScreens() {
        val violations = mutableListOf<String>()
        // Catch hardcoded estimates like 'bottom = 90.dp', 'bottom = 120.dp', 'bottom = 80.dp'
        val hardcodedRegex = Regex("""bottom\s*=\s*(?:80|90|100|120)\.dp""")

        screensDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        val trimmed = line.trim()
                        if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                            if (hardcodedRegex.containsMatchIn(line)) {
                                violations.add("${file.name}:${index + 1} -> $trimmed")
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Screens should not use hardcoded bottom dp values (e.g. 90.dp, 120.dp). Use LocalMiniPlayerInset.current instead:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun testAllScreensConsumeLocalMiniPlayerInset() {
        val requiredScreenFiles = listOf(
            "HomeScreen.kt",
            "SearchScreen.kt",
            "RecitersScreen.kt",
            "LibraryScreen.kt",
            "SettingsScreen.kt",
            "SurahsScreen.kt",
            "RadioScreen.kt",
            "KhatamScreen.kt",
            "PlaylistDetailScreen.kt",
            "ReciterProfileScreen.kt",
        )

        requiredScreenFiles.forEach { filename ->
            val file = screensDir.walkTopDown().firstOrNull { it.name == filename }
            assertTrue("Screen file $filename must exist", file != null && file.exists())
            val content = file!!.readText()
            assertTrue(
                "Screen $filename must use LocalMiniPlayerInset to adjust its bottom padding",
                content.contains("LocalMiniPlayerInset")
            )
        }
    }
}

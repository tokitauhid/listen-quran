package com.listenquran

import com.listenquran.ui.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NavigationArchitectureTest {

    @Test
    fun testAllTopLevelDestinationsBelongToKnownGraphs() {
        val validGraphs = setOf("home_graph", "reciters_graph", "library_graph")
        TopLevelDestination.entries.forEach { destination ->
            assertTrue(
                "Destination ${destination.name} graphRoute '${destination.graphRoute}' must be one of $validGraphs",
                destination.graphRoute in validGraphs
            )
            assertFalse(
                "Destination ${destination.name} route must not be empty",
                destination.route.isBlank()
            )
        }
    }

    @Test
    fun testBottomNavDestinations() {
        val bottomTabs = TopLevelDestination.bottomNavDestinations
        assertEquals(3, bottomTabs.size)
        assertEquals(TopLevelDestination.HOME, bottomTabs[0])
        assertEquals(TopLevelDestination.RECITERS, bottomTabs[1])
        assertEquals(TopLevelDestination.LIBRARY, bottomTabs[2])
    }

    @Test
    fun testFromRouteResolution() {
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.fromRoute("home"))
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.fromRoute("settings"))
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.fromRoute("search"))
        assertEquals(TopLevelDestination.HOME, TopLevelDestination.fromRoute("surahs"))
        assertEquals(TopLevelDestination.RECITERS, TopLevelDestination.fromRoute("reciters"))
        assertEquals(TopLevelDestination.LIBRARY, TopLevelDestination.fromRoute("library"))
        assertEquals(TopLevelDestination.LIBRARY, TopLevelDestination.fromRoute("playlist_detail"))
    }

    @Test
    fun testNoRawNavControllerNavigateOutsideHelpers() {
        val mainSrcDir = File("src/main/java/com/listenquran")
        assertTrue("Source directory must exist: ${mainSrcDir.absolutePath}", mainSrcDir.exists())

        val violations = mutableListOf<String>()
        val rawNavigateRegex = Regex("""navController\s*\.\s*navigate\s*\(""")

        mainSrcDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                // TopLevelDestination.kt is the dedicated helper location where the raw call is encapsulated
                if (file.name != "TopLevelDestination.kt") {
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            // Ignore comments
                            val trimmed = line.trim()
                            if (!trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")) {
                                if (rawNavigateRegex.containsMatchIn(line)) {
                                    violations.add("${file.name}:${index + 1} -> $trimmed")
                                }
                            }
                        }
                    }
                }
            }

        assertTrue(
            "Found raw navController.navigate() calls outside TopLevelDestination helpers:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}

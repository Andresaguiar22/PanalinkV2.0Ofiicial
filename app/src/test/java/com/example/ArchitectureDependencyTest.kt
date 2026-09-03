package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architecture guard-rails for the Panalink codebase.
 *
 * Goal: keep the layering sane while the legacy monoliths (MainActivity,
 * StatesRepository, ChatViewModel/ChatScreen, InicioTabContent) are split
 * incrementally. Every rule ships with an explicit allowlist of the CURRENT
 * violations (measured 2026-08-28) so the suite is green from day one
 * and each refactor block is expected to REMOVE entries from these lists.
 * Adding NEW violations fails the build immediately.
 *
 * Targeted layers (rule of the refactor):
 *   UI / Features
 *      ↓
 *   Domain
 *      ↓
 *   Data
 *      ↓
 *   Infrastructure (Supabase, B2, VCDN, Room, LiveKit, workers)
 *
 * Never allowed:
 *   Data ──→ UI          ❌
 *   Domain ──→ Android    ❌
 *   UI ──→ Supabase directly ❌   (except the allowlisted migration debt)
 *   Repository ──→ Composable  ❌
 */
class ArchitectureDependencyTest {

    private val mainSrc = File("app/src/main/java/com/example")

    private fun kotlinFilesUnder(rel: String): List<File> =
        File(mainSrc, rel).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun packageOf(file: File, root: String): String {
        val relPath = file.path.substringAfter(root).trimStart('/')
        return relPath.substringBeforeLast("/").replace('/', '.')
    }

    /** Direct imports of `com.example.<...>` from a set of files. */
    private fun ownPackageImports(file: File): List<String> {
        val imports = mutableListOf<String>()
        file.readLines().forEach { line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("import ") && trimmed.contains("com.example.")) {
                val imported = trimmed.removePrefix("import ").substringBefore(" ")?.substringBefore(";")
                if (imported != null) imports.add(imported)
            }
        }
        return imports
    }

    private fun directAndroidImports(file: File): List<String> =
        file.readLines().mapNotNull { line ->
            val t = line.trimStart()
            if (t.startsWith("import android.")) t.removePrefix("import ") else null
        }

    private fun assertNoUnexpected(ruleName: String, offenderRel: String, offenders: List<String>, allowlist: Set<String>) {
        val newOnes = offenders.filterNot { allowlist.contains(it) }.toSet()
        assertTrue(
            "[$ruleName] NEW dependency violations (remove them or, if it is acknowledged migration debt, add the file to the allowlist):\n" +
                newOnes.sorted().joinToString("\n"),
            newOnes.isEmpty()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rule 1 — `ui/` must not import `data.supabase.*` directly (UI → infra).
    // Currently 32 files do; the migration debt is allowlisted and every refactor
    // block (Feature extraction, repository moves) is expected to shrink this list.
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun uiMustNotImportSupabaseDataLayer() {
        val offenders = mutableListOf<String>()
        kotlinFilesUnder("ui").forEach { file ->
            val imports = ownPackageImports(file)
            val bad = imports.filter { it.startsWith("com.example.data.supabase.") || it == "com.example.data.supabase" }
            if (bad.isNotEmpty()) offenders.add(packageOf(file, "ui") + "/" + file.name)
        }
        val knownMigrationDebt = setOf(
            "ui/components/chat/bubble/MessageBubbleEngine.kt",
            "ui/components/chat/ChannelCreationDialog.kt",
            "ui/components/chat/ChannelPostCard.kt",
            "ui/components/chat/list/ChatPreviewCard.kt",
            "ui/screen/UserProfileScreen.kt",
            "ui/screen/ContactsTabContent.kt",
            "ui/screen/ProfileScreen.kt",
            "ui/screen/FavoritesScreen.kt",
            "ui/screen/ChatsListNavIcons.kt",
            "ui/screen/InicioTabContent.kt",
            "ui/screen/CleanStoryEditorScreen.kt",
            "ui/screen/EstadosTabContent.kt",
            "ui/screen/ViewStateScreen.kt",
            "ui/screen/PlusMenuComponents.kt",
            "ui/screen/PendingUploadsComponents.kt",
            "ui/screen/ChatsListScreen.kt",
            "ui/screen/LlamadasTabContent.kt",
            "ui/screen/ChatsTabContent.kt",
            "ui/screen/TikTokVideoFeedScreen.kt",
                                                            "ui/settings/providers/DashboardSummaryProvider.kt",
            "ui/settings/viewmodel/DashboardViewModel.kt",
            "ui/settings/viewmodel/CustomizationViewModel.kt",
            "ui/settings/viewmodel/ChatsSettingsViewModel.kt",
            "ui/profile/components/ReelsGrid.kt",
                        "ui/viewmodel/SocialViewModel.kt",
            "ui/viewmodel/NotificationsViewModel.kt",
            "ui/viewmodel/ProfileViewModel.kt",
            "ui/viewmodel/FeedViewModel.kt",
            "ui/viewmodel/PendingUploadsViewModel.kt",
            "ui/viewmodel/AuthViewModel.kt",
            "ui/viewmodel/StatesViewModel.kt",
            "ui/viewmodel/onboarding/OnboardingViewModel.kt"
        )
        assertNoUnexpected("ui → data.supabase", "ui/", offenders, knownMigrationDebt)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rule 2 — `di/` must not import `ui.*` (DI wiring is infrastructure; it must
    // depend on data/domain/features, never on the UI layer).
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun diMustNotImportUi() {
        val offenders = mutableListOf<String>()
        kotlinFilesUnder("di").forEach { file ->
            val imports = ownPackageImports(file)
            val bad = imports.filter { it.startsWith("com.example.ui.") || it == "com.example.ui" }
            if (bad.isNotEmpty()) offenders.add(file.name + ": " + bad.joinToString(", "))
        }
        assertNoUnexpected("di → ui", "di/", offenders, emptySet())
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rule 3 — `domain/` must not import `android.*` (pure Kotlin domain).
    // 4 files still draw android.util.Log / Parcelable etc; they become pure as the
    // refactor extracts the platform details into data/infrastructure.
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun domainMustNotImportAndroid() {
        val offenders = mutableListOf<String>()
        kotlinFilesUnder("domain").forEach { file ->
            val bad = directAndroidImports(file)
            if (bad.isNotEmpty()) offenders.add(file.name + ": " + bad.joinToString(", "))
        }
        val knownMigrationDebt = setOf(
            "UploadMediaUseCase.kt",
            "UpdateProfileUseCase.kt",
            "DeleteMessageUseCase.kt",
            "SendMessageUseCase.kt"
        )
        assertNoUnexpected("domain → android", "domain/", offenders, knownMigrationDebt)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rule 4 — `data/` must never import `ui.*`. Zero tolerance (no allowlist).
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun dataMustNeverImportUi() {
        val offenders = mutableListOf<String>()
        val roots = listOf(
            "data",
            "feature/auth/data",
            "feature/settings/data",
            "features/stickers/data"
        )
        roots.forEach { root ->
            kotlinFilesUnder(root).forEach { file ->
                val imports = ownPackageImports(file)
                val bad = imports.filter { it.startsWith("com.example.ui.") || it == "com.example.ui" }
                if (bad.isNotEmpty()) offenders.add(root + "/" + file.name + ": " + bad.joinToString(", "))
            }
        }
        assertTrue(
            "[data → ui] Violation — data layer must never depend on ui.\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Rule 5 — Repositories must not import androidx.compose.* (Repository → Composable).
    // NOTE: the 8 repositories that lived under ui/session and ui/settings/repository
    // weremoved to feature/settings/data and data/repository (2026-08-28),
    // which is outside the roots this rule scans. The rule stays as a guard for any
    // repository added in the near-trigonometric roots.
    // ─────────────────────────────────────────────────────────────────────────────
    @Test
    fun repositoriesMustNotImportCompose() {
        val offenders = mutableListOf<String>()
        val repoRoots = listOf(
            "data/repository",
            "feature/settings/data",
            "feature/auth/data",
            "features/stickers/data"
        )
        repoRoots.forEach { root ->
            kotlinFilesUnder(root).forEach { file ->
                val bad = file.readLines().filter { it.trimStart().startsWith("import androidx.compose.") }
                if (bad.isNotEmpty()) offenders.add(file.name + ": " + bad.joinToString(", "))
            }
        }
        assertNoUnexpected("repository → compose", "repositories", offenders, emptySet())
    }
}
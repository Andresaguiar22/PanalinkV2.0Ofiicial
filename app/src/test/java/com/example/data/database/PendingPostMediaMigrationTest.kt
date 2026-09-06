package com.example.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingPostMediaMigrationTest {

    private val TEST_DB = "migration-post-media-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PanalinkDatabase::class.java.canonicalName ?: PanalinkDatabase::class.java.name,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration46To47CreatesMediaRowsWithoutDataLoss() {
        var db = helper.createDatabase(TEST_DB, 46)
        db.execSQL("""
            INSERT INTO pending_posts (id, userId, content, type, mediaUrisJson, privacy, status, createdAt, progress)
            VALUES ('pp-legacy', 'u1', 'Hello', 'ALBUM', '["content://media/a", "content://media/b"]', 'PUBLIC', 'pending', 123456, 0.0)
        """.trimIndent())
        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 47, true, PanalinkDatabase.MIGRATION_46_47)

        // El post original sigue presente (no destructivo).
        val postCursor = db.query("SELECT mediaUrisJson FROM pending_posts WHERE id = 'pp-legacy'")
        assertTrue(postCursor.moveToFirst())
        assertEquals("""["content://media/a", "content://media/b"]""", postCursor.getString(0))
        postCursor.close()

        // Se crearon tantas filas PendingPostMedia como URIs.

        val count = db.query("SELECT COUNT(*) FROM pending_post_media WHERE postId = 'pp-legacy'")
        assertTrue(count.moveToFirst())
        assertEquals(2, count.getInt(0))
        count.close()

        // Cada fila conserva indice estable, URI local y estado PENDING..
        val rows = db.query("SELECT mediaIndex, localUri, status FROM pending_post_media WHERE postId = 'pp-legacy' ORDER BY mediaIndex ASC")
        var seen0 = false
        var seen1 = false
        while (rows.moveToNext()) {
            val index = rows.getInt(0)
            val uri = rows.getString(1)
            val status = rows.getString(2)
            if (index == 0 && uri == "content://media/a") {
                seen0 = true
                assertEquals("PENDING", status)
            }
            if (index == 1 && uri == "content://media/b") {
                seen1 = true
                assertEquals("PENDING", status)
            }
        }
        rows.close()
        assertTrue(seen0)
        assertTrue(seen1)
    }
}
package org.jellyfin.mobile.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class JellyfinDatabaseMigrationTest {

    /** How the app opens its database (no SQLite driver). */
    @get:Rule
    val appHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), JellyfinDatabase::class.java)

    /** Room's newer SQLite-driver path, which calls a different migration hook. */
    @get:Rule
    val driverHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File.createTempFile("migration-test", ".db").apply { delete() },
        driver = AndroidSQLiteDriver(),
        databaseClass = JellyfinDatabase::class,
    )

    private val signedInWithADownloadedBook = listOf(
        "INSERT INTO Server (id, hostname, last_used_timestamp) VALUES (1, 'http://vault', 0)",
        "INSERT INTO User (id, server_id, user_id, access_token, last_login_timestamp) VALUES (1, 1, 'u', 't', 0)",
        "INSERT INTO hidden_item (itemId) VALUES ('h1')",
        "INSERT INTO Download (item_id, media_source) VALUES ('t1', '{}')",
        "INSERT INTO download_record (itemId, title, filePath, fileSizeBytes, downloadedAt, mediaType) " +
            "VALUES ('t1', 'Chapter', '/Downloads/t1', 10, 0, 'AUDIO')",
    )

    /** After moving to shared downloads: still signed in, hidden items kept, old downloads forgotten. */
    private val expectedCounts = mapOf("User" to 1L, "hidden_item" to 1L, "Download" to 0L, "download_group" to 0L)

    @Test
    fun `moving to shared downloads forgets old downloads but keeps the sign-in and hidden items`() {
        appHelper.createDatabase(DB, 7).use { db -> signedInWithADownloadedBook.forEach(db::execSQL) }

        appHelper.runMigrationsAndValidate(DB, 8, true).use { db ->
            expectedCounts.mapValues { (table, _) ->
                db.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst()
                    it.getLong(0)
                }
            } shouldBe expectedCounts
        }
    }

    @Test
    fun `the migration does the same on an SQLite driver`() {
        driverHelper.createDatabase(7).apply {
            signedInWithADownloadedBook.forEach(::execSQL)
            close()
        }

        driverHelper.runMigrationsAndValidate(8).apply {
            expectedCounts.mapValues { (table, _) -> count(table) } shouldBe expectedCounts
            close()
        }
    }

    @Test
    fun `books gain a series, empty for the books already there`() {
        appHelper.createDatabase(DB, 8).use { db ->
            db.execSQL(
                "INSERT INTO audiobook_entries (id, server_id, parent_id, title, author, image_item_id, image_tag, " +
                    "runtime_ticks, track_ids, is_bundle, date_added, last_played, played_percentage) " +
                    "VALUES ('B:dir:book', 1, NULL, 'Book', NULL, NULL, NULL, 100, 't1,t2', 1, '', '', 40)",
            )
        }

        appHelper.runMigrationsAndValidate(DB, 9, true).use { db ->
            db.query("SELECT title, series, series_index, series_key FROM audiobook_entries").use {
                it.moveToFirst()
                listOf(it.getString(0), it.getString(1), it.isNull(2), it.getString(3)) shouldBe listOf("Book", null, true, null)
            }
        }
    }

    @Test
    fun `bookmarks come in empty, the library kept`() {
        appHelper.createDatabase(DB, 9).use { db ->
            db.execSQL("INSERT INTO hidden_item (itemId) VALUES ('h1')")
        }

        appHelper.runMigrationsAndValidate(DB, 10, true).use { db ->
            listOf("hidden_item", "audiobook_bookmark").map { table ->
                db.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst()
                    it.getLong(0)
                }
            } shouldBe listOf(1L, 0L)
        }
    }

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use {
            it.step()
            it.getLong(0)
        }

    private companion object {
        const val DB = "migration-test"
    }
}

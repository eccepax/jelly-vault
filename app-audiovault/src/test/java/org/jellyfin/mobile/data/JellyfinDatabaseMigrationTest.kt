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

    private fun SQLiteConnection.count(table: String): Long =
        prepare("SELECT COUNT(*) FROM $table").use {
            it.step()
            it.getLong(0)
        }

    private companion object {
        const val DB = "migration-test"
    }
}

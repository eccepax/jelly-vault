package org.jellyfin.mobile.music.data

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
class TuneDatabaseMigrationTest {

    /** How the app opens its database (no SQLite driver). */
    @get:Rule
    val appHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TuneDatabase::class.java)

    /** Room's newer SQLite-driver path, which calls a different migration hook. */
    @get:Rule
    val driverHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = File.createTempFile("migration-test", ".db").apply { delete() },
        driver = AndroidSQLiteDriver(),
        databaseClass = TuneDatabase::class,
    )

    private val signedInWithADownloadedAlbum = listOf(
        "INSERT INTO Server (id, hostname, last_used_timestamp) VALUES (1, 'http://vault', 0)",
        "INSERT INTO User (id, server_id, user_id, access_token, last_login_timestamp) VALUES (1, 1, 'u', 't', 0)",
        "INSERT INTO download_collection (id, kind, title, artist, coverUrl, addedAt) " +
            "VALUES ('pb', 'ALBUM', 'The Purple Bird', null, null, 1)",
        "INSERT INTO download_track (id, title, artist, albumId, albumTitle, durationMs) " +
            "VALUES ('a', 'A', 'B', 'pb', 'The Purple Bird', 1000)",
        "INSERT INTO download_collection_track (collectionId, position, trackId) VALUES ('pb', 0, 'a')",
    )

    /** After moving to shared downloads: still signed in, old downloads forgotten, group tables empty. */
    private val expectedCounts = mapOf(
        "User" to 1L,
        "download_collection" to 0L,
        "download_track" to 0L,
        "download_group" to 0L,
        "download_group_item" to 0L,
    )

    @Test
    fun `moving to shared downloads forgets old downloads but keeps the sign-in`() {
        appHelper.createDatabase(DB, 4).use { db -> signedInWithADownloadedAlbum.forEach(db::execSQL) }

        appHelper.runMigrationsAndValidate(DB, 5, true).use { db ->
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
        driverHelper.createDatabase(4).apply {
            signedInWithADownloadedAlbum.forEach(::execSQL)
            close()
        }

        driverHelper.runMigrationsAndValidate(5).apply {
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

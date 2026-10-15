package org.jellyfin.mobile.books.data

import android.app.Application
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BookDatabase::class.java)

    @Test
    fun `moving to shared downloads keeps the sign-in, reading progress and bookmarks`() {
        helper.createDatabase(DB, 4).use { db ->
            db.execSQL("INSERT INTO Server (id, hostname, last_used_timestamp) VALUES (1, 'http://vault', 0)")
            db.execSQL(
                "INSERT INTO User (id, server_id, user_id, access_token, last_login_timestamp) VALUES (1, 1, 'u', 't', 0)",
            )
            db.execSQL(
                "INSERT INTO book_progress (bookItemId, pageNumber, totalPages, lastUpdated) VALUES ('b1', 12, 300, 0)",
            )
            db.execSQL(
                "INSERT INTO book_bookmark (id, bookItemId, pageIndex, label, note, createdAt) VALUES (1, 'b1', 12, 'here', null, 0)",
            )
            db.execSQL(
                "INSERT INTO download_record (itemId, title, filePath, fileSizeBytes, downloadedAt, mediaType) " +
                    "VALUES ('b1', 'Book', '/books/b1', 10, 0, 'BOOK')",
            )
        }

        helper.runMigrationsAndValidate(DB, 5, true).use { db ->
            listOf("User", "book_progress", "book_bookmark", "download_group").associateWith { table ->
                db.query("SELECT COUNT(*) FROM $table").use {
                    it.moveToFirst()
                    it.getLong(0)
                }
            } shouldBe mapOf("User" to 1L, "book_progress" to 1L, "book_bookmark" to 1L, "download_group" to 0L)
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}

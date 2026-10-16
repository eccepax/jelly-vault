package org.jellyfin.mobile.books

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoTest {

    @Test
    fun `the logo is in the app's accent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val logo = ContextCompat.getDrawable(context, R.drawable.book_logo)!!
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        logo.setBounds(0, 0, SIZE, SIZE)
        logo.draw(Canvas(bitmap))

        // The book's cover, right of its bookmark
        bitmap.getPixel(64, 48) shouldBe context.getColor(R.color.vault_accent)
    }

    private companion object {
        const val SIZE = 96
    }
}

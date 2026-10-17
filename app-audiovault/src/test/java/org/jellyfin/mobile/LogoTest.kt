package org.jellyfin.mobile

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
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoTest {

    @Test
    fun `the logo is in the app's accent`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val logo = ContextCompat.getDrawable(context, R.drawable.app_logo)!!
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        logo.setBounds(0, 0, SIZE, SIZE)
        logo.draw(Canvas(bitmap))

        // The left ear cup of the headset
        bitmap.getPixel(24, 64) shouldBe context.getColor(CommonR.color.vault_accent)
    }

    private companion object {
        const val SIZE = 96
    }
}

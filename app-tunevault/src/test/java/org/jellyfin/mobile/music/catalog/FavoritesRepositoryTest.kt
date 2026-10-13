package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesRepositoryTest {

    private val store = FakeFavoritesStore()
    private val repository = FavoritesRepository(store, TestScope(UnconfinedTestDispatcher()))

    @Test
    fun `without a change the server's value stands`() {
        repository.isFavorite("a", serverValue = true) shouldBe true
        repository.isFavorite("b", serverValue = false) shouldBe false
    }

    @Test
    fun `a change shows at once and is saved`() {
        repository.setFavorite("a", true)

        repository.isFavorite("a", serverValue = false) shouldBe true
        store.writes shouldBe listOf("a=true")
    }

    @Test
    fun `changes are per item`() {
        repository.setFavorite("a", true)

        repository.isFavorite("b", serverValue = false) shouldBe false
    }

    @Test
    fun `a failed save puts the old value back`() {
        store.failure = IOException("offline")

        repository.setFavorite("a", true)

        repository.isFavorite("a", serverValue = false) shouldBe false
    }

    @Test
    fun `toggling back and forth saves each change in order`() {
        repository.setFavorite("a", true)
        repository.setFavorite("a", false)

        store.writes shouldBe listOf("a=true", "a=false")
        repository.isFavorite("a", serverValue = true) shouldBe false
    }

    @Test
    fun `changes are observable`() {
        repository.setFavorite("a", true)

        repository.overrides.value shouldBe mapOf("a" to true)
    }

    @Test
    fun `clearing forgets every change, so the next account sees only its own`() {
        repository.setFavorite("a", true)

        repository.clear()

        repository.isFavorite("a", serverValue = false) shouldBe false
    }
}

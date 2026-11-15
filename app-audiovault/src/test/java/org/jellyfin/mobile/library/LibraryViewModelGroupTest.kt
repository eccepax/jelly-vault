package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.Test
import java.util.UUID

/** [LibraryViewModel] grouping the whole library's books by author or by series, and opening a group. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelGroupTest : LibraryViewModelTestBase() {

    private fun book(name: String, author: String) = BaseItemDto(
        id = UUID.randomUUID(),
        name = name,
        type = BaseItemKind.AUDIO_BOOK,
        path = "/books/$name/$name.m4b",
        artists = listOf(author),
    )

    private fun wholeLibraryFromServer() {
        every { appPreferences.libraryGlobalFlattened } returns true
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        stubItems(
            listOf(
                book("Caste", "Isabel Wilkerson"),
                book("The Warmth of Other Suns", "Isabel Wilkerson"),
                book("A People's History", "Howard Zinn"),
            ),
        )
    }

    @Test
    fun `grouped by author, the library shows a group per author`() = runTest {
        wholeLibraryFromServer()
        every { appPreferences.libraryGroupBy } returns GroupBy.AUTHOR.name

        val viewModel = createViewModel()

        viewModel.itemNamesShouldBecome(listOf("Howard Zinn", "Isabel Wilkerson"))
        viewModel.groupBy.value shouldBe GroupBy.AUTHOR
    }

    @Test
    fun `choosing how to group is remembered and groups at once`() = runTest {
        wholeLibraryFromServer()
        val viewModel = createViewModel()
        viewModel.itemNamesShouldBecome(listOf("A People's History", "Caste", "The Warmth of Other Suns"))

        viewModel.setGroupBy(GroupBy.AUTHOR)

        viewModel.itemNamesShouldBecome(listOf("Howard Zinn", "Isabel Wilkerson"))
        verify { appPreferences.libraryGroupBy = GroupBy.AUTHOR.name }
    }

    @Test
    fun `opening a group shows its books under its name, and going up shows the groups again`() = runTest {
        wholeLibraryFromServer()
        every { appPreferences.libraryGroupBy } returns GroupBy.AUTHOR.name
        val viewModel = createViewModel()
        viewModel.itemNamesShouldBecome(listOf("Howard Zinn", "Isabel Wilkerson"))

        viewModel.openGroup("Isabel Wilkerson")

        viewModel.itemNamesShouldBecome(listOf("Caste", "The Warmth of Other Suns"))
        viewModel.title.value shouldBe "Isabel Wilkerson"
        viewModel.canGoUp.value shouldBe true

        viewModel.goUp() shouldBe true

        viewModel.itemNamesShouldBecome(listOf("Howard Zinn", "Isabel Wilkerson"))
        viewModel.canGoUp.value shouldBe false
    }

    @Test
    fun `searching finds books, not groups`() = runTest {
        wholeLibraryFromServer()
        every { appPreferences.libraryGroupBy } returns GroupBy.AUTHOR.name
        val viewModel = createViewModel()
        viewModel.itemNamesShouldBecome(listOf("Howard Zinn", "Isabel Wilkerson"))

        viewModel.setFilterQuery("Caste")

        viewModel.itemNamesShouldBecome(listOf("Caste"))
    }

    @Test
    fun `browsing by folder there's no grouping`() = runTest {
        every { appPreferences.libraryGroupBy } returns GroupBy.AUTHOR.name
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))

        val viewModel = createViewModel()

        viewModel.itemNamesShouldBecome(listOf("Books"))
        viewModel.canGroup.value shouldBe false
    }
}

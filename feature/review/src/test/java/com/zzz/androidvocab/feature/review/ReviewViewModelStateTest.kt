package com.zzz.androidvocab.feature.review

import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelStateTest {
    @After
    fun tearDown() = resetTestDispatcher()

    @Test
    fun initialStateIsLoadingUntilQueueArrives() =
        runTest {
            setTestDispatcher()
            val viewModel = createTestViewModel(reviewRepository = TestReviewRepository())

            assertEquals(true, viewModel.uiState.value.isLoading)

            val loaded =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading }
                }
            advanceUntilIdle()

            assertEquals(false, loaded.await().isLoading)
        }

    @Test
    fun dueItemsAreShownBeforeNewItems() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(TestFixtures.reviewQueueItem(cardId = "due-card", wordId = "due-word")),
                    newItems =
                        listOf(
                            TestFixtures.reviewQueueItem(cardId = "new-card", wordId = "new-word", isNew = true),
                        ),
                )
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)

            val state =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isLoading }
                }
            advanceUntilIdle()

            assertEquals(
                "due-card",
                state
                    .await()
                    .item
                    ?.card
                    ?.id,
            )
            assertEquals(2, viewModel.uiState.value.remainingCount)
        }

    @Test
    fun backIsHiddenWhenCurrentCardChanges() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.item != null }
            }.join()
            advanceUntilIdle()

            viewModel.showBack()
            assertEquals(true, viewModel.uiState.first { it.isBackVisible }.isBackVisible)

            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(TestFixtures.reviewQueueItem(cardId = "card-2", wordId = "word-2")),
                    newItems = emptyList(),
                )

            val stateAfterCardChange = viewModel.uiState.first { it.item?.card?.id == "card-2" }
            assertEquals(false, stateAfterCardChange.isBackVisible)
        }
}

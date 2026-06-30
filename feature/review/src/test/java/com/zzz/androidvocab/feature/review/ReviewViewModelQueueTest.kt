package com.zzz.androidvocab.feature.review

import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelQueueTest {
    @After
    fun tearDown() = resetTestDispatcher()

    @Test
    fun submittedCardIsHiddenWhileQueueStillContainsStaleEntry() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            val loaded =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.item != null }
                }
            runCurrent()
            loaded.await()

            viewModel.showBack()
            viewModel.submit(ReviewRating.Good)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(null, state.item)
            assertEquals(0, state.remainingCount)
            assertEquals(true, state.isAdvancingToNextCard)
            assertEquals(false, state.isBackVisible)
        }

    @Test
    fun nextCardAppearsEvenIfQueueRefreshStillContainsSubmittedCardFirst() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            viewModel.uiState.first { it.item != null }
            advanceUntilIdle()

            viewModel.showBack()
            viewModel.submit(ReviewRating.Good)
            advanceUntilIdle()

            reviewRepository.queue.value =
                TodayQueue(
                    dueItems =
                        listOf(
                            TestFixtures.reviewQueueItem(cardId = TestFixtures.CARD_ID, wordId = TestFixtures.WORD_ID),
                            TestFixtures.reviewQueueItem(cardId = "card-2", wordId = "word-2"),
                        ),
                    newItems = emptyList(),
                )

            val state = viewModel.uiState.first { it.item?.card?.id == "card-2" }
            assertEquals("card-2", state.item?.card?.id)
            assertEquals(1, state.remainingCount)
            assertEquals(false, state.isAdvancingToNextCard)
        }

    @Test
    fun completionStateWaitsUntilSubmittedCardActuallyLeavesQueue() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.collect {}
            }
            val loaded =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.item != null }
                }
            runCurrent()
            loaded.await()

            viewModel.showBack()
            viewModel.submit(ReviewRating.Good)
            runCurrent()

            assertEquals(true, viewModel.uiState.value.isAdvancingToNextCard)

            reviewRepository.queue.value = TodayQueue(dueItems = emptyList(), newItems = emptyList())
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(null, state.item)
            assertEquals(0, state.remainingCount)
            assertEquals(false, state.isAdvancingToNextCard)
        }
}

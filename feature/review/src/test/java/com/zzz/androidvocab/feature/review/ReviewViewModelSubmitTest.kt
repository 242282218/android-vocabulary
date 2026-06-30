package com.zzz.androidvocab.feature.review

import com.zzz.androidvocab.core.common.AppError
import com.zzz.androidvocab.core.common.AppException
import com.zzz.androidvocab.core.model.ReviewRating
import com.zzz.androidvocab.core.model.TodayQueue
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelSubmitTest {
    @After
    fun tearDown() = resetTestDispatcher()

    @Test
    fun submitIgnoresDuplicateClicksWhileFirstSubmitIsRunning() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.item != null }
            }.join()
            advanceUntilIdle()

            viewModel.submit(ReviewRating.Good)
            viewModel.submit(ReviewRating.Again)
            advanceUntilIdle()

            assertEquals(listOf(ReviewRating.Good), reviewRepository.commands.map { it.rating })
        }

    @Test
    fun submitUsesElapsedTimeAfterAnswerIsShown() =
        runTest {
            setTestDispatcher()
            val clock = TestClock(Instant.parse("2026-05-16T08:00:00Z"))
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository, clock = clock)
            launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.uiState.first { it.item != null }
            }.join()
            advanceUntilIdle()

            viewModel.showBack()
            clock.instant = Instant.parse("2026-05-16T08:00:03.500Z")
            viewModel.submit(ReviewRating.Easy)
            advanceUntilIdle()

            assertEquals(3_500L, reviewRepository.commands.single().durationMs)
            assertEquals(
                Instant.parse("2026-05-15T08:00:00Z"),
                reviewRepository.commands.single().expectedLastReviewAt,
            )
            assertEquals(1, reviewRepository.commands.single().expectedReviewCount)
        }

    @Test
    fun submittedCardCannotBeSubmittedAgainBeforeQueueRefresh() =
        runTest {
            setTestDispatcher()
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository)
            viewModel.uiState.first { it.item != null }
            advanceUntilIdle()

            viewModel.showBack()
            viewModel.submit(ReviewRating.Good)
            advanceUntilIdle()

            viewModel.showBack()
            viewModel.submit(ReviewRating.Again)
            advanceUntilIdle()

            assertEquals(listOf(ReviewRating.Good), reviewRepository.commands.map { it.rating })
            assertEquals(false, viewModel.uiState.value.isBackVisible)
        }

    @Test
    fun submitFailureKeepsAnswerVisibleAndAllowsRetry() =
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

            val visibleBack =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.isBackVisible }
                }
            viewModel.showBack()
            runCurrent()
            assertEquals(true, visibleBack.await().isBackVisible)
            reviewRepository.nextSubmitError = AppException(AppError.DatabaseWriteFailed("disk full"))
            val failed =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.errorMessage != null }
                }
            viewModel.submit(ReviewRating.Good)
            runCurrent()

            val failedState = failed.await()
            assertEquals(true, failedState.isBackVisible)
            assertEquals(false, failedState.isSubmitting)
            assertEquals("学习数据保存失败：disk full", failedState.errorMessage)
            assertEquals(emptyList<ReviewRating>(), reviewRepository.commands.map { it.rating })

            reviewRepository.nextSubmitBlocker = CompletableDeferred()
            val retrying =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { it.isSubmitting && it.errorMessage == null && it.isBackVisible }
                }
            viewModel.submit(ReviewRating.Good)
            runCurrent()

            val retryingState = retrying.await()
            assertEquals(null, retryingState.errorMessage)
            assertEquals(true, retryingState.isSubmitting)
            assertEquals(true, retryingState.isBackVisible)
            assertEquals(listOf(ReviewRating.Good), reviewRepository.commands.map { it.rating })

            val retried =
                async(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.uiState.first { !it.isSubmitting && it.errorMessage == null && !it.isBackVisible }
                }
            reviewRepository.nextSubmitBlocker!!.complete(Unit)
            runCurrent()

            val retriedState = retried.await()
            assertEquals(false, retriedState.isBackVisible)
            assertEquals(null, retriedState.errorMessage)
            assertEquals(false, retriedState.isSubmitting)
        }

    @Test
    fun retryAfterSubmitFailureKeepsOriginalDecisionDuration() =
        runTest {
            setTestDispatcher()
            val clock = TestClock(Instant.parse("2026-05-16T08:00:00Z"))
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository, clock = clock)
            viewModel.uiState.first { it.item != null }
            advanceUntilIdle()

            viewModel.showBack()
            clock.instant = Instant.parse("2026-05-16T08:00:03.500Z")
            reviewRepository.nextSubmitError = AppException(AppError.DatabaseWriteFailed("disk full"))
            viewModel.submit(ReviewRating.Good)
            advanceUntilIdle()

            clock.instant = Instant.parse("2026-05-16T08:00:12.000Z")
            viewModel.submit(ReviewRating.Good)
            advanceUntilIdle()

            assertEquals(3_500L, reviewRepository.commands.single().durationMs)
        }

    @Test
    fun failedDurationDoesNotCarryToNextCard() =
        runTest {
            setTestDispatcher()
            val clock = TestClock(Instant.parse("2026-05-16T08:00:00Z"))
            val reviewRepository = TestReviewRepository()
            val viewModel = createTestViewModel(reviewRepository = reviewRepository, clock = clock)
            viewModel.uiState.first { it.item != null }
            advanceUntilIdle()

            viewModel.showBack()
            clock.instant = Instant.parse("2026-05-16T08:00:03.500Z")
            reviewRepository.nextSubmitError = AppException(AppError.DatabaseWriteFailed("disk full"))
            viewModel.submit(ReviewRating.Good)
            advanceUntilIdle()

            reviewRepository.queue.value =
                TodayQueue(
                    dueItems = listOf(TestFixtures.reviewQueueItem(cardId = "card-2", wordId = "word-2")),
                    newItems = emptyList(),
                )
            viewModel.uiState.first { it.item?.card?.id == "card-2" }
            clock.instant = Instant.parse("2026-05-16T08:00:12.000Z")
            viewModel.showBack()
            clock.instant = Instant.parse("2026-05-16T08:00:12.500Z")
            viewModel.submit(ReviewRating.Easy)
            advanceUntilIdle()

            assertEquals(500L, reviewRepository.commands.single().durationMs)
        }
}

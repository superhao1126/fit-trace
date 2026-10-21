package com.fittrace.feature.main

import androidx.lifecycle.viewModelScope
import com.fittrace.core.model.*
import java.time.*
import java.util.TimeZone
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `period failures are independent retries retain curve and range switches clear both`() =
        runTest(dispatcher) {
            var fail = false
            var requested = 0
            val repo =
                object : RecordsRepositoryFake() {
                    override suspend fun insights(
                        days: Int,
                        tzOffset: Int,
                    ): AppResult<PeriodInsights> {
                        requested = days
                        return if (fail) AppResult.Failure(AppError.Network)
                        else AppResult.Success(PeriodInsights(days, null, 1, 1, 0, 0))
                    }
                }
            val model = TrendsViewModel(repo)
            try {
                runCurrent()
                assertEquals(7, requested)
                assertNotNull(model.state.value.data)
                assertNull(model.state.value.insights!!.averageCalories)
                fail = true
                model.loadInsights()
                runCurrent()
                assertNotNull(model.state.value.insightsError)
                assertNotNull(model.state.value.insights)
                assertNotNull(model.state.value.data)
                model.load(30)
                assertNull(model.state.value.data)
                assertNull(model.state.value.insights)
                runCurrent()
                assertEquals(30, requested)
                assertNotNull(model.state.value.data)
                assertNull(model.state.value.insights)
            } finally {
                model.viewModelScope.cancel()
            }
        }

    @Test
    fun `month query spans local month and refresh retries first page instead of more`() =
        runTest(dispatcher) {
            val old = TimeZone.getDefault()
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            var range: Pair<String?, String?>? = null
            var fail = false
            var pageRequested = 0
            val body =
                BodyEntry("b", 70.2, null, null, null, null, "2026-10-19T08:00:00+08:00", null)
            val repo =
                object : RecordsRepositoryFake() {
                    override suspend fun bodies(
                        page: Int,
                        from: String?,
                        to: String?,
                    ): AppResult<RecordPage<BodyEntry>> {
                        pageRequested = page
                        range = from to to
                        return if (fail) AppResult.Failure(AppError.Network)
                        else AppResult.Success(RecordPage(listOf(body), 40))
                    }
                }
            val model = RecordsViewModel(repo)
            try {
                model.select(RecordKind.BODY, null, month = YearMonth.of(2026, 10))
                runCurrent()
                assertEquals("2026-10-01T00:00+08:00" to "2026-11-01T00:00+08:00", range)
                fail = true
                model.load()
                runCurrent()
                assertEquals(2, pageRequested)
                assertTrue(model.state.value.loadMoreError)
                assertEquals(1, model.state.value.items.size)
                model.refresh()
                runCurrent()
                assertEquals(1, pageRequested)
                assertFalse(model.state.value.loadMoreError)
                assertEquals(1, model.state.value.items.size)
                model.select(RecordKind.BODY, null, month = YearMonth.of(2026, 9))
                assertTrue(model.state.value.items.isEmpty())
                runCurrent()
                assertEquals("2026-09-01T00:00+08:00" to "2026-10-01T00:00+08:00", range)
            } finally {
                model.viewModelScope.cancel()
                TimeZone.setDefault(old)
            }
        }
}

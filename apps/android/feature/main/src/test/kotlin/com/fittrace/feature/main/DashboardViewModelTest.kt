package com.fittrace.feature.main

import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.DashboardRepository
import com.fittrace.core.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 16)
    private lateinit var model: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        if (::model.isInitialized) model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `dashboard sends east positive offsets in Shanghai UTC and Los Angeles`() =
        runTest(dispatcher) {
            for ((zone, expected) in
                listOf("Asia/Shanghai" to 480, "UTC" to 0, "America/Los_Angeles" to -420)) {
                var captured: Pair<String, Int>? = null
                model =
                    DashboardViewModel(
                        object : DashboardRepository {
                            override suspend fun overview(
                                date: String,
                                timezoneOffset: Int,
                            ): AppResult<Dashboard> {
                                captured = date to timezoneOffset
                                return AppResult.Success(dashboardFixture(date))
                            }
                        },
                        timezone = { ZoneId.of(zone) },
                        today = { today },
                    )
                runCurrent()
                assertEquals(zone, today.toString() to expected, captured)
                model.viewModelScope.cancel()
            }
        }

    @Test
    fun `failed refresh preserves previous data while changing dates clears it`() =
        runTest(dispatcher) {
            var fail = false
            model =
                DashboardViewModel(
                    object : DashboardRepository {
                        override suspend fun overview(
                            date: String,
                            timezoneOffset: Int,
                        ): AppResult<Dashboard> =
                            if (fail) AppResult.Failure(AppError.Network)
                            else AppResult.Success(dashboardFixture(date))
                    }
                ) {
                    today
                }
            runCurrent()
            val original = model.state.value.data
            fail = true
            model.refresh()
            runCurrent()
            assertSame(original, model.state.value.data)
            assertNotNull(model.state.value.error)
            model.selectDate(today.minusDays(1))
            assertNull(model.state.value.data)
            runCurrent()
            assertNull(model.state.value.data)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun `date switch cancels in flight request and future dates never load`() =
        runTest(dispatcher) {
            val dates = mutableListOf<String>()
            model =
                DashboardViewModel(
                    object : DashboardRepository {
                        override suspend fun overview(
                            date: String,
                            timezoneOffset: Int,
                        ): AppResult<Dashboard> {
                            dates += date
                            delay(100)
                            return AppResult.Success(dashboardFixture(date))
                        }
                    }
                ) {
                    today
                }
            runCurrent()
            model.selectDate(today.minusDays(1))
            runCurrent()
            model.selectDate(today.plusDays(1))
            advanceUntilIdle()
            assertEquals(today.minusDays(1).toString(), model.state.value.data?.date)
            assertEquals(listOf(today.toString(), today.minusDays(1).toString()), dates)
        }
}

internal fun dashboardFixture(
    date: String = "2026-10-16",
    records: List<DiaryRecord> = emptyList(),
) =
    Dashboard(
        date,
        null,
        null,
        0,
        0,
        0,
        0,
        records,
        DashboardTrend("2026-10-10", date, null, 0, 0, 0, 0),
    )

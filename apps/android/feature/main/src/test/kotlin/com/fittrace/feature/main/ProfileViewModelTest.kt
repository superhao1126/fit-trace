package com.fittrace.feature.main

import androidx.lifecycle.viewModelScope
import com.fittrace.core.domain.ProfileRepository
import com.fittrace.core.model.*
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

internal open class ProfileRepositoryFake : ProfileRepository {
    var user = profileFixture()
    var body: ProfileBody? = ProfileBody(70.2, "2026-10-16T07:40:00+08:00")
    var profileFailure: AppError? = null
    var bodyFailure: AppError? = null
    var saveFailure: AppError? = null
    var profileInput: ProfileUpdate? = null
    var goalInput: GoalUpdate? = null
    var saves = 0

    override suspend fun profile(): AppResult<UserProfile> =
        profileFailure?.let { AppResult.Failure(it) } ?: AppResult.Success(user)

    override suspend fun latestBody(): AppResult<ProfileBody?> =
        bodyFailure?.let { AppResult.Failure(it) } ?: AppResult.Success(body)

    override suspend fun updateProfile(input: ProfileUpdate): AppResult<UserProfile> {
        saves++
        profileInput = input
        delay(10)
        return saveFailure?.let { AppResult.Failure(it) }
            ?: AppResult.Success(
                user.copy(nickname = input.nickname, gender = input.gender).also { user = it }
            )
    }

    override suspend fun updateGoal(input: GoalUpdate): AppResult<UserProfile> {
        saves++
        goalInput = input
        delay(10)
        return saveFailure?.let { AppResult.Failure(it) }
            ?: AppResult.Success(
                user
                    .copy(
                        goal =
                            FitnessGoal(
                                input.type,
                                body?.weight ?: input.currentWeight!!,
                                input.targetWeight,
                                input.targetDate,
                                "2026-10-16T06:00:00Z",
                            )
                    )
                    .also { user = it }
            )
    }
}

internal fun profileFixture(
    goal: FitnessGoal? =
        FitnessGoal(GoalType.LOSE_FAT, 71.0, 68.0, "2026-12-01T15:59:59Z", "2026-10-01T00:00:00Z")
) =
    UserProfile(
        "u",
        "hello@example.com",
        "循形用户",
        null,
        UserGender.UNSPECIFIED,
        "2026-10-01T00:00:00Z",
        goal,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var fake: ProfileRepositoryFake
    private lateinit var model: ProfileViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        fake = ProfileRepositoryFake()
        model = ProfileViewModel(fake) { LocalDate.of(2026, 10, 16) }
    }

    @After
    fun cleanup() {
        model.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `profile errors retain data and failed weight never becomes empty account`() =
        runTest(dispatcher) {
            model.load()
            advanceUntilIdle()
            val original = model.state.value.profile
            fake.bodyFailure = AppError.Network
            model.load()
            advanceUntilIdle()
            assertSame(original, model.state.value.profile)
            assertNotNull(model.state.value.bodyError)
            fake.profileFailure = AppError.Network
            model.load()
            advanceUntilIdle()
            assertSame(original, model.state.value.profile)
            assertNotNull(model.state.value.error)
            fake.profileFailure = null
            fake.bodyFailure = null
            fake.body = null
            model.load()
            advanceUntilIdle()
            assertNull(model.state.value.error)
            assertNull(model.state.value.bodyError)
            assertNull(model.state.value.body)
        }

    @Test
    fun `profile validation prevents blank save and failed save retains draft`() =
        runTest(dispatcher) {
            model.load()
            advanceUntilIdle()
            model.beginProfile()
            model.nickname(" ")
            model.saveProfile {}
            assertEquals(0, fake.saves)
            model.nickname(" 新昵称 ")
            fake.saveFailure = AppError.Network
            var successes = 0
            model.saveProfile { successes++ }
            model.saveProfile { successes++ }
            advanceUntilIdle()
            assertEquals(1, fake.saves)
            assertEquals(0, successes)
            assertEquals(" 新昵称 ", model.state.value.nickname)
            assertNotNull(model.state.value.formError)
            fake.saveFailure = null
            model.saveProfile { successes++ }
            advanceUntilIdle()
            assertEquals(1, successes)
            assertEquals("新昵称", model.state.value.profile?.nickname)
        }

    @Test
    fun `goal rejects invalid target and past dates and submits new weight only after explicit edit`() =
        runTest(dispatcher) {
            model.load()
            advanceUntilIdle()
            model.beginGoal()
            model.targetWeight("80")
            model.saveGoal {}
            assertEquals(0, fake.saves)
            model.targetWeight("68.123")
            model.saveGoal {}
            assertEquals(0, fake.saves)
            model.targetWeight("68")
            model.targetDate("2026-10-15")
            model.saveGoal {}
            assertEquals(0, fake.saves)
            model.targetDate("2026-12-01")
            model.saveGoal {}
            advanceUntilIdle()
            assertNull(fake.goalInput?.currentWeight)
            assertEquals(68.0, fake.goalInput?.targetWeight)
            assertNotNull(fake.goalInput?.targetDate)
            model.beginGoal()
            model.editWeight()
            model.currentWeight("70.5")
            model.saveGoal {}
            advanceUntilIdle()
            assertEquals(70.5, fake.goalInput?.currentWeight)
        }

    @Test
    fun `goal with no body requires current weight and maintain has no percent`() =
        runTest(dispatcher) {
            fake.user = profileFixture(null)
            fake.body = null
            model.load()
            advanceUntilIdle()
            model.beginGoal()
            model.targetWeight("68")
            model.saveGoal {}
            assertEquals(0, fake.saves)
            model.currentWeight("70")
            model.saveGoal {}
            advanceUntilIdle()
            assertEquals(70.0, fake.goalInput?.currentWeight)
            assertEquals(27, profileFixture().goal?.progress(70.2))
            assertEquals(100, profileFixture().goal?.progress(67.0))
            assertEquals(0, profileFixture().goal?.progress(72.0))
            assertNull(
                FitnessGoal(GoalType.MAINTAIN, 70.0, 70.0, null, "2026-10-01T00:00:00Z")
                    .progress(70.0)
            )
        }
}

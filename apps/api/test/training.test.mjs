/* global structuredClone */
import 'reflect-metadata';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import dto from '../dist/training/training.dto.js';
import training from '../dist/training/training.service.js';
import workoutDto from '../dist/workouts/dto/create-workout.dto.js';
import updateWorkoutDto from '../dist/workouts/dto/update-workout.dto.js';
import { exerciseLibrary, trainingPresets } from '@fit-trace/shared';
test('expanded presets preserve IDs and every template passes server nested validation', async () => {
  assert.equal(new Set(exerciseLibrary.map((e) => e.id)).size, exerciseLibrary.length);
  assert.equal(new Set(trainingPresets.map((t) => t.id)).size, trainingPresets.length);
  assert.ok(trainingPresets.some((t) => t.id === 'preset-full'));
  for (const template of trainingPresets) {
    assert.equal((await validate(plainToInstance(dto.TrainingTemplateDto, template))).length, 0);
    assert.deepEqual(training.normalizeExercises(template.exercises, true), template.exercises);
    assert.ok(
      template.exercises.every((e) => e.sets.every((s) => !s.completed && s.weightKg === 0)),
    );
  }
});
const exercises = [
  {
    exerciseId: 'squat',
    name: 'test',
    unit: 'reps',
    restSeconds: 60,
    sets: [{ reps: 12, weightKg: 0, completed: true }],
  },
];
test('nested training validation rejects bad sets and null workout exercises', async () => {
  const valid = { name: '全身', durationMinutes: 30, exercises };
  assert.equal((await validate(plainToInstance(dto.TrainingTemplateDto, valid))).length, 0);
  for (const value of [
    { ...valid, exercises: [] },
    {
      ...valid,
      exercises: [{ ...exercises[0], sets: [{ reps: 0, weightKg: -1, completed: 'yes' }] }],
    },
  ])
    assert.ok((await validate(plainToInstance(dto.TrainingTemplateDto, value))).length);
  for (const Dto of [workoutDto.CreateWorkoutDto, updateWorkoutDto.UpdateWorkoutDto])
    assert.ok(
      (
        await validate(
          plainToInstance(Dto, {
            type: 'STRENGTH',
            name: '训练',
            durationMinutes: 10,
            exercises: null,
          }),
        )
      ).length,
    );
});
test('template normalizes catalog names and clears completion state', () => {
  const value = training.normalizeExercises(exercises, true);
  assert.equal(value[0].name, '徒手深蹲');
  assert.equal(value[0].sets[0].completed, false);
  assert.equal(exercises[0].sets[0].completed, true);
  assert.throws(() => training.normalizeExercises([{ ...exercises[0], exerciseId: 'missing' }]), {
    status: 400,
  });
  assert.throws(() => training.normalizeExercises([{ ...exercises[0], unit: 'seconds' }]), {
    status: 400,
  });
});
test('listing templates uses the current owner and never reads another account', async () => {
  let args;
  const service = new training.TrainingService({
    trainingTemplate: {
      findMany: async (input) => {
        args = input;
        return [];
      },
    },
  });
  const templates = await service.templates('owner');
  assert.deepEqual(args.where, { userId: 'owner' });
  assert.ok(templates.every((t) => t.preset));
});
test('updating a missing or foreign template cannot mutate data', async () => {
  let mutated = false;
  const service = new training.TrainingService({
    trainingTemplate: {
      findFirst: async () => null,
      update: async () => {
        mutated = true;
      },
    },
  });
  await assert.rejects(
    service.saveTemplate('owner', { name: '模板', durationMinutes: 30, exercises }, 'foreign'),
    { status: 404 },
  );
  assert.equal(mutated, false);
});
test('weekly plans reject duplicate days and foreign template references', async () => {
  let written = false;
  const service = new training.TrainingService({
    trainingTemplate: { findMany: async () => [] },
    trainingPlan: {
      upsert: async () => {
        written = true;
      },
    },
  });
  const days = Array.from({ length: 7 }, (_, weekday) => ({ weekday, templateId: null }));
  await assert.rejects(
    service.savePlan('owner', { days: days.map((d) => ({ ...d, weekday: 1 })) }),
    { status: 400 },
  );
  const bad = structuredClone(days);
  bad[0].templateId = 'foreign';
  await assert.rejects(service.savePlan('owner', { days: bad }), { status: 400 });
  assert.equal(written, false);
});
test('weekly plans persist seven days for owner and allow preset templates', async () => {
  let data;
  const service = new training.TrainingService({
    trainingTemplate: { findMany: async () => [] },
    trainingPlan: {
      upsert: async (input) => {
        data = input;
      },
    },
  });
  const days = Array.from({ length: 7 }, (_, weekday) => ({
    weekday,
    templateId: weekday === 1 ? 'preset-full' : null,
  }));
  assert.deepEqual(await service.savePlan('owner', { days: [...days].reverse() }), days);
  assert.deepEqual(data.where, { userId: 'owner' });
  assert.equal(data.create.userId, 'owner');
  assert.deepEqual(data.update.days, days);
});

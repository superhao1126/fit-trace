import 'reflect-metadata';
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import provider from '../dist/providers/ai/openai-compatible-food-recognition.provider.js';
import mock from '../dist/providers/ai/mock-food-recognition.provider.js';
import dto from '../dist/ai/dto/food-text.dto.js';

test('meal text is trimmed and rejects blank, non-string and oversized input', async () => {
  const value = plainToInstance(dto.FoodTextDto, { description: '  牛肉面  ' });
  assert.equal(value.description, '牛肉面');
  assert.equal((await validate(value)).length, 0);
  for (const description of ['   ', null, 123, '米'.repeat(501)])
    assert.ok((await validate(plainToInstance(dto.FoodTextDto, { description }))).length);
});
test('both AI transports analyze text without image input; unknown stays null and zero stays zero', async () => {
  const original = globalThis.fetch;
  try {
    for (const mode of ['chat', 'responses']) {
      let sent;
      globalThis.fetch = async (_url, options) => {
        sent = JSON.parse(options.body);
        const output = JSON.stringify({
          foods: [
            null,
            { name: '面', portion: null, grams: null, kcalPer100g: null },
            { name: '水', grams: 200, kcalPer100g: 0 },
            { name: '饭', grams: 200, kcalPer100g: 116 },
          ],
        });
        return {
          ok: true,
          json: async () =>
            mode === 'chat'
              ? { choices: [{ message: { content: output } }] }
              : { output_text: output },
        };
      };
      const values = { AI_API_KEY: 'fixture', AI_API_MODE: mode };
      const ai = new provider.OpenAiCompatibleFoodRecognitionProvider({
        get: (key, fallback) => values[key] ?? fallback,
      });
      const result = await ai.analyzeText('一碗面，水和米饭');
      assert.deepEqual(
        result.foods.map((f) => f.estimatedCalories),
        [null, 0, 232],
      );
      const content = (sent.messages ?? sent.input)[0].content;
      assert.equal(content.length, 1);
      assert.ok(content[0].text.includes('一碗面，水和米饭'));
      assert.ok(!JSON.stringify(sent).includes('image_url'));
      const schema =
        mode === 'chat' ? sent.response_format.json_schema.schema : sent.text.format.schema;
      assert.deepEqual(
        schema.properties.foods.items.required,
        Object.keys(schema.properties.foods.items.properties),
      );
      await ai.recognize('https://example.invalid/meal.png');
      assert.equal((sent.messages ?? sent.input)[0].content.length, 2);
    }
  } finally {
    globalThis.fetch = original;
  }
});
test('unconfigured text AI fails explicitly instead of returning a fixed fake meal', async () => {
  await assert.rejects(new mock.MockFoodRecognitionProvider().analyzeText('牛肉面'), {
    status: 503,
  });
  const ai = new provider.OpenAiCompatibleFoodRecognitionProvider({ get: () => undefined });
  await assert.rejects(ai.analyzeText('牛肉面'), { status: 503 });
});

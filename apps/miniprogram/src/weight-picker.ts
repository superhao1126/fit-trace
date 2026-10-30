export type WeightParts = [number, number];

/** Use integer hundredths to preserve existing two-decimal records. */
export function toWeightParts(weight: number = 70): WeightParts {
  const cents = Math.min(
    50000,
    Math.max(100, Math.round((Number.isFinite(weight) ? weight : 70) * 100)),
  );
  return [Math.floor(cents / 100), cents % 100];
}

export function normalizeWeightParts(parts: Array<string | number>): WeightParts {
  const whole = Math.min(500, Math.max(1, Math.trunc(Number(parts[0])) || 70));
  const fraction = whole === 500 ? 0 : Math.min(99, Math.max(0, Math.trunc(Number(parts[1])) || 0));
  return [whole, fraction];
}

export function fromWeightParts(parts: WeightParts): number {
  const [whole, fraction] = normalizeWeightParts(parts);
  return (whole * 100 + fraction) / 100;
}

const [jsUrl, wasmUrl] = process.argv.slice(2);
if (!jsUrl || !wasmUrl) {
  throw new Error("Usage: bun benchmark-workers.mjs <js-worker-url> <wasm-worker-url>");
}

const targets = [
  { name: "js", baseUrl: new URL(jsUrl) },
  { name: "wasm", baseUrl: new URL(wasmUrl) },
];
const expectedChecksum = "3686109669";
const warmupCount = 5;
const sampleCount = 50;
const samples = new Map(targets.map(({ name }) => [name, { baseline: [], benchmark: [] }]));

async function measure(target, path) {
  const start = performance.now();
  const response = await fetch(new URL(path, target.baseUrl), { cache: "no-store" });
  const body = await response.text();
  const elapsed = performance.now() - start;
  const expectedBody = path === "/benchmark" ? expectedChecksum : "Hello, Kotlin!";
  if (response.status !== 200 || body !== expectedBody) {
    throw new Error(`${target.name} returned ${response.status} ${JSON.stringify(body)}`);
  }
  return elapsed;
}

for (let index = 0; index < warmupCount; index++) {
  for (const target of targets) await measure(target, "/benchmark");
}

for (let index = 0; index < sampleCount; index++) {
  const order = index % 2 === 0 ? targets : targets.toReversed();
  for (const target of order) {
    const routes =
      index % 2 === 0 ? ["/hello/Kotlin", "/benchmark"] : ["/benchmark", "/hello/Kotlin"];
    const elapsed = samples.get(target.name);
    elapsed.baseline.push(await measure(target, routes[0]));
    elapsed.benchmark.push(await measure(target, routes[1]));
  }
}

function percentile(sorted, fraction) {
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)];
}

for (const target of targets) {
  const result = samples.get(target.name);
  const baseline = result.baseline.toSorted((left, right) => left - right);
  const benchmark = result.benchmark.toSorted((left, right) => left - right);
  const medianBaseline = percentile(baseline, 0.5);
  const medianBenchmark = percentile(benchmark, 0.5);
  console.log(
    `${target.name}: hello median ${medianBaseline.toFixed(1)} ms, ` +
      `benchmark median ${medianBenchmark.toFixed(1)} ms ` +
      `(delta ${(medianBenchmark - medianBaseline).toFixed(1)} ms); ` +
      `benchmark p95 ${percentile(benchmark, 0.95).toFixed(1)} ms (n=${benchmark.length})`,
  );
}

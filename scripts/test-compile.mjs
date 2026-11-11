import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../", import.meta.url));
const reports = new URL("../build/reports/compile-tests/", import.meta.url);
await mkdir(reports, { recursive: true });

function parseShardValue(name, fallback) {
  const value = process.env[name];
  if (value === undefined) return fallback;
  assert.match(value, /^\d+$/, `${name} must be a non-negative integer`);
  return Number(value);
}

const cases = [
  ["EnvironmentConversionPositive", []],
  ["WrongEnvironmentConversion", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["CommonAdaptersPositive", []],
  ["WrongCommonAdapters", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["CloudflareWorkerPositive", []],
  ["WrongCloudflareWorker", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["Positive", []],
  ["WrongResponse", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["WrongBody", ["ARGUMENT_TYPE_MISMATCH"]],
  ["WrongPath", ["INITIALIZER_TYPE_MISMATCH"]],
  ["WrongInput", ["INITIALIZER_TYPE_MISMATCH"]],
  ["WrongParsedInput", ["INITIALIZER_TYPE_MISMATCH"]],
  ["IncompleteHandler", ["NO_ELSE_IN_WHEN"]],
  ["WrongCodec", ["ARGUMENT_TYPE_MISMATCH"]],
  ["WrongPlugin", ["UNRESOLVED_REFERENCE"]],
  ["WrongAttributeWrite", ["ARGUMENT_TYPE_MISMATCH", "ASSIGNMENT_TYPE_MISMATCH"]],
  ["WrongAttributeRead", ["INITIALIZER_TYPE_MISMATCH"]],
  ["WrongLoggingConfiguration", ["ASSIGNMENT_TYPE_MISMATCH"]],
  ["WrongCorsMethod", ["ARGUMENT_TYPE_MISMATCH"]],
  ["WrongSerializer", ["ARGUMENT_TYPE_MISMATCH", "INITIALIZER_TYPE_MISMATCH"]],
  ["MissingSerializer", ["NO_VALUE_FOR_PARAMETER"]],
  ["ImplicitReceive", ["NO_VALUE_FOR_PARAMETER"]],
  ["ImplicitResponse", ["NO_VALUE_FOR_PARAMETER"]],
  ["WrongBearerVerifier", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["WrongPrincipal", ["INITIALIZER_TYPE_MISMATCH"]],
  ["WrongAuthenticatedPrincipal", ["INITIALIZER_TYPE_MISMATCH"]],
  ["WrongAuthenticatedResponse", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["WrongErrorResponse", ["RETURN_TYPE_MISMATCH", "ARGUMENT_TYPE_MISMATCH"]],
  ["MissingHttp", ["UNRESOLVED_REFERENCE_WRONG_RECEIVER"]],
  ["MissingErrorHandling", ["UNRESOLVED_REFERENCE_WRONG_RECEIVER", "UNRESOLVED_REFERENCE"]],
];

const shardCount = parseShardValue("COMPILE_TEST_SHARD_COUNT", 1);
const shardIndex = parseShardValue("COMPILE_TEST_SHARD_INDEX", 0);
assert.ok(shardCount > 0, "COMPILE_TEST_SHARD_COUNT must be greater than zero");
assert.ok(
  shardIndex < shardCount,
  "COMPILE_TEST_SHARD_INDEX must be less than COMPILE_TEST_SHARD_COUNT",
);

const selectedCases = cases.filter((_, index) => index % shardCount === shardIndex);
assert.ok(selectedCases.length > 0, "The selected compile-test shard is empty");

for (const [fixture, diagnostics] of selectedCases) {
  const result = spawnSync(
    "./gradlew",
    [":compile-tests:compileKotlinWasmJs", `-PcompileFixture=${fixture}`, "--console=plain"],
    { cwd: root, encoding: "utf8", timeout: 180_000 },
  );
  if (result.error) throw result.error;
  const output = result.stdout + result.stderr;
  await writeFile(new URL(`${fixture}.log`, reports), output);
  if (diagnostics.length === 0) {
    assert.equal(result.status, 0, `Positive compilation failed:\n${output}`);
  } else {
    assert.notEqual(result.status, 0, `${fixture} unexpectedly compiled`);
    assert.ok(
      output
        .split("\n")
        .some(
          (line) =>
            line.includes(`${fixture}.kt:`) &&
            diagnostics.some((code) => line.includes(`[${code}]`)),
        ),
      `${fixture} failed without the expected type diagnostic:\n${output}`,
    );
  }
  console.log(`Compile ${fixture}: ${diagnostics.length ? "rejected as expected" : "passed"}`);
}

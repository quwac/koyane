import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../../", import.meta.url));
const example = fileURLToPath(new URL("../", import.meta.url));
function run(command, args, cwd = root) {
  const result = spawnSync(command, args, { cwd, stdio: "inherit" });
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${command} failed`);
}
for (const target of ["js", "wasm"]) {
  const kotlinTarget = target === "js" ? "js" : "wasmJs";
  run("./gradlew", [
    `:examples:hello-workers:${kotlinTarget}ProductionExecutableCompileSync`,
    "-PcloudflareWorkerFixture",
    "--console=plain",
  ]);
  run("bun", [`examples/hello-workers/scripts/prepare-workers-${target}.mjs`]);
  const config = `wrangler.${target}.jsonc`;
  const wrangler = ["node", "node_modules/wrangler/bin/wrangler.js"];
  run(
    wrangler[0],
    [
      ...wrangler.slice(1),
      "deploy",
      "--dry-run",
      "--config",
      config,
      "--outdir",
      `build/request-worker-${target}`,
    ],
    example,
  );
  const server = Bun.spawn({
    cmd: [
      ...wrangler,
      "dev",
      "--local",
      "--config",
      config,
      "--ip",
      "127.0.0.1",
      "--port",
      "0",
      "--var",
      "GREETING:common",
      "--show-interactive-dev-session=false",
    ],
    cwd: example,
    stdout: "pipe",
    stderr: "pipe",
  });
  let logs = "";
  const ready = Promise.withResolvers();
  async function capture(stream) {
    for await (const chunk of stream) {
      logs = (logs + new TextDecoder().decode(chunk)).slice(-32768);
      const match = logs.match(/Ready on (http:\/\/127\.0\.0\.1:\d+)/);
      if (match) ready.resolve(match[1]);
    }
  }
  const captures = [capture(server.stdout), capture(server.stderr)];
  server.exited.then((code) => ready.reject(new Error(`Wrangler exited: ${code}\n${logs}`)));
  const timer = setTimeout(() => ready.reject(new Error(`Startup timed out\n${logs}`)), 30000);
  try {
    const origin = await ready.promise;
    clearTimeout(timer);
    const request = (pathname) => fetch(origin + pathname, { signal: AbortSignal.timeout(10000) });
    const responses = await Promise.all(Array.from({ length: 16 }, () => request("/hello")));
    for (const response of responses) assert.equal(response.status, 200);
    const bodies = await Promise.all(responses.map((response) => response.text()));
    const ids = bodies.map((body) => {
      assert.match(body, /^common\/\d+$/);
      return Number(body.split("/")[1]);
    });
    assert.equal(new Set(ids).size, 16);
    const released = (await (await request("/released")).text()).split(",").map(Number);
    assert.deepEqual(
      released,
      ids.toSorted((a, b) => a - b),
    );
    assert.equal((await request("/failure")).status, 500);
    const afterFailure = (await (await request("/released")).text()).split(",").map(Number);
    assert.deepEqual(
      afterFailure,
      Array.from({ length: 18 }, (_, index) => index + 1),
    );
    console.log(
      `Cloudflare Worker ${target}: common environment, concurrent request isolation, release after success/failure, dry-run passed`,
    );
  } finally {
    clearTimeout(timer);
    server.kill("SIGTERM");
    const shutdown = setTimeout(() => server.kill("SIGKILL"), 5000);
    try {
      await server.exited;
      await Promise.all(captures);
    } finally {
      clearTimeout(shutdown);
    }
  }
}

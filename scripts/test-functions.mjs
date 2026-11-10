import assert from "node:assert/strict";
import { spawn, spawnSync, execFileSync } from "node:child_process";
import { once } from "node:events";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const target = process.argv[2];
const host = process.argv[3];
if (
  !["js", "wasm"].includes(target) ||
  !["vercel", "lambda", "cloud-run", "supabase"].includes(host)
) {
  throw new Error("Expected js|wasm vercel|lambda|cloud-run|supabase");
}
const frameworkMain = fileURLToPath(
  new URL("./main.js", import.meta.resolve("@google-cloud/functions-framework")),
);
const root = fileURLToPath(new URL("../", import.meta.url));
const examples = {
  vercel: "hello-fetch-functions",
  supabase: "hello-fetch-functions",
  lambda: "hello-lambda",
  "cloud-run": "hello-cloud-run-functions",
};
const directory = path.join(root, `examples/${examples[host]}/build/${host}-${target}`);
let invoke;
let cleanup = async () => {};
let logs = "";
let passed = false;
try {
  if (host === "vercel") {
    const { default: entry } = await import(pathToFileURL(path.join(directory, "api/index.mjs")));
    invoke = (route, options = {}) =>
      entry.fetch(new Request(`https://example.test${route}`, options));
  } else if (host === "lambda") {
    const { handler } = await import(pathToFileURL(path.join(directory, "index.mjs")));
    // Non-HTTP events and payload v1 must fail explicitly.
    await assert.rejects(handler({ version: "1.0" }), /version 2.0/);
    await assert.rejects(handler({ Records: [] }), /version 2.0/);
    await assert.rejects(handler({ version: "2.0" }), /HTTP method/);
    invoke = async (route, options = {}) => {
      const url = new URL(route, "https://example.test");
      const headers = Object.fromEntries(new Headers(options.headers));
      const requestCookie = headers.cookie;
      delete headers.cookie;
      const body = options.body ?? null;
      const output = await handler({
        version: "2.0",
        rawPath: url.pathname,
        rawQueryString: url.search.slice(1),
        headers,
        ...(requestCookie ? { cookies: requestCookie.split("; ") } : {}),
        requestContext: { http: { method: options.method ?? "GET" } },
        body: body === null ? null : Buffer.from(body).toString("base64"),
        isBase64Encoded: body !== null,
      });
      assert.equal(output.isBase64Encoded, false);
      assert.ok(Array.isArray(output.cookies));
      assert.ok(!Object.keys(output.headers).some((key) => key.toLowerCase() === "set-cookie"));
      const nativeHeaders = new Headers(output.headers);
      for (const cookie of output.cookies) nativeHeaders.append("Set-Cookie", cookie);
      return new Response([204, 205, 304].includes(output.statusCode) ? null : output.body, {
        status: output.statusCode,
        headers: nativeHeaders,
      });
    };
    const plain = await handler({
      version: "2.0",
      rawPath: "/echo",
      rawQueryString: "prefix=plain",
      headers: { "X-Message": "value" },
      requestContext: { http: { method: "POST" } },
      body: "日本語",
      isBase64Encoded: false,
    });
    assert.equal(plain.body, "plain:value:日本語");
    await assert.rejects(
      handler({
        version: "2.0",
        rawPath: "/echo",
        requestContext: { http: { method: "POST" } },
        body: "?",
        isBase64Encoded: true,
      }),
    );
  } else {
    let processHandle;
    let origin;
    if (host === "cloud-run") {
      const { createServer } = await import("node:net");
      const reservation = createServer();
      reservation.listen(0, "127.0.0.1");
      await once(reservation, "listening");
      const port = reservation.address().port;
      await new Promise((resolve) => reservation.close(resolve));
      origin = `http://127.0.0.1:${port}`;
      processHandle = spawn(process.execPath, [frameworkMain, "--target=hello", `--port=${port}`], {
        cwd: directory,
        stdio: ["ignore", "pipe", "pipe"],
      });
      const closed = once(processHandle, "close");
      cleanup = async () => {
        processHandle.kill();
        await closed;
      };
    } else {
      const image =
        "public.ecr.aws/supabase/edge-runtime:v1.78.1@sha256:362e9177660ae4d3affb6978480cc6161a8ea0764f1c2c7f738348eda1c3fbf2";
      const name = `koyane-functions-${target}-${process.pid}`;
      execFileSync(
        "docker",
        [
          "run",
          "--detach",
          "--rm",
          "--name",
          name,
          "--publish",
          "127.0.0.1::9000",
          "--mount",
          `type=bind,source=${root}/examples/hello-fetch-functions,target=/home/deno/example,readonly`,
          "--env",
          `KOYANE_SERVICE_PATH=/home/deno/example/build/supabase-${target}/supabase/functions/hello`,
          "--mount",
          `type=bind,source=${root}/scripts/functions-edge-main.ts,target=/home/deno/main/index.ts,readonly`,
          image,
          "start",
          "--main-service",
          "/home/deno/main",
        ],
        { stdio: "pipe" },
      );
      cleanup = async () => {
        const result = spawnSync("docker", ["logs", name], { encoding: "utf8" });
        logs = result.stdout + result.stderr;
        execFileSync("docker", ["rm", "--force", name], { stdio: "pipe" });
      };
      const port = execFileSync("docker", ["port", name, "9000/tcp"], { encoding: "utf8" })
        .trim()
        .split(":")
        .at(-1);
      origin = `http://127.0.0.1:${port}/hello`;
    }
    if (processHandle) {
      processHandle.stdout.on("data", (data) => {
        logs += data;
      });
      processHandle.stderr.on("data", (data) => {
        logs += data;
      });
    }
    const deadline = Date.now() + 30_000;
    while (true) {
      try {
        const response = await fetch(`${origin}/hello/ready`, {
          signal: AbortSignal.timeout(2_000),
        });
        const body = await response.text();
        assert.equal(response.status, 200, body);
        assert.equal(body, "Hello, ready!");
        break;
      } catch (error) {
        if (Date.now() > deadline || processHandle?.exitCode != null) throw error;
        await new Promise((resolve) => setTimeout(resolve, 100));
      }
    }
    invoke = (route, options = {}) =>
      fetch(`${origin}${route}`, { ...options, signal: AbortSignal.timeout(5_000) });
  }
  let response = await invoke("/hello/%E4%B8%96%E7%95%8C");
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "Hello, 世界!");
  assert.match(response.headers.get("content-type"), /^text\/plain; charset=utf-8$/);
  // Parsed JSON and urlencoded bodies must reach Kotlin in their original form.
  for (const [type, body] of [
    ["text/plain", "body 世界🌏"],
    ["application/json", '{ "hello": "世界" }'],
    ["application/x-www-form-urlencoded", "hello=a%26b&hello=c"],
  ]) {
    response = await invoke("/echo?prefix=a%26b", {
      method: "POST",
      headers: { "Content-Type": type, "X-Message": "message" },
      body,
    });
    assert.equal(response.status, 200);
    assert.equal(await response.text(), `a&b:message:${body}`);
  }
  response = await invoke("/headers", {
    headers: { "X-Value": "first, second", Cookie: "a=1; b=2" },
  });
  assert.equal(await response.text(), "first, second|a=1; b=2");
  response = await invoke("/head", { method: "HEAD" });
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "");
  response = await invoke("/empty", { method: "DELETE" });
  assert.equal(response.status, 204);
  assert.equal(await response.text(), "");
  response = await invoke("/cookie");
  assert.equal(
    response.headers.get("Set-Cookie"),
    "session=test; Expires=Wed, 01 Jan 2031 00:00:00 GMT; HttpOnly",
  );
  response = await invoke("/missing");
  assert.equal(response.status, 404);
  response = await invoke("/echo?prefix=missing", { method: "POST", body: "body" });
  assert.equal(response.status, 400);
  response = await invoke("/failure");
  assert.equal(response.status, 500);
  assert.doesNotMatch(await response.text(), /private failure/);
  const names = ["one", "two", "three"];
  assert.deepEqual(
    await Promise.all(names.map(async (name) => (await invoke(`/slow/${name}`)).text())),
    names,
  );
  passed = true;
  console.log(
    `${host} ${target}: HTTP, UTF-8, raw bodies, query, headers, cookies, HEAD, empty, errors, and concurrent suspend handlers passed`,
  );
} finally {
  await cleanup();
  if (!passed) console.error(logs);
}

import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import http from "node:http";
import { once } from "node:events";
import { fileURLToPath } from "node:url";

const target = process.argv[2];
if (target !== "js" && target !== "wasm") throw new Error("Expected js or wasm");
const cwd = fileURLToPath(new URL(`../build/lambda-web-${target}/`, import.meta.url));
const server = spawn(process.execPath, ["index.js"], {
  cwd,
  env: { ...process.env, AWS_LAMBDA_HTTP_ENDPOINT: "127.0.0.1:0" },
  stdio: ["ignore", "pipe", "pipe"],
});
const closed = once(server, "close");
const timeout = setTimeout(() => server.kill("SIGKILL"), 30_000);
let output = "";
server.stdout.on("data", (data) => {
  output += data;
});
server.stderr.on("data", (data) => {
  output += data;
});

async function waitForAddress() {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const match = /Listening on 127\.0\.0\.1:(\d+)/.exec(output);
    if (match) return `http://127.0.0.1:${match[1]}`;
    assert.equal(server.exitCode, null, `Server exited before listening:\n${output}`);
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error(`Server did not start:\n${output}`);
}

function rawRequest(url, options, chunks) {
  return new Promise((resolve, reject) => {
    const request = http.request(url, options, (response) => {
      response.setEncoding("utf8");
      let body = "";
      response.on("data", (chunk) => {
        body += chunk;
      });
      response.on("end", () => resolve({ status: response.statusCode, body }));
      response.on("error", reject);
    });
    request.on("error", reject);
    for (const chunk of chunks) request.write(chunk);
    request.end();
  });
}

try {
  const origin = await waitForAddress();
  let response = await fetch(`${origin}/hello/%E4%B8%96%E7%95%8C`);
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "Hello, 世界!");
  assert.match(response.headers.get("content-type"), /^text\/plain; charset=utf-8$/);

  response = await fetch(`${origin}/echo?prefix=a%26b`, {
    method: "POST",
    headers: { "X-Message": "message" },
    body: "body 世界",
  });
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "a&b:message:body 世界");

  const encoded = Buffer.from("分割された本文🌏");
  const streamed = await rawRequest(
    `${origin}/echo?prefix=stream`,
    {
      method: "POST",
      headers: { "X-Message": "utf8" },
    },
    Array.from(encoded, (byte) => Buffer.from([byte])),
  );
  assert.deepEqual(streamed, { status: 200, body: "stream:utf8:分割された本文🌏" });

  const headers = await rawRequest(
    `${origin}/headers`,
    {
      headers: { "X-Value": ["one", "two"], Cookie: ["a=1", "b=2"] },
    },
    [],
  );
  assert.deepEqual(headers, { status: 200, body: "one, two|a=1; b=2" });

  response = await fetch(`${origin}/echo?prefix=x`, { method: "POST", body: "missing header" });
  assert.equal(response.status, 400);
  await response.text();

  response = await fetch(`${origin}/hello/Kotlin`, { method: "OPTIONS" });
  assert.equal(response.status, 405);
  assert.equal(response.headers.get("allow"), "GET");
  await response.text();
  response = await fetch(`${origin}/missing`);
  assert.equal(response.status, 404);
  await response.text();

  response = await fetch(`${origin}/head`, { method: "HEAD" });
  assert.equal(response.status, 200);
  assert.equal(await response.text(), "");
  response = await fetch(`${origin}/empty`, { method: "DELETE" });
  assert.equal(response.status, 204);
  assert.equal(await response.text(), "");

  response = await fetch(`${origin}/failure`);
  assert.equal(response.status, 500);
  assert.equal(await response.text(), "Internal Server Error");

  await Promise.all(
    Array.from({ length: 24 }, async (_, index) => {
      const reply = await fetch(`${origin}/echo?prefix=${index}`, {
        method: "POST",
        headers: { "X-Message": `header-${index}` },
        body: `body-${index}`,
      });
      assert.equal(reply.status, 200);
      assert.equal(await reply.text(), `${index}:header-${index}:body-${index}`);
    }),
  );

  const aborted = http.request(`${origin}/echo?prefix=abort`, { method: "POST" });
  aborted.on("error", () => {});
  aborted.write("unfinished");
  await new Promise((resolve) => setTimeout(resolve, 30));
  aborted.destroy();
  response = await fetch(`${origin}/hello/after-abort`);
  assert.equal(await response.text(), "Hello, after-abort!");
  console.log(
    `Node ${process.versions.node} ${target} integration passed: HTTP, UTF-8, duplicate headers, errors, concurrency, disconnects`,
  );
} finally {
  clearTimeout(timeout);
  server.kill("SIGTERM");
  await closed;
}

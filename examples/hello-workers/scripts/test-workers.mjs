import assert from "node:assert/strict";
import { rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";

// Wrangler's Node process owns workerd; Bun runs assertions and HTTP requests.
const target = process.argv[2] ?? "wasm";
if (!["js", "wasm"].includes(target)) {
  throw new Error(`Unknown Workers target: ${target}`);
}
const config = target === "js" ? "wrangler.js.jsonc" : "wrangler.wasm.jsonc";
const todoId = "123e4567-e89b-12d3-a456-426614174000";
const missingTodoId = "123e4567-e89b-12d3-a456-426614174099";
const example = fileURLToPath(new URL("../", import.meta.url));
const persistence = fileURLToPath(new URL(`../build/wrangler-${target}`, import.meta.url));
await rm(persistence, { recursive: true, force: true });
const migration = Bun.spawn({
  cmd: [
    "node",
    "node_modules/wrangler/bin/wrangler.js",
    "d1",
    "migrations",
    "apply",
    "DB",
    "--config",
    config,
    "--local",
    "--persist-to",
    persistence,
  ],
  cwd: example,
  env: { ...process.env, CI: "true" },
  stdout: "pipe",
  stderr: "pipe",
});
const [migrationStdout, migrationStderr, migrationExitCode] = await Promise.all([
  new Response(migration.stdout).text(),
  new Response(migration.stderr).text(),
  migration.exited,
]);
if (migrationExitCode !== 0) {
  await rm(persistence, { recursive: true, force: true });
  throw new Error(
    `D1 migration failed with ${migrationExitCode}:\n${migrationStdout}${migrationStderr}`,
  );
}
const server = Bun.spawn({
  cmd: [
    "node",
    "node_modules/wrangler/bin/wrangler.js",
    "dev",
    "--config",
    config,
    "--local",
    "--persist-to",
    persistence,
    "--ip",
    "127.0.0.1",
    "--port",
    "0",
    "--show-interactive-dev-session=false",
  ],
  cwd: example,
  stdout: "pipe",
  stderr: "pipe",
});
const ready = Promise.withResolvers();
let logs = "";
async function capture(stream) {
  const decoder = new TextDecoder();
  for await (const chunk of stream) {
    logs = (logs + decoder.decode(chunk, { stream: true })).slice(-32_768);
    const match = logs.match(/Ready on (http:\/\/127\.0\.0\.1:\d+)/);
    if (match) ready.resolve(match[1]);
  }
}
const captures = [capture(server.stdout), capture(server.stderr)];
server.exited.then((code) => ready.reject(new Error(`Wrangler exited with ${code}:\n${logs}`)));
const startupTimer = setTimeout(
  () => ready.reject(new Error(`Wrangler startup timed out:\n${logs}`)),
  30_000,
);

try {
  const origin = await ready.promise;
  clearTimeout(startupTimer);
  const worker = {
    fetch(input, init) {
      const source = new URL(input);
      return fetch(new URL(source.pathname + source.search, origin), {
        ...init,
        signal: AbortSignal.timeout(10_000),
      });
    },
  };
  for (const [path, status, body] of [
    ["/benchmark", 200, "3686109669"],
    ["/hello/Kotlin", 200, "Hello, Kotlin!"],
    ["/hello/%E4%B8%96%E7%95%8C", 200, "Hello, 世界!"],
    ["/hello/a%2Fb", 200, "Hello, a/b!"],
    ["/hello/%252F", 200, "Hello, %2F!"],
    ["/hello/Ada?ignored=yes", 200, "Hello, Ada!"],
    ["/users/1", 200, "Ada"],
    ["/users/2", 404, ""],
    ["/users/not-a-long", 400, ""],
    ["/users/9223372036854775808", 400, ""],
    ["/hello/%FF", 400, ""],
    ["/hello/%", 400, ""],
    ["/missing", 404, ""],
    ["/failure", 500, ""],
    ["/hello/", 404, ""],
  ]) {
    const response = await worker.fetch(`http://example.test${path}`);
    assert.equal(response.status, status, path);
    assert.equal(await response.text(), body, path);
    if (status === 200) {
      assert.equal(response.headers.get("content-type"), "text/plain; charset=utf-8");
    }
  }
  const wrongMethod = await worker.fetch("http://example.test/hello/Ada", { method: "POST" });
  assert.equal(wrongMethod.status, 405);
  assert.equal(wrongMethod.headers.get("allow"), "GET");

  const uuid = "550e8400-e29b-41d4-a716-446655440000";
  const mixedPath = `/organizations/${uuid}/users/${uuid}`;
  const mixed = await worker.fetch(
    `http://example.test${mixedPath}?page=2&limit=5&enabled=false&q=a+b%2Bc`,
    {
      headers: { authorization: "Bearer synthetic", "X-Request-Id": "r1" },
    },
  );
  assert.equal(mixed.status, 200);
  assert.equal(await mixed.text(), `${uuid}/${uuid}/2/5/false/a b+c/Bearer synthetic/r1`);
  const defaults = await worker.fetch(`http://example.test${mixedPath}`, {
    headers: { Authorization: "token" },
  });
  assert.equal(await defaults.text(), `${uuid}/${uuid}/1/10/true/null/token/null`);
  for (const [path, headers] of [
    [mixedPath, {}],
    [`${mixedPath}?page=invalid`, { Authorization: "token" }],
    [`${mixedPath}?enabled=yes`, { Authorization: "token" }],
    [`${mixedPath}?q=%FF`, { Authorization: "token" }],
    [`/organizations/not-a-uuid/users/${uuid}`, { Authorization: "token" }],
  ]) {
    assert.equal((await worker.fetch(`http://example.test${path}`, { headers })).status, 400, path);
  }
  const json = await worker.fetch("http://example.test/numbers/3", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "4",
  });
  assert.equal(json.status, 201);
  assert.equal(json.headers.get("content-type"), "application/json; charset=utf-8");
  assert.equal(await json.text(), "7");
  const queryMethod = await worker.fetch("http://example.test/numbers/search", {
    method: "QUERY",
    headers: { "Content-Type": "application/json" },
    body: "4",
  });
  assert.equal(queryMethod.status, 200);
  assert.equal(await queryMethod.text(), "4");
  const queryWrongMethod = await worker.fetch("http://example.test/numbers/search");
  assert.equal(queryWrongMethod.status, 405);
  assert.ok(queryWrongMethod.headers.get("allow").split(", ").includes("QUERY"));
  assert.equal(
    (
      await worker.fetch("http://example.test/numbers/3", {
        method: "POST",
        body: "{invalid}",
      })
    ).status,
    415,
  );
  const createdUser = await worker.fetch("http://example.test/users", {
    method: "POST",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Accept: "application/json",
    },
    body: '{"name":"Ada"}',
  });
  assert.equal(createdUser.status, 200);
  assert.equal(createdUser.headers.get("content-type"), "application/json; charset=utf-8");
  assert.deepEqual(await createdUser.json(), { id: "123", name: "Ada" });
  assert.equal(
    (
      await worker.fetch("http://example.test/users", {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "text/plain" },
        body: '{"name":"Ada"}',
      })
    ).status,
    406,
  );

  const createdTodo = await worker.fetch("http://example.test/todos", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ id: todoId, title: "Read D1 docs" }),
  });
  assert.equal(createdTodo.status, 201);
  assert.deepEqual(await createdTodo.json(), {
    id: todoId,
    title: "Read D1 docs",
    completed: false,
  });
  const duplicateTodo = await worker.fetch("http://example.test/todos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: todoId, title: "Duplicate" }),
  });
  assert.equal(duplicateTodo.status, 400);
  assert.equal(await duplicateTodo.text(), "");
  const listedTodos = await worker.fetch("http://example.test/todos", {
    headers: { Accept: "application/json" },
  });
  assert.equal(listedTodos.status, 200);
  assert.deepEqual(await listedTodos.json(), [
    { id: todoId, title: "Read D1 docs", completed: false },
  ]);
  const foundTodo = await worker.fetch(`http://example.test/todos/${todoId}`, {
    headers: { Accept: "application/json" },
  });
  assert.equal(foundTodo.status, 200);
  assert.deepEqual(await foundTodo.json(), {
    id: todoId,
    title: "Read D1 docs",
    completed: false,
  });
  const updatedTodo = await worker.fetch(`http://example.test/todos/${todoId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ title: "Ship the Worker", completed: true }),
  });
  assert.equal(updatedTodo.status, 200);
  assert.deepEqual(await updatedTodo.json(), {
    id: todoId,
    title: "Ship the Worker",
    completed: true,
  });
  const missingUpdate = await worker.fetch(`http://example.test/todos/${missingTodoId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title: "Missing", completed: true }),
  });
  assert.equal(missingUpdate.status, 404);
  assert.equal(await missingUpdate.text(), "");
  assert.equal((await worker.fetch("http://example.test/todos/not-a-uuid")).status, 400);
  const invalidTodo = await worker.fetch("http://example.test/todos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: "", title: "Invalid" }),
  });
  assert.equal(invalidTodo.status, 400);
  assert.equal(await invalidTodo.text(), "");
  const deletedTodo = await worker.fetch(`http://example.test/todos/${todoId}`, {
    method: "DELETE",
  });
  assert.equal(deletedTodo.status, 204);
  assert.equal(await deletedTodo.text(), "");
  assert.equal((await worker.fetch(`http://example.test/todos/${todoId}`)).status, 404);
  assert.equal(
    (
      await worker.fetch(`http://example.test/todos/${todoId}`, {
        method: "DELETE",
      })
    ).status,
    404,
  );

  const corsOrigin = "https://client.example";
  const preflight = await worker.fetch("http://example.test/numbers/3", {
    method: "OPTIONS",
    headers: {
      Origin: corsOrigin,
      "Access-Control-Request-Method": "POST",
      "Access-Control-Request-Headers": "Content-Type, Authorization",
    },
  });
  assert.equal(preflight.status, 204);
  assert.equal(await preflight.text(), "");
  assert.equal(preflight.headers.get("access-control-allow-origin"), corsOrigin);
  assert.equal(
    preflight.headers.get("access-control-allow-headers"),
    "authorization, content-type",
  );
  assert.ok(preflight.headers.get("access-control-allow-methods").split(", ").includes("POST"));
  assert.ok(preflight.headers.get("access-control-allow-methods").split(", ").includes("PUT"));
  assert.ok(preflight.headers.get("access-control-allow-methods").split(", ").includes("DELETE"));
  assert.equal(preflight.headers.get("access-control-max-age"), "600");
  assert.deepEqual(
    new Set(preflight.headers.get("vary").toLowerCase().split(", ")),
    new Set(["origin", "access-control-request-method", "access-control-request-headers"]),
  );
  for (const headers of [
    { Origin: "https://other.example", "Access-Control-Request-Method": "POST" },
    { Origin: corsOrigin, "Access-Control-Request-Method": "PATCH" },
    {
      Origin: corsOrigin,
      "Access-Control-Request-Method": "POST",
      "Access-Control-Request-Headers": "X-Denied",
    },
  ]) {
    const denied = await worker.fetch("http://example.test/numbers/3", {
      method: "OPTIONS",
      headers,
    });
    assert.equal(denied.status, 403);
    assert.equal(denied.headers.get("access-control-allow-origin"), null);
  }
  for (const [path, method, status] of [
    ["/hello/Cors", "GET", 200],
    ["/users/no", "GET", 400],
    ["/missing", "GET", 404],
    ["/hello/Cors", "POST", 405],
  ]) {
    const response = await worker.fetch(`http://example.test${path}`, {
      method,
      headers: { Origin: corsOrigin },
    });
    assert.equal(response.status, status);
    assert.equal(response.headers.get("access-control-allow-origin"), corsOrigin);
    assert.equal(response.headers.get("access-control-expose-headers"), "allow");
    assert.equal(response.headers.get("vary"), "Origin");
    await response.text();
  }
  const deniedOrigin = await worker.fetch("http://example.test/hello/Cors", {
    headers: { Origin: "https://other.example" },
  });
  assert.equal(deniedOrigin.status, 200);
  assert.equal(deniedOrigin.headers.get("access-control-allow-origin"), null);
  await deniedOrigin.text();

  for (const [authorization, status, challenge] of [
    [null, 401, 'Bearer realm="example"'],
    ["Basic synthetic", 401, 'Bearer realm="example"'],
    ["Bearer synthetic-rejected", 401, 'Bearer realm="example", error="invalid_token"'],
    ["Bearer bad,header", 400, 'Bearer realm="example", error="invalid_request"'],
  ]) {
    const response = await worker.fetch("http://example.test/me", {
      headers: {
        Origin: corsOrigin,
        ...(authorization ? { Authorization: authorization } : {}),
      },
    });
    assert.equal(response.status, status);
    assert.equal(response.headers.get("www-authenticate"), challenge);
    assert.equal(response.headers.get("access-control-allow-origin"), corsOrigin);
    assert.equal(await response.text(), "");
  }
  const authPreflight = await worker.fetch("http://example.test/me", {
    method: "OPTIONS",
    headers: {
      Origin: corsOrigin,
      "Access-Control-Request-Method": "GET",
      "Access-Control-Request-Headers": "Authorization",
    },
  });
  assert.equal(authPreflight.status, 204);
  assert.equal(authPreflight.headers.get("www-authenticate"), null);
  assert.equal(authPreflight.headers.get("access-control-allow-headers"), "authorization");
  assert.equal(await authPreflight.text(), "");

  for (const messages of [[], ["hello", "日本語", 'quote"', "line\nbreak", "\\path", "😀"]]) {
    const response = await worker.fetch("http://example.test/json/echo", {
      method: "POST",
      headers: { "Content-Type": "application/json", Origin: corsOrigin },
      body: JSON.stringify(messages),
    });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get("content-type"), "application/json; charset=utf-8");
    assert.equal(response.headers.get("access-control-allow-origin"), corsOrigin);
    assert.deepEqual(await response.json(), messages);
  }
  for (const body of ["", "null", "{}", "[broken]", '["hello",{}]', '["hello"] trailing']) {
    const response = await worker.fetch("http://example.test/json/echo", {
      method: "POST",
      headers: { "Content-Type": "application/json", Origin: corsOrigin },
      body,
    });
    assert.equal(response.status, 400, body);
    assert.equal(response.headers.get("access-control-allow-origin"), corsOrigin);
    assert.equal(await response.text(), "");
  }

  const concurrent = await Promise.all(
    Array.from({ length: 16 }, (_, index) =>
      worker.fetch(`http://example.test/hello/${index}`).then((response) => response.text()),
    ),
  );
  assert.deepEqual(
    concurrent,
    Array.from({ length: 16 }, (_, index) => `Hello, ${index}!`),
  );
  console.log(
    `Workers ${target} integration passed: D1 CRUD, paths, typed inputs, headers, content negotiation, CORS, Bearer challenges, asynchronous and concurrent handlers`,
  );
} finally {
  clearTimeout(startupTimer);
  server.kill("SIGTERM");
  const shutdownTimer = setTimeout(() => server.kill("SIGKILL"), 5_000);
  try {
    await server.exited;
    await Promise.all(captures);
  } finally {
    clearTimeout(shutdownTimer);
    await rm(persistence, { recursive: true, force: true });
  }
}

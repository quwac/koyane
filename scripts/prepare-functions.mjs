import { cp, mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const target = process.argv[2];
if (target !== "js" && target !== "wasm") throw new Error("Expected js or wasm");
const root = fileURLToPath(new URL("../", import.meta.url));
const tooling = JSON.parse(await readFile(path.join(root, "package.json"), "utf8"));
const platform = target === "js" ? "js" : "wasmJs";
const variant = target === "js" ? "kotlin" : "optimized";
const destinations = new Map();
for (const [host, example] of [
  ["vercel", "hello-fetch-functions"],
  ["supabase", "hello-fetch-functions"],
  ["cloud-run", "hello-cloud-run-functions"],
  ["lambda", "hello-lambda"],
]) {
  const destination = path.join(root, `examples/${example}/build/${host}-${target}`);
  const source = path.join(
    root,
    `examples/${example}/build/compileSync/${platform}/main/productionExecutable/${variant}`,
  );
  await rm(destination, { recursive: true, force: true });
  const kotlin = host === "supabase" ? "supabase/functions/hello/kotlin" : "kotlin";
  await mkdir(path.join(destination, kotlin), { recursive: true });
  for (const name of await readdir(source)) {
    if (name.endsWith(".mjs") || name.endsWith(".wasm")) {
      await cp(path.join(source, name), path.join(destination, kotlin, name));
    }
  }
  destinations.set(host, destination);
}
async function write(host, name, content) {
  const destination = path.join(destinations.get(host), name);
  await mkdir(path.dirname(destination), { recursive: true });
  await writeFile(destination, content);
}
const fetchModule = "koyane-examples-hello-fetch-functions";
await write(
  "vercel",
  "api/index.mjs",
  `import { fetch } from "../kotlin/${fetchModule}.mjs";\nexport default { fetch };\n`,
);
await write(
  "vercel",
  "package.json",
  JSON.stringify({ private: true, type: "module", engines: { node: "24.x" } }, null, 2) + "\n",
);
await write(
  "vercel",
  "vercel.json",
  JSON.stringify(
    {
      version: 2,
      functions: { "api/index.mjs": { includeFiles: "kotlin/**" } },
      rewrites: [{ source: "/(.*)", destination: "/api" }],
    },
    null,
    2,
  ) + "\n",
);
// Supabase packages the Wasm bytes as static files. Its user worker reads these
// locally; Kotlin's default browser loader would try to fetch a file: URL.
const supabaseImport = target === "js" ? `${fetchModule}.mjs` : "bridge.mjs";
if (target === "wasm") {
  await write(
    "supabase",
    "supabase/functions/hello/kotlin/bridge.mjs",
    `
import { importObject, setWasmExports } from "./${fetchModule}.import-object.mjs";
const bytes = await Deno.readFile(new URL("./${fetchModule}.wasm", import.meta.url));
const { instance } = await WebAssembly.instantiate(bytes, importObject);
setWasmExports({ memory: importObject.intrinsics.memory });
instance.exports._start();
export const fetch = instance.exports.fetch;
`,
  );
}
await write(
  "supabase",
  "supabase/functions/hello/index.ts",
  `
import { fetch } from "./kotlin/${supabaseImport}";
Deno.serve((request: Request) => {
  // Hosted requests include /hello before the application's route.
  const url = new URL(request.url);
  url.pathname = url.pathname.replace(/^\\/hello(?=\\/|$)/, "") || "/";
  return fetch(new Request(url, request));
});
`,
);
await write(
  "supabase",
  "supabase/config.toml",
  `project_id = "koyane-hello"\n\n[functions.hello]\nverify_jwt = true\n` +
    (target === "wasm" ? 'static_files = ["./functions/hello/kotlin/*.wasm"]\n' : ""),
);
await write(
  "lambda",
  "index.mjs",
  'export { handler } from "./kotlin/koyane-examples-hello-lambda.mjs";\n',
);
await write(
  "lambda",
  "package.json",
  '{ "private": true, "type": "module", "engines": { "node": ">=22" } }\n',
);
await write(
  "cloud-run",
  "index.js",
  `
import { http } from "@google-cloud/functions-framework";
import { handle } from "./kotlin/koyane-examples-hello-cloud-run-functions.mjs";
http("hello", (request, response) => handle(request, response, request.rawBody?.toString("utf8") ?? ""));
`,
);
await write(
  "cloud-run",
  "package.json",
  JSON.stringify(
    {
      private: true,
      type: "module",
      main: "index.js",
      engines: { node: ">=22" },
      scripts: { start: "functions-framework --target=hello" },
      dependencies: {
        "@google-cloud/functions-framework":
          tooling.devDependencies["@google-cloud/functions-framework"],
      },
    },
    null,
    2,
  ) + "\n",
);
for (const [host, destination] of destinations) console.log(`${host} ${target}: ${destination}`);

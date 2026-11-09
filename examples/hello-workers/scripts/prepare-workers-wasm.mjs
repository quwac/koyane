import { mkdir, readFile, writeFile, copyFile, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const example = fileURLToPath(new URL("../", import.meta.url));
const base = "koyane-examples-hello-workers";
const source = path.join(example, "build/compileSync/wasmJs/main/productionExecutable/optimized");
const target = path.join(example, "build/worker-wasm");
await rm(target, { recursive: true, force: true });
await mkdir(target, { recursive: true });

await copyFile(path.join(source, `${base}.wasm`), path.join(target, `${base}.wasm`));
await copyFile(
  path.join(source, `${base}.import-object.mjs`),
  path.join(target, `${base}.import-object.mjs`),
);

// Kotlin's JS string fallback compiles tiny Wasm helpers at runtime. Workers
// requires all Wasm to be supplied as static modules, including these helpers.
const builtins = await readFile(path.join(source, `${base}.js-builtins.mjs`), "utf8");
const helper =
  /const (moduleFromCharCodeArray|moduleIntoCharCode) = new WebAssembly\.Module\(new Uint8Array\(\[([\s\S]*?)\]\)\);/g;
const matches = [...builtins.matchAll(helper)];
if (
  matches.length < 1 ||
  matches.length > 2 ||
  new Set(matches.map((match) => match[1])).size !== matches.length
) {
  throw new Error("Kotlin JS string glue changed; review the Workers bridge");
}
for (const [, name, encoded] of matches) {
  const bytes = encoded
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)
    .map(Number);
  if (bytes.some((byte) => !Number.isInteger(byte) || byte < 0 || byte > 255)) {
    throw new Error("Invalid Kotlin JS string helper bytes");
  }
  await writeFile(path.join(target, `${name}.wasm`), new Uint8Array(bytes));
}
await writeFile(
  path.join(target, `${base}.js-builtins.mjs`),
  builtins.replace(helper, (_, name) => `import ${name} from "./${name}.wasm";`),
);
await writeFile(
  path.join(target, "bridge.mjs"),
  `
import wasm from "./${base}.wasm";
import { importObject, setWasmExports } from "./${base}.import-object.mjs";
const instance = new WebAssembly.Instance(wasm, importObject);
setWasmExports({ memory: importObject.intrinsics.memory });
const { fetch, _start } = instance.exports;
_start();
export { fetch };
`,
);
for (const filename of ["bridge.mjs", `${base}.import-object.mjs`, `${base}.js-builtins.mjs`]) {
  const content = await readFile(path.join(target, filename), "utf8");
  if (
    /new WebAssembly\.Module\s*\(|WebAssembly\.(?:compile|compileStreaming|instantiateStreaming)\s*\(/.test(
      content,
    )
  ) {
    throw new Error(`Dynamic Wasm compilation in ${filename}; review the Workers bridge`);
  }
}
console.log("Kotlin/Wasm modules prepared in examples/hello-workers/build/worker-wasm");

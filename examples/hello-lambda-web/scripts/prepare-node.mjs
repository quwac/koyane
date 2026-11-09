import { cp, mkdir, readdir, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const target = process.argv[2];
if (target !== "js" && target !== "wasm") throw new Error("Expected js or wasm");
const example = fileURLToPath(new URL("../", import.meta.url));
const platform = target === "js" ? "js" : "wasmJs";
const variant = target === "js" ? "kotlin" : "optimized";
const source = path.join(
  example,
  `build/compileSync/${platform}/main/productionExecutable/${variant}`,
);
const destination = path.join(example, `build/lambda-web-${target}`);
await rm(destination, { recursive: true, force: true });
await mkdir(path.join(destination, "kotlin"), { recursive: true });
for (const name of await readdir(source)) {
  if (name.endsWith(".mjs") || name.endsWith(".wasm")) {
    await cp(path.join(source, name), path.join(destination, "kotlin", name));
  }
}
await writeFile(
  path.join(destination, "index.js"),
  'import "./kotlin/koyane-examples-hello-lambda-web.mjs";\n',
);
await writeFile(
  path.join(destination, "package.json"),
  JSON.stringify({ private: true, type: "module", engines: { node: ">=24" } }, null, 2) + "\n",
);
console.log(`Lambda Web Functions ${target} package prepared in ${destination}`);

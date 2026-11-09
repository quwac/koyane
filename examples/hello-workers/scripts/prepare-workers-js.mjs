import { cp, mkdir, readdir, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const example = fileURLToPath(new URL("../", import.meta.url));
const source = path.join(example, "build/compileSync/js/main/productionExecutable/kotlin");
const target = path.join(example, "build/worker-js");
await rm(target, { recursive: true, force: true });
await mkdir(path.join(target, "kotlin"), { recursive: true });

for (const filename of await readdir(source)) {
  if (filename.endsWith(".mjs")) {
    await cp(path.join(source, filename), path.join(target, "kotlin", filename));
  }
}

await writeFile(
  path.join(target, "bridge.mjs"),
  `
import "./kotlin/koyane-examples-hello-workers.mjs";
export { fetch } from "./kotlin/koyane-koyane-workers.mjs";
`,
);

console.log("Kotlin/JS modules prepared in examples/hello-workers/build/worker-js");

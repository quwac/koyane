import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { access, cp, mkdir, mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const repositoryRoot = fileURLToPath(new URL("../", import.meta.url));
const reports = path.join(repositoryRoot, "build/reports/bom-tests");
await mkdir(reports, { recursive: true });
const work = await mkdtemp(path.join(reports, "run-"));
const repository = path.join(work, "repository");
const consumer = path.join(work, "consumer");
const root = path.join(work, "source");
const selected = spawnSync(
  "git",
  ["ls-files", "--cached", "--others", "--exclude-standard", "-z"],
  {
    cwd: repositoryRoot,
    encoding: "utf8",
    timeout: 30_000,
  },
);
if (selected.error) throw selected.error;
assert.equal(selected.status, 0, selected.stderr);
for (const file of new Set(selected.stdout.split("\0").filter(Boolean))) {
  const destination = path.join(root, file);
  await mkdir(path.dirname(destination), { recursive: true });
  await cp(path.join(repositoryRoot, file), destination);
}

async function gradle(label, args) {
  const result = spawnSync("./gradlew", [...args, "--console=plain"], {
    cwd: root,
    encoding: "utf8",
    timeout: 180_000,
  });
  const output = result.stdout + result.stderr;
  await writeFile(path.join(work, `${label}.log`), output);
  if (result.error) throw result.error;
  assert.equal(result.status, 0, `${label} failed:\n${output}`);
  console.log(`BOM ${label}: passed`);
}

await gradle("publish", ["publishToMavenLocal", `-Dmaven.repo.local=${repository}`]);
const pom = await readFile(
  path.join(root, "koyane-bom/build/publications/maven/pom-default.xml"),
  "utf8",
);
const xmlValue = (xml, tag) => {
  const match = new RegExp(`<${tag}>([^<]+)</${tag}>`).exec(xml);
  assert.ok(match, `Missing ${tag} in generated POM`);
  return match[1];
};
const group = xmlValue(pom, "groupId");
const version = xmlValue(pom, "version");
assert.equal(xmlValue(pom, "packaging"), "pom");
const managed = /<dependencyManagement>([\s\S]+)<\/dependencyManagement>/.exec(pom);
assert.ok(managed, "Missing Maven dependencyManagement");
assert.doesNotMatch(pom.replace(managed[0], ""), /<dependencies>/);
const modules = [];
for (const entry of await readdir(root, { withFileTypes: true })) {
  if (!entry.isDirectory() || !entry.name.startsWith("koyane-") || entry.name === "koyane-bom")
    continue;
  try {
    await access(path.join(root, entry.name, "build.gradle.kts"));
  } catch {
    continue;
  }
  modules.push(entry.name);
}
modules.sort();
const constraints = [...managed[1].matchAll(/<dependency>([\s\S]+?)<\/dependency>/g)].map(
  ([, xml]) => {
    assert.equal(xmlValue(xml, "groupId"), group);
    assert.equal(xmlValue(xml, "version"), version);
    return xmlValue(xml, "artifactId");
  },
);
assert.deepEqual(constraints.toSorted(), modules);
const published = await readdir(path.join(repository, group.replaceAll(".", "/")));
assert.deepEqual(
  published.toSorted(),
  ["koyane-bom", ...modules.flatMap((name) => [name, `${name}-js`, `${name}-wasm-js`])].toSorted(),
);

const build = await readFile(path.join(root, "build.gradle.kts"), "utf8");
const kotlinVersion = /kotlin\("multiplatform"\) version "([^"]+)"/.exec(build)[1];
await mkdir(path.join(consumer, "src/commonMain/kotlin"), { recursive: true });
await mkdir(path.join(consumer, "gradle"), { recursive: true });
const verification = (
  await readFile(path.join(root, "gradle/verification-metadata.xml"), "utf8")
).replace(
  "</configuration>",
  `<trusted-artifacts><trust group="${group}" reason="Locally generated test publications"/></trusted-artifacts></configuration>`,
);
await writeFile(path.join(consumer, "gradle/verification-metadata.xml"), verification);
await writeFile(
  path.join(consumer, "settings.gradle.kts"),
  `
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement {
    repositories {
        maven { url = uri(${JSON.stringify(pathToFileURL(repository).href)}); content { includeGroup("${group}") } }
        mavenCentral { content { excludeGroup("${group}") } }
    }
}
rootProject.name = "bom-consumer"
`,
);
await writeFile(
  path.join(consumer, "build.gradle.kts"),
  `
@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
plugins { kotlin("multiplatform") version "${kotlinVersion}" }
val allModules = providers.gradleProperty("allModules").isPresent
val selected = if (allModules) listOf(${modules.map((name) => JSON.stringify(name)).join(", ")}) else listOf("koyane-http")
kotlin {
    js { nodejs() }
    wasmJs { nodejs() }
    sourceSets.commonMain.dependencies {
        implementation(project.dependencies.platform("${group}:koyane-bom:${version}"))
        selected.forEach { implementation("${group}:" + it) }
    }
}
tasks.register("verifyConsumption") {
    dependsOn("compileKotlinJs", "compileKotlinWasmJs")
    doLast {
        kotlin.targets.filter { it.name != "metadata" }.forEach { target ->
            val configuration = configurations.getByName(target.compilations.getByName("main").compileDependencyConfigurationName)
            val resolved = configuration.incoming.resolutionResult.allComponents.mapNotNull { it.moduleVersion }.filter { it.group == "${group}" }
            check(resolved.all { it.version == "${version}" }) { "Unaligned versions: " + resolved }
            val expected = if (allModules) selected.toSet() else setOf("koyane-core", "koyane-http")
            val suffix = if (target.name == "js") "-js" else "-wasm-js"
            check(resolved.map { it.name }.toSet() == expected + expected.map { it + suffix } + "koyane-bom") {
                "Unexpected modules for " + target.name + ": " + resolved
            }
        }
    }
}
`,
);
await writeFile(
  path.join(consumer, "src/commonMain/kotlin/Application.kt"),
  `
import dev.quwac.koyane.core.koyane
import dev.quwac.koyane.http.Http
val application = koyane { install(Http) { get("/hello") { text("Hello") } } }
`,
);
await gradle("minimal-consumer", [
  "-p",
  consumer,
  "verifyConsumption",
  "--dependency-verification=strict",
]);
await gradle("all-modules-consumer", [
  "-p",
  consumer,
  "verifyConsumption",
  "-PallModules",
  "--dependency-verification=strict",
]);
console.log(
  `BOM verified: ${modules.length} modules, Maven POM, JS/Wasm versionless consumption; reports: ${work}`,
);

import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmod, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(new URL("release.sh", import.meta.url));
const work = await mkdtemp(path.join(os.tmpdir(), "koyane-release-"));
const environment = { ...process.env, GIT_CONFIG_GLOBAL: "/dev/null", GIT_CONFIG_NOSYSTEM: "1" };
let fixtures = 0;
let assertions = 0;

function command(cwd, executable, args, env = environment) {
  const result = spawnSync(executable, args, { cwd, env, encoding: "utf8", timeout: 30_000 });
  if (result.error) throw result.error;
  return result;
}

function git(cwd, ...args) {
  const result = command(cwd, "git", args);
  assert.equal(result.status, 0, result.stderr);
  return result.stdout.trim();
}

async function fixture() {
  const directory = path.join(work, String(fixtures++));
  await mkdir(directory);
  const remote = path.join(directory, "remote.git");
  const checkout = path.join(directory, "checkout");
  const bin = path.join(directory, "bin");
  const log = path.join(directory, "gh.log");
  git(directory, "init", "--bare", "--initial-branch=main", remote);
  git(directory, "clone", remote, checkout);
  git(checkout, "config", "user.name", "Release test");
  git(checkout, "config", "user.email", "release@example.invalid");
  git(checkout, "commit", "--allow-empty", "-m", "Initial commit");
  git(checkout, "push", "--set-upstream", "origin", "main");
  git(checkout, "switch", "-c", "feature/release-test");
  await mkdir(bin);
  await writeFile(log, "");
  await writeFile(
    path.join(bin, "gh"),
    '#!/usr/bin/env bash\nprintf "%s\\n" "$*" >> "$TEST_GH_LOG"\nprintf "%s\\n" "$TEST_VERIFY_RESULT"\nexit "$TEST_GH_EXIT_CODE"\n',
  );
  await chmod(path.join(bin, "gh"), 0o755);
  return { directory, remote, checkout, bin, log };
}

function release(repository, args, verification = "completed/success", ghExitCode = "0") {
  return command(repository.checkout, "bash", [script, ...args], {
    ...environment,
    PATH: `${repository.bin}:${process.env.PATH}`,
    TEST_GH_LOG: repository.log,
    TEST_VERIFY_RESULT: verification,
    TEST_GH_EXIT_CODE: ghExitCode,
  });
}

function rejected(repository, args, message, verification, ghExitCode) {
  const result = release(repository, args, verification, ghExitCode);
  assert.notEqual(result.status, 0, result.stdout);
  if (message) assert.match(result.stderr, message);
  assert.equal(git(repository.checkout, "tag", "--list"), "");
  assert.equal(git(repository.remote, "tag", "--list"), "");
  assertions++;
}

try {
  const invalid = await fixture();
  for (const args of [[], [""], ["1.2"], ["01.2.3"], ["1.2.3-rc.1"], ["1.2.3", "extra"]]) {
    rejected(invalid, args, /Usage:|Expected version/);
  }
  assert.equal(await readFile(invalid.log, "utf8"), "");

  const dirty = await fixture();
  await writeFile(path.join(dirty.checkout, "untracked.txt"), "uncommitted change");
  rejected(dirty, ["1.2.3"], /worktree must be clean/);

  const ahead = await fixture();
  git(ahead.checkout, "commit", "--allow-empty", "-m", "Unmerged change");
  rejected(ahead, ["1.2.3"], /HEAD must match origin\/main/);

  const behind = await fixture();
  git(behind.checkout, "switch", "main");
  git(behind.checkout, "commit", "--allow-empty", "-m", "New main commit");
  git(behind.checkout, "push", "origin", "main");
  git(behind.checkout, "switch", "feature/release-test");
  rejected(behind, ["1.2.3"], /HEAD must match origin\/main/);

  const unverified = await fixture();
  for (const status of ["in_progress/", "completed/failure", "null"]) {
    rejected(unverified, ["1.2.3"], /Verify workflow must succeed/, status);
  }
  rejected(unverified, ["1.2.3"], null, "", "1");

  for (const version of ["v0.1.0", "1.2.3"]) {
    const ready = await fixture();
    const result = release(ready, [version]);
    assert.equal(result.status, 0, result.stderr);
    const tag = `v${version.replace(/^v/, "")}`;
    assert.equal(git(ready.checkout, "cat-file", "-t", `refs/tags/${tag}`), "tag");
    assert.equal(git(ready.checkout, "rev-parse", tag), git(ready.remote, "rev-parse", tag));
    assert.equal(
      git(ready.remote, "for-each-ref", "--format=%(refname)", "refs/heads"),
      "refs/heads/main",
    );
    assert.match(
      await readFile(ready.log, "utf8"),
      /--workflow ci.yml --branch main --event push --commit [a-f0-9]{40}/,
    );
    assertions++;

    git(ready.checkout, "tag", "--delete", tag);
    const duplicate = release(ready, [version]);
    assert.notEqual(duplicate.status, 0);
    assert.match(duplicate.stderr, /Tag already exists/);
    assert.equal(git(ready.checkout, "rev-parse", tag), git(ready.remote, "rev-parse", tag));
    assertions++;
  }
  console.log(
    `Release guards passed: ${assertions} cases using temporary local Git remotes; no external tag pushes.`,
  );
} finally {
  await rm(work, { recursive: true, force: true });
}

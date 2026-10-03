import { expect, test } from "bun:test";
import { mkdtemp, rm } from "fs/promises";
import { tmpdir } from "os";
import { join } from "path";
import { changedPaths, selectTasks } from "./ktlint-changed";

const modules = ["app", "core/model", "core/database"];

test("selects and deduplicates Android and JVM source sets, including Java directories", () => {
  expect(selectTasks([
    "app/src/main/java/Foo.kt", "app/src/main/kotlin/Bar.kt",
    "app/src/androidTest/kotlin/Test.kt", "app/src/debug/kotlin/Debug.kt",
    "core/model/src/test/kotlin/Test.kt", "README.md",
  ], modules)).toEqual([
    ":app:ktlintAndroidTestSourceSetCheck", ":app:ktlintDebugSourceSetCheck",
    ":app:ktlintMainSourceSetCheck", ":core:model:ktlintTestSourceSetCheck",
  ]);
});

test("configuration changes always use full checks", () => {
  for (const path of [".editorconfig", "app/.editorconfig", "app/build.gradle.kts",
    "gradle/libs.versions.toml", "gradle.properties", "gradlew",
    "app/config/ktlint/baseline.xml", "buildSrc/src/main/kotlin/Rules.kt"]) {
    expect(selectTasks(["app/src/main/kotlin/Foo.kt", path], modules)).toEqual(["ktlintCheck"]);
  }
});

test("unrecognized Kotlin locations fall back to module or full checks", () => {
  expect(selectTasks(["core/database/custom/Foo.kt"], modules)).toEqual([":core:database:ktlintCheck"]);
  expect(selectTasks(["settings-helper.kts"], modules)).toEqual(["ktlintCheck"]);
  expect(selectTasks(["docs/review.md"], modules)).toEqual([]);
});

test("Git selection includes staged, unstaged, deleted, renamed and untracked paths with spaces", async () => {
  const root = await mkdtemp(join(tmpdir(), "ktlint-selection-"));
  const git = async (...args: string[]) => {
    const result = Bun.spawn(["git", ...args], { cwd: root, stdout: "pipe", stderr: "pipe" });
    if (await result.exited !== 0) throw new Error(await new Response(result.stderr).text());
  };
  try {
    await git("init");
    for (const path of ["staged.kt", "unstaged.kt", "deleted.kt", "old name.kt"]) {
      await Bun.write(join(root, path), "original\n");
    }
    await git("add", ".");
    // No commit or interaction with the user's identity hooks is needed.
    const tree = Bun.spawn(["git", "write-tree"], { cwd: root, stdout: "pipe" });
    const base = (await new Response(tree.stdout).text()).trim();
    await tree.exited;
    await Bun.write(join(root, "staged.kt"), "staged\n");
    await git("add", "staged.kt");
    await Bun.write(join(root, "unstaged.kt"), "unstaged\n");
    await rm(join(root, "deleted.kt"));
    await git("mv", "old name.kt", "new name.kt");
    await Bun.write(join(root, "new file.kt"), "new\n");
    expect((await changedPaths(root, base)).sort()).toEqual([
      "deleted.kt", "new file.kt", "new name.kt", "old name.kt", "staged.kt", "unstaged.kt",
    ]);
    await expect(changedPaths(root, "missing-ref")).rejects.toThrow();
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

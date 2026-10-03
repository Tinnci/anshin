import { dirname, resolve } from "path";

// Select existing plugin tasks, so rules, reporters and baselines stay identical to CI.
export function selectTasks(paths: string[], modules: string[]): string[] {
  const tasks = new Set<string>();
  const orderedModules = [...modules].sort((a, b) => b.length - a.length);
  for (const path of paths) {
    if (
      /(^|\/)\.editorconfig$/.test(path) ||
      /(^|\/)([^/]*\.gradle(?:\.kts)?|gradle\.properties|libs\.versions\.toml|baseline\.xml)$/.test(path) ||
      path.startsWith("gradle/") || path.startsWith("buildSrc/") ||
      path.startsWith("build-logic/") || /^gradlew(?:\.bat)?$/.test(path)
    ) return ["ktlintCheck"];
    if (!/\.(kt|kts)$/.test(path)) continue;
    const module = orderedModules.find((candidate) => path.startsWith(`${candidate}/`));
    if (!module) return ["ktlintCheck"];
    const relative = path.slice(module.length + 1);
    const sourceSet = /^src\/([^/]+)\/(?:java|kotlin)\//.test(relative)
      ? relative.split("/")[1]
      : undefined;
    const prefix = `:${module.replaceAll("/", ":")}:`;
    if (!sourceSet) tasks.add(`${prefix}ktlintCheck`);
    else tasks.add(`${prefix}ktlint${sourceSet[0].toUpperCase()}${sourceSet.slice(1)}SourceSetCheck`);
  }
  return [...tasks].sort();
}

export async function changedPaths(root: string, base = "HEAD"): Promise<string[]> {
  const git = async (args: string[]) => {
    const result = Bun.spawn(["git", ...args], { cwd: root, stdout: "pipe", stderr: "pipe" });
    const [output, error, code] = await Promise.all([
      new Response(result.stdout).text(), new Response(result.stderr).text(), result.exited,
    ]);
    if (code !== 0) throw new Error(error.trim() || `git exited with ${code}`);
    return output.split("\0").filter(Boolean);
  };
  const [tracked, untracked] = await Promise.all([
    git(["diff", "--no-renames", "--name-only", "-z", base, "--"]),
    git(["ls-files", "--others", "--exclude-standard", "-z"]),
  ]);
  return [...new Set([...tracked, ...untracked])];
}

if (import.meta.main) {
  try {
    const root = resolve(dirname(import.meta.path), "..");
    const args = process.argv.slice(2);
    let base = "HEAD";
    let dryRun = false;
    let files: string[] | undefined;
    let gradleArgs: string[] = [];
    for (let i = 0; i < args.length; i++) {
      if (args[i] === "--") { gradleArgs = args.slice(i + 1); break; }
      if (args[i] === "--dry-run") dryRun = true;
      else if (args[i] === "--base" && args[i + 1]) base = args[++i];
      else if (args[i] === "--files") {
        files = [];
        while (args[i + 1] && !args[i + 1].startsWith("--")) files.push(args[++i]);
        if (!files.length) throw new Error("--files needs repository-relative paths");
      } else throw new Error(`Unknown or incomplete option: ${args[i]}`);
    }
    if (files?.some((path) => path.startsWith("/") || path.split("/").includes(".."))) {
      throw new Error("--files accepts repository-relative paths only");
    }
    const settings = await Bun.file(resolve(root, "settings.gradle.kts")).text();
    const modules = [...settings.matchAll(/include\("(:[^"\s]+)"\)/g)]
      .map((match) => match[1].slice(1).replaceAll(":", "/"));
    if (!modules.length) throw new Error("Cannot discover modules in settings.gradle.kts");
    const paths = files ?? await changedPaths(root, base);
    const tasks = selectTasks(paths, modules);
    if (!tasks.length) console.log("No Kotlin or lint configuration changes; nothing to check.");
    else {
      console.log(`Selected tasks: ${tasks.join(" ")}`);
      if (!dryRun) {
        const child = Bun.spawn(["./gradlew", ...tasks, ...gradleArgs], {
          cwd: root, stdin: "inherit", stdout: "inherit", stderr: "inherit",
        });
        process.exitCode = await child.exited;
      }
    }
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  }
}

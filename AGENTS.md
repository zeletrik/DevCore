# AGENTS.md

DevCore is a self-hosted developer toolbox: small tools for JSON, encoding, JWT, hashes, time, networks and more, in one
Kotlin, Spring Boot and Vaadin Flow app. Every page is rendered on the server, and each tool's logic is plain Kotlin.
This file is the working guide for coding agents. [`CONTRIBUTING.md`](CONTRIBUTING.md) is the short human version, and
the tests enforce most of what follows.

## Commands

| Command                                     | What it does                                                      |
|---------------------------------------------|-------------------------------------------------------------------|
| `./gradlew test detekt`                     | What CI runs. It must pass before a change is done.               |
| `./gradlew test --tests '*Base64CodecTest'` | One test class.                                                   |
| `./gradlew bootRun`                         | Starts DevCore at <http://localhost:8080>.                        |
| `./gradlew -t classes`                      | In a second terminal next to `bootRun`: reloads on every change.  |
## Commands

| Command                                     | What it does                                                      |
|---------------------------------------------|-------------------------------------------------------------------|
| `./gradlew test detekt`                     | What CI runs. It must pass before a change is done.               |
| `./gradlew test --tests '*Base64CodecTest'` | One test class.                                                   |
| `./gradlew bootRun`                         | Starts DevCore at <http://localhost:8080>.                        |
| `./gradlew -t classes`                      | In a second terminal next to `bootRun`: reloads on every change.  |

Gradle uses the JDK 25 on `JAVA_HOME` and doesn't download one. The build needs no Node.js. If something already listens
on port 8080, leave it running and start yours elsewhere: `SERVER_PORT=18080 ./gradlew bootRun`.

## Stack

- Kotlin on JDK 25, Spring Boot 4, Vaadin 25 (Flow: server-side components, no frontend code to write).
- Jackson 3: its packages are `tools.jackson.*`, not `com.fasterxml.jackson.*`.
- Every version lives in `gradle/libs.versions.toml`. Libraries without one there are managed by the Spring Boot and
  Vaadin BOMs. Never write a version into `build.gradle.kts`.
- Detekt with its default rules; `detekt.yml` is empty on purpose.

## Layout

```
src/main/kotlin/eu/zeletrik/devcore/
  shell/                 the app around the tools
    Tool.kt              the tool registry: navigation, Home, search and page titles all derive from it
    Category.kt          sidebar groups and Home sections
    ToolView.kt          ToolView, TextToolView (recomputes as you type), Mode and modeGroup
    GeneratorView.kt     tools that produce output on demand
    Fields.kt            input fields and their validation helpers
    Controls.kt          radio groups, selects and option rows
    OutputField.kt       read-only output with a copy button
    CodeImageView.kt     image preview with PNG and SVG downloads
    OutputLimit.kt       the output cap and BoundedWriter
    DeepWork.kt          large-stack worker threads with a time limit
    DevCoreProperties.kt operator settings (devcore.*)
  tools/                 shared by tools: FieldError, secureRandom, lowercaseLinear and uppercaseLinear
  tools/<package>/       one package per tool: its logic (no Vaadin) and <Name>View.kt
src/test/kotlin/...      mirrors main; ArchitectureTest, RepositoryFilesTest and HttpSurfaceTest sit at the root
```

Never edit generated files: `src/main/frontend/generated/` and `src/main/bundles/`. semantic-release owns
`CHANGELOG.md` and the version in `gradle.properties`.

## House rules

These hold for every change.
- **Never log, store or persist user input**: not in logs, files, cookies or a logged exception message. Only the
  shell logs. A failure is one WARN line with the tool id and the exception class, and only `ToolView.kt` and
  `ToolCatalog.kt` create loggers.
- **No outbound calls, no host-name lookups, no temporary files, no `println`.** Parse addresses as text and write
  images to memory.
- **Never read passwords, secrets or keys from a URL.** A tool that takes them overrides `acceptsInputParam()` to
  return `false`.
- **Bounded work**, as the next section describes.
- **Only components in Vaadin's prebuilt bundle**: no add-ons, no npm packages.
- **Anything that may be a secret or an identifier** is random from `secureRandom` (`tools/Randomness.kt`).
- **American spelling** in code, comments and user-visible text.
- **A released tool id never changes.** It is the tool's URL and its name in cookies and configuration.

## Bounded work

Every tool must stay fast and small on its worst input by itself. Nothing outside it can stop a runaway computation.
- **Inputs.** `TextToolView` caps every field at 1,000,000 characters (`MAX_INPUT_CHARS`). When the work grows faster
  than the input, add a tighter cap that is checked first and has its own message. Markdown stops at 100,000
  characters, for example, and barcodes at 80.
- **Outputs.** `OutputField` refuses more than 5,000,000 characters (`MAX_OUTPUT_CHARS`). When an output can grow far
  past its input, write it into a `BoundedWriter`, so it fails while it is built and not after it fills the memory.
- **Recursion.** Parsers and formatters that recurse once per nesting level run inside `DeepWork.run { }`, which has a
  large stack and a 10-second limit. The limit only abandons the task; its thread runs on, so the task must be bounded
  anyway.
- **Case mapping.** On user text, call `lowercaseLinear()` and `uppercaseLinear()`, not `lowercase()` and
  `uppercase()`: the JDK's versions are quadratic on characters such as `İ` and `ß`. `equals(…, ignoreCase = true)`
  and short strings that are already capped are fine.
- **Check it.** Time the worst case at the cap: 1,000,000 characters of `İ`, deeply nested brackets, counts that
  multiply. If it takes more than about a second, the cap is too high.

## Errors

- Invalid input throws `IllegalArgumentException`, usually through `require(…) { "Message" }`. The view shows the
  message on the main input and clears the outputs. For another field, throw `FieldError("<key>", "Message")` with the
  key the field was registered under (`field(component, "<key>")`).
- Messages are for users: short and specific, with no final period and with thousands separators, as in
  `Input is too large (max 1,000,000 characters)`.
- Don't catch exceptions to hide them. Anything else reaches the shell's safety net, which shows
  `Couldn't process this input` and logs only the tool id and the exception class.

## Code style

- Detekt must report nothing. Fix the code; never add `@Suppress`, a baseline or rules in `detekt.yml`.
- Tabs, lines of at most 120 characters (`.editorconfig`), and trailing commas in multi-line lists.
- Write for a human reader. Every class and object has a KDoc, and comments say why, not what. Match the comment
  density of the code around you.
- A deliberate shortcut gets a `// NOTE:` comment that names its limit and what would replace it.
- Reuse the shell's helpers before writing new ones:
  - fields: `textArea`, `textLine`, `secretField`, `numberField` with `boundedInt`, and `boundedLong`;
  - options: `choice` for 2 to 4 choices, `enumSelect` for 5 or more, `optionsRow` and `modeGroup`;
  - outputs: `OutputField` and `CodeImageView`.
- An option enum carries a `val label: String`, and its labels are distinct and not blank.
- Add a dependency only for what a few lines of code can't do. It goes into `gradle/libs.versions.toml`, and its license
  appears on the About page without further work.

## Tests

- Test DevCore's code, not its frameworks or libraries. A test that would fail after a library upgrade, with no change
  on our side, tests the wrong thing.
- Each tool's logic has a unit test in the mirrored package, written with `kotlin.test` and no Spring. It covers the
  happy path, the edge cases, each limit at and just past its boundary, and every error message, asserted exactly.
- Views are tested once for all tools:
  - `ToolViewContractTest` covers the shell: caps, errors, the safety net, deep links and no input in the logs.
  - `ToolsSmokeTest` covers the deep-link and control conventions for every tool.

  A view gets a test of its own only when it has behavior of its own, and then one happy-path test in
  `ToolsSmokeTest`.
- `ArchitectureTest` enforces shared conventions only. Extend it for a new convention, not for one tool.
- Test names are backtick sentences that describe behavior. Names and comments don't refer to tickets or planning
  documents.

## Commits and documentation

- Don't commit or push unless you're asked to.
- Commit messages follow Conventional Commits: `fix:`, `feat:`, and `feat!:` or a `BREAKING CHANGE:` footer. They set
  the next version and become the release notes, so write them for users.
- `README.md` is for users. Its Tools table and tool count must match the registry, which tests check. Docs carry no
  change history; release notes come from the commits.
- A setting for operators is a `devcore.*` property in `DevCoreProperties`, with its default in `application.yaml`
  and a row in the README's Configuration table: the environment variable, its default and what it does.

## Adding a tool

Follow [`.claude/skills/add-tool/SKILL.md`](.claude/skills/add-tool/SKILL.md); in Claude Code, run it as `/add-tool`. It
takes a tool from the decisions to the registry entry, logic, view, tests and docs.

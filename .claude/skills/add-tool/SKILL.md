---
name: add-tool
description: Add a new tool to DevCore end to end, from the registry entry and the logic to the view, the tests and the README. Use when asked to add, build or scaffold a tool, or to implement a tool request.
argument-hint: "<what the tool should do>"
---

# Add a tool to DevCore

The tool to add: $ARGUMENTS

If that line is empty, ask what the tool should do before anything else. Read `AGENTS.md` first. Its house rules,
limits and test policy apply to every step below. Paths are relative to `src/main/kotlin/eu/zeletrik/devcore/` unless
they start at the repository root.

## 1. Decide the design

Settle these before writing code. Infer what you can from the request and the existing tools. Ask the user only about
choices that change behavior, such as the fit, the modes or which field a deep link fills.

| Decision    | Rule                                                                                                |
|-------------|-----------------------------------------------------------------------------------------------------|
| Fit         | No network, outbound call, host lookup, account or stored data. If it needs one, stop and say why.  |
| Overlap     | If an existing tool in `shell/Tool.kt` already covers the request, extend that tool instead.        |
| Id          | Lowercase and hyphenated (`number-base-converter`). It never changes after a release.               |
| Package     | A short lowercase word: the id without hyphens (`numberbase`, `jsonpath`, `urlparser`).             |
| Title       | In Title Case, as the sidebar shows it (`Number Base Converter`).                                   |
| Description | Starts with a verb, has no final period, and reads as one line on Home.                             |
| Category    | One entry of `shell/Category.kt`. A new category is a separate decision for the user.               |
| Icon        | A `VaadinIcon` that no entry in `shell/Tool.kt` uses yet.                                           |
| Keywords    | Lowercase search terms beyond the title: abbreviations, formats and synonyms.                       |
| Kind        | See below.                                                                                          |
| `?input=`   | Yes for plain text. No if the main input can be a password, secret or key. Generators have none.    |
| Modes       | See below.                                                                                          |
| Limits      | How do the work and the output grow on the worst input? If faster than the input, choose a cap now. |

For the **kind**, choose the view base class:
- `TextToolView` recomputes as the input changes. Most tools are this kind.
- `GeneratorView` makes output on demand from options only.
- `ToolView` is for anything else.

For the **modes**:
- Encode and decode use `Mode` and `modeGroup` from `shell/ToolView.kt`.
- Any other pair gets its own enum with a `fromParam`, like `HashMode` in `tools/hashing/PasswordHashingView.kt`.

## 2. Write the logic

Create `tools/<package>/<Name>.kt`. Use these as models:
- `tools/base64/Base64Codec.kt` is the simplest.
- `tools/jsonpath/JsonPathTester.kt` adds caps, a `BoundedWriter` and `FieldError`.
- `tools/uuid/Uuids.kt` is a generator that takes the clock as a parameter.

Rules for the logic:
- Write an `object` of pure functions: text and options in, text or a small data class out. Never import Vaadin here.
  `ArchitectureTest` checks it.
- Give the object a KDoc, and add one to each function whose behavior isn't obvious from its name.
- Invalid input throws `IllegalArgumentException` with the exact message a user sees: `require(…) { "…" }`. A
  problem in a field other than the main input throws `FieldError("<key>", "…")`.
- Check the caps first. Write any output that can grow past its input into a `BoundedWriter`, and call
  `lowercaseLinear()` or `uppercaseLinear()` on user text.
- Anything that may be a secret or an identifier is random from `secureRandom`.
- Take "now" and other changing inputs as parameters, so the result is deterministic in tests.

## 3. Unit-test the logic

Create `src/test/kotlin/eu/zeletrik/devcore/tools/<package>/<Name>Test.kt` with `kotlin.test` and no Spring. Model it
on `Base64CodecTest`. Cover:
- the happy path of every option and mode;
- edge cases: whitespace, Unicode (`é`, emoji, `İ`) and the largest valid values;
- every limit, at its boundary and one past it;
- every error message, asserted exactly;
- for a cap that exists because of time, the worst case at the cap within a time bound, as `CaseMappingTest` does.

Test only this tool's code, not the libraries it calls.

## 4. Write the view

Create `tools/<package>/<Name>View.kt`. A text tool looks like this:

```kotlin
/** <What the page lets you do, in one line.> */
@Route("<id>", layout = MainLayout::class)
@SpringComponent
@UIScope
class <Name>View : TextToolView<TextArea>(Tool.<ENTRY>, textArea("<Input label>")) {
	internal val option = choice("<Label>", <Option>.entries, <Option>.<DEFAULT>, <Option>::label) { recompute() }
	internal val output = OutputField("<Output label>")

	init {
		add(option, input, output)
	}

	override fun render(text: String) {
		output.value = <Name>.<function>(text, option.value)
	}

	override fun clearOutputs() = output.clear()
}
```

For the layout and the fields:
- The page shows options first, then inputs, then outputs.
- Components are `internal val` so that tests can reach them.
- Every option listener calls `recompute()`. In a generator it calls `generate()`.
- A second input field is `field(textLine("Key"), "key")`, and its key matches the key of a `FieldError`.
- When the main output doesn't need a field, pass `optional = true` and wrap the work that depends on it in
  `optional(component) { … }`.
- Override `isEmpty()` when a second field must also be filled before anything runs, as `JsonPathView` does.

For the processing:
- Recursive or deep work runs as `output.value = DeepWork.run { <Name>.<function>(text, value) }`. Read every component
  value before the lambda; the task never touches a component.
- With modes, override `applyMode(mode: String?)` so that `?mode=` selects one.
- With secrets in the main input, override `acceptsInputParam()` to return `false`.

For other kinds of output:
- A generator extends `GeneratorView(Tool.<ENTRY>)`. It puts `generateButton` and `generateError` in its
  `optionsRow`, implements `produce()`, and reads numbers with `boundedInt(field) ?: return`. See
  `tools/uuid/UuidGeneratorView.kt`.
- An image output uses `CodeImageView`, as `tools/qr/` does.

## 5. Register the tool

In `shell/Tool.kt`, import the view. Add the entry in the alphabetical order of the constant names:

```kotlin
	<ENTRY>(
		"<Title>",
		"<Verb …, no final period>",
		Category.<CATEGORY>,
		VaadinIcon.<ICON>,
		listOf("<keyword>", "<keyword>"),
		<Name>View::class.java,
	),
```

## 6. Update the shared tests

All of these files are in `src/test/kotlin/eu/zeletrik/devcore/`.
- `ArchitectureTest`, the `released ids` test: add `Tool.<ENTRY> to "<id>"`.
- `ArchitectureTest`, the `verbs` set: add the description's first word if the set doesn't have it.
- `tools/ToolsSmokeTest`, the `linked` set: add the id in alphabetical order if `?input=` fills the main input.
- `tools/ToolsSmokeTest`, the `modes` map: add `"<id>" to listOf("<default>", "<other>")` if the tool has modes.
- `tools/ToolsSmokeTest`: add one happy-path test only if the view has behavior of its own. For example, a mode that
  swaps fields, fields that appear and disappear, or options that change the call beyond passing their value on.

Don't add anything else. The contract tests for caps, errors, logs, option controls and secrets in links already go
through every entry of the registry.

## 7. Document the tool

- In the `README.md` Tools table, add one row. Keep the rows sorted by category, then title. The `input` column is
  `yes` or `—`, and the `mode` column lists the mode names with the default first, or `—`.
- Raise the tool count by one in all three places: the `README.md` introduction ("N tools in eight categories"),
  `Project.DESCRIPTION` in `shell/Project.kt`, and the `org.opencontainers.image.description` label in
  `.github/workflows/release.yml`. `ArchitectureTest` checks that the three agree.

## 8. Verify

1. Run `./gradlew test detekt`. All tests must pass, and Detekt must report no findings. Fix the code, never the checks.
2. Optional: run the app and open `/<id>` and `/<id>?input=…`. If port 8080 is taken, use
   `SERVER_PORT=18080 ./gradlew bootRun`.
3. Report the files you added and changed, the limits you chose and why. Suggest `feat: add <Title>` as the commit
   message. Don't commit unless the user asks.

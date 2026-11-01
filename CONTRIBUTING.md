# Contributing to DevCore

Thanks for helping. Bug reports and tool requests go through the issue forms; security problems go through
[`SECURITY.md`](SECURITY.md), never a public issue.

## Set up

- Install JDK 25 and point `JAVA_HOME` at it. Gradle builds with that JDK and doesn't detect or download one.
- `./gradlew bootRun` starts DevCore at <http://localhost:8080>. For live reload from a terminal, also run
  `./gradlew -t classes` in a second terminal.
- `./gradlew test detekt` runs the same checks as CI. Fix Detekt findings in the code; don't add `@Suppress`, a
  baseline or configuration.

## Commits and pull requests

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org): `fix:` for a bug fix, `feat:` for
a new tool or feature, and `feat!:` or a `BREAKING CHANGE:` footer for a breaking change. They decide the next version
and the release notes, so write them for users. Keep a pull request to one change, and fill in the template.

## House rules

These hold for every tool and every change:
- **Never log, store or persist user input.** Not in logs, files, cookies or exception messages that get logged. A
  failure is logged with the tool id and the exception class only.
- **No outbound calls.** DevCore makes no HTTP requests and fetches nothing at runtime: no OCSP, CRL or issuer URLs,
  no release notes, no fonts or scripts from a CDN.
- **No network lookups.** Never resolve a host name (for example with `InetAddress.getByName`); parse addresses as
  text.
- **Never read passwords, secrets or keys from a URL.** A tool that takes them overrides `acceptsInputParam()` to
  return `false`, so `?input=` is ignored.
- **No temporary files.** Images are written to memory, never through ImageIO's file cache.
- **Bounded work.** Inputs and outputs have size limits. Deeply recursive work (parsers, regexes) runs through
  `DeepWork`, whose threads have a large stack.
- **Only components in Vaadin's prebuilt bundle.** The build needs no Node.js; a component outside the bundle would
  change that.
- **American spelling** in code and user-visible text.

## Adding a tool

1. **Register it.** Add an entry to the `Tool` enum in `shell/Tool.kt` with:
   - a title;
   - a description that starts with a verb and has no final period;
   - a category;
   - an icon that no other tool uses;
   - search keywords;
   - its view class.
2. **Write the logic** in a new package under `tools/`, as plain Kotlin without Vaadin, and unit-test it.
3. **Write the view** in the same package:
   - Extend `TextToolView` for a tool that recomputes as you type, `GeneratorView` for one that produces output on
     demand, or `ToolView` otherwise.
   - Annotate it with `@Route("<id>", layout = MainLayout::class)`, `@SpringComponent` and `@UIScope`.
   - The id is lowercase and hyphenated, and it never changes after a release: it is the tool's URL and its name in
     cookies and configuration.
   - For modes, override `applyMode` so `?mode=` works.
4. **Test the view** with a browserless test (`SpringBrowserlessTest`), as the existing tools do.
5. **Document it:**
   - add a row to the Tools table in `README.md`;
   - update the tool count in `README.md`, `Project.DESCRIPTION` and the image description label in
     `.github/workflows/release.yml`.

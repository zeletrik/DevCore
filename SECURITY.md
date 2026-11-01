# Security policy

## Supported versions

Only the latest release receives security fixes. Check the version in DevCore's header or on its About page, and
update to the [latest release](https://github.com/zeletrik/devcore/releases/latest) before you report.

## Reporting a vulnerability

Report vulnerabilities privately through GitHub's private vulnerability reporting: open the repository's **Security**
tab and choose **Report a vulnerability**, or go to
<https://github.com/zeletrik/devcore/security/advisories/new>. Don't open a public issue, pull request or discussion
for a vulnerability.

Include:
- the DevCore version and how you run it (image, jar or source, and the reverse proxy in front of it, if any);
- the tool or URL involved and the steps to reproduce;
- what an attacker gains.

Leave out real secrets: use made-up tokens, keys and passwords in examples. The report stays private until a fixed
release is out.

## Scope

In scope:
- the DevCore application;
- the container image `ghcr.io/zeletrik/devcore`.

Not vulnerabilities:
- DevCore has no login, by design. Anyone who can reach it can use every tool, so run it on a private network or
  behind a proxy that authenticates (see the README).
- A secret that a user puts in a deep link ends up in browser history and proxy logs, as the README warns.
- Problems in a reverse proxy, identity provider or other software in front of DevCore. Report those to their
  maintainers.

# Releasing moka

Releases are tag-driven: push a `v`-prefixed tag and
[`.github/workflows/release.yml`](.github/workflows/release.yml) does the rest via
`sbt ci-release`. There is no manual publish step and no version number in the build —
sbt-dynver reads it from the tag.

## One-time setup

### 1. Central Portal namespace — done

`io.github.vimalaguti` is associated with the account (GitHub login associates
`io.github.<user>` automatically). It matches `ThisBuild / organization` in `build.sbt`.

### 2. PGP key — outstanding

```bash
gpg --gen-key                      # use a passphrase; keep it for PGP_PASSPHRASE
gpg --list-keys --keyid-format LONG # note the long key id
gpg --keyserver keyserver.ubuntu.com --send-keys <LONG_ID>
```

Export it for CI as a **single line** of base64:

```bash
gpg --armor --export-secret-keys <LONG_ID> | base64 -w0
```

### 3. GitHub secrets

Settings → Secrets and variables → Actions:

| Secret | Value |
| ------ | ----- |
| `PGP_SECRET` | the single-line base64 from step 2 |
| `PGP_PASSPHRASE` | the key's passphrase |
| `SONATYPE_USERNAME` | user token *name* from the Central Portal |
| `SONATYPE_PASSWORD` | user token *password* from the Central Portal |

Generate the token under Central Portal → Account → Generate User Token. Never commit these.

## Cutting a release

```bash
git switch master && git pull
git status --porcelain      # must be empty: a dirty tree publishes a SNAPSHOT instead
git tag -a v0.1.0 -m "moka 0.1.0"
git push origin v0.1.0
```

The workflow then runs `+testFull` and, only if it passes, `ci-release`.

**The promotion step is currently gated.** `release.yml` sets
`CI_SONATYPE_RELEASE: sonaUpload`, so `ci-release` signs the artifacts and uploads the bundle
but does **not** promote it. Finish by hand:

1. Open <https://central.sonatype.com/publishing/deployments>.
2. Check the deployment validated — this is where a malformed `PGP_SECRET` or a key the
   validator dislikes shows up.
3. Hit **Publish**.

Maven Central is immutable: a released version can never be replaced or withdrawn. Keep the
gate until a release has gone through once, then delete the `CI_SONATYPE_RELEASE` line and tags
will publish on their own.

Artifacts appear on <https://repo1.maven.org/maven2/io/github/vimalaguti/> within ten minutes
to a few hours of promotion.

Afterwards, regenerate the site from the tag so the install snippet shows the released
version (`mdocVariables` interpolates `@VERSION@` from `version.value`):

```bash
sbt docs/mdoc && (cd website && npm run build)
```

## Rules that will bite

- **The tag must start with `v`.** sbt-dynver ignores a bare `0.1.0` tag and falls back to
  `0.0.0+n-<sha>-SNAPSHOT`.
- **The tracked tree must be clean.** dynver marks a dirty tree as a snapshot, and
  `publishTo` follows it to `central-snapshots` rather than the staging bundle — so a dirty
  release quietly becomes a snapshot instead of failing.
- **Snapshots are not enabled** for the namespace, and the workflow only triggers on tags, so
  nothing is published between releases. Enable them per namespace on the Portal first if you
  ever want them.
- **Scala 3.9.0 is built but never published.** `macros / publish / skip` is true there, which
  `publishSigned` honours, so `+publishSigned` emits only `moka_2.13` and `moka_3`.
  Check with `sbt "++3.9.0; show macros/publish/skip"` (expects `true`).
- **`ci-release` no-ops without the secrets**, printing
  `No access to secret variables, doing nothing`. A green release run that published nothing
  means a missing secret, not a successful release — check the log.

## Signing locally

`sbt-pgp` shells out to `gpg --detach-sign --armor --use-agent`, which needs a way to ask for
the key's passphrase. From a non-interactive shell or a script it fails with

```
gpg: signing failed: Inappropriate ioctl for device
```

That is the missing passphrase prompt, not a bad key: `/usr/bin/pinentry` here is
`pinentry-curses`, which needs a controlling terminal to draw the prompt on.

`export GPG_TTY=$(tty)` only helps in a **real terminal**. Anywhere without a controlling tty —
a script, a CI step, an IDE or agent shell — `tty` prints `not a tty`, so the export stores that
literal string and pinentry fails exactly as before. Check with `tty` before trusting it.

The fix is to unlock the key once from a real terminal:

```bash
echo test | gpg --clearsign > /dev/null    # prompts once
```

gpg-agent then serves the passphrase to any later process — including a non-interactive sbt —
for `default-cache-ttl` of idle time up to `max-cache-ttl` (see `~/.gnupg/gpg-agent.conf`).
Confirm with `gpg-connect-agent 'keyinfo --list' /bye`: the field after `P` shows `1` when the
passphrase is cached and `-` when it is not.

For genuinely headless signing, add `allow-preset-passphrase` to `gpg-agent.conf`, reload the
agent, and inject it with
`/usr/lib/gnupg/gpg-preset-passphrase --preset <keygrip of the signing key>`
(`gpg --list-secret-keys --with-keygrip`).

CI needs none of this — sbt-ci-release imports `PGP_SECRET` and unlocks it with
`PGP_PASSPHRASE` non-interactively.

## Doing it by hand

Useful for inspecting a bundle before it goes public. Requires the PGP key locally.

```bash
sbt "+publishSigned; sonaUpload"   # stages the bundle, then uploads it to the Portal
                                   # "Publish" on the Portal releases it
sbt "+publishSigned; sonaRelease"  # or skip the review and release directly
sbt +publishLocal                  # or just install into ~/.ivy2/local for a smoke test
```

`localStaging`, `sonaUpload` and `sonaRelease` come from sbt itself, not from a plugin —
there is deliberately no sbt-sonatype in this build.

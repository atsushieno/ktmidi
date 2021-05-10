# Hacking ktmidi source code


## Project structure

ktmidi is composed of one single Kotlin Multiplatform (MPP) project.

Most of the features are implemented in `commonMain`, and some "platform"-specific parts such as `AndroidMidiAccess` or `JvmMidiAccess` are implemented in respective source directories (`androidMain`, `jvmMain`, etc.).

Tests are somewhat different - majority of the tests are still in `commonTest`, but some tests that depend on synchronized execution via `runBlocking` are not runnable with KotlinJS, so they are (so far) in `jvmTest`.


## Project development management

Bug reports are welcomed at [GitHub issues](https://github.com/atsushieno/ktmidi/issues). Any code change suggestions are expected as [Pull requests](https://github.com/atsushieno/ktmidi/pulls). Though they are not very strict, feel free to reach @atsushieno by other means (email, mastodon.cloud, Facebook, Twitter etc.) too.

Although when creating PRs please note that:

- by submitting PRs or code/data contributions in any other formats, you are supposed to agree to license under the MIT license
- we likely don't accept changes that break CI builds. For example we have iOS build disabled (as it does not build there).

Contributed code may be reformatted by @atsushieno or any possible maintainers.

We also use [GitHub Discussions](https://github.com/atsushieno/ktmidi/discussions) for any public discussions. Troubleshooting, casual feature requests, suggestions (and complaints), usage showcase, release plans etc.

Although at this state the developer is almost only one (@atsushieno) and the project will be driven without promising plans on Discussions.


## Continuous Integration

At this state, we use GitHub Actions to verify builds, and publish Maven packages to Maven Central via SonaType, as well as GitHub Packages (supplemental). It is maintained by @atsushieno. Pull requests are built as well, and release pipelines run by git tags (though it is manually released at Nexus Repository Manager at this state).

Builds are run on ubuntu-20.04 machine, and that brings in some limitations e.g. we don't really support platform native libraries even if they are needed.


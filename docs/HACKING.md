# Hacking ktmidi source code


## Project structure

The project is named as `ktmidi-project` in `settings.gradle`, and in this `ktmidi-project` project there are two modules:

- `ktmidi` - the core module
- `ktmidi-jvm-desktop` - contains ALSA implementation

`ktmidi` is composed of one single Kotlin Multiplatform (MPP) project.

Most of the features are implemented in `commonMain`, and some "platform"-specific parts such as `AndroidMidiAccess` or `JvmMidiAccess` are implemented in respective source directories (`androidMain`, `jvmMain`, etc.).

Tests are somewhat different - majority of the tests are still in `commonTest`, but some tests that depend on synchronized execution via `runBlocking` are not runnable with KotlinJS, so they are (so far) in `jvmTest`.


## Project development management

Bug reports are welcomed at [GitHub issues](https://github.com/atsushieno/ktmidi/issues). Any code change suggestions are expected as [Pull requests](https://github.com/atsushieno/ktmidi/pulls). Though they are not very strict, feel free to reach @atsushieno by other means (email, mastodon.cloud, Facebook, Twitter etc.) too.

Although when creating PRs please note that:

- by submitting PRs or code/data contributions in any other formats, you are supposed to agree to license your contributions under the MIT license
- we likely don't accept changes that break CI builds. For example we have iOS build disabled (as it does not build there).

We also use [GitHub Discussions](https://github.com/atsushieno/ktmidi/discussions) for any public discussions. Troubleshooting, casual feature requests, suggestions (and complaints), usage showcase, release plans etc.

Although at this state the developer is almost only one (@atsushieno) and the project will be driven without promising plans on Discussions.


## Code policy

Right now we don't have strict coding convention. But contributed code may be reformatted by @atsushieno or any possible maintainers.

We in general expect new features available on all platforms. But like virtual MIDI port support, features that have limited subset of supported platforms might be added with appropriate abstraction. (Platform-specific implementation does not conclift with this principle.)

We keep trying to minimize dependencies. It is for two reasons:

- foundation libraries like this library should not bring in too many dependencies to avoid possible conflicts with irrelevant libraries each other.
- extraneous dependencies potentially harm build stability and cross-platform portability.

Sample projects do not have these problems in general and therefore those principles do not apply, but CI builds still have to be kept solid.


## Continuous Integration

At this state, we use GitHub Actions to verify builds, and publish Maven packages to Maven Central via SonaType. It is maintained by @atsushieno. Pull requests are built as well, and release pipelines run by git tags (though it is manually released at Nexus Repository Manager).

Builds run on ubuntu-20.04 machine, and they bring in some limitations e.g. we don't really support platform native libraries even if they are needed. It is partly due to an issue in the build infrastructure: https://github.com/atsushieno/ktmidi/issues/13


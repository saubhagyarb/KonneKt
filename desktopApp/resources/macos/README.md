# MediaRemote adapter (macOS only)

This helper provides system-wide now-playing metadata and artwork on macOS 15.4+
(including browser players). DeskBuddy runs the upstream script under Apple's
`/usr/bin/perl` and falls back to its Music/Spotify Apple Events reader if the
adapter is missing or unavailable.

The BSD-3-Clause dependency is pinned to `ungive/mediaremote-adapter` v0.7.7,
commit `e3ff5021eb0875858bd05f48d2e9ba2e962d1cf6`. To rebuild its universal
`x86_64` + `arm64` files on a Mac with CMake installed:

```bash
git clone --depth 1 --branch v0.7.7 https://github.com/ungive/mediaremote-adapter.git /tmp/mra
cd /tmp/mra
git checkout e3ff5021eb0875858bd05f48d2e9ba2e962d1cf6
cmake -S . -B build
cmake --build build
codesign --force --sign - build/MediaRemoteAdapterTestClient

cp -R build/MediaRemoteAdapter.framework <repo>/desktopApp/resources/macos/
cp build/MediaRemoteAdapterTestClient <repo>/desktopApp/resources/macos/
cp bin/mediaremote-adapter.pl <repo>/desktopApp/resources/macos/
cp LICENSE <repo>/desktopApp/resources/macos/LICENSE-mediaremote-adapter
```

DeskBuddy expects all four files in this `macos/` directory. The test client is
needed by the adapter's `test` command; the command can briefly publish a test
session if no player is currently reporting media. At runtime, DeskBuddy copies
the test client to a private temporary file and makes that copy executable,
because Compose Desktop doesn't preserve executable mode for packaged resources.
Check adapter compatibility with:

```bash
/usr/bin/perl mediaremote-adapter.pl \
  "$PWD/MediaRemoteAdapter.framework" \
  "$PWD/MediaRemoteAdapterTestClient" test
```

An exit code of `0` means the adapter works on this macOS version. When these
files are absent, DeskBuddy still starts and uses the Music/Spotify fallback.

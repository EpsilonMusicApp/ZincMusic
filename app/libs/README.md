# Vendored binaries (`app/libs/`)

This directory holds third-party artifacts that are committed to the repo instead of
being resolved from a Maven repository. All of them are picked up automatically by
the `implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])` statement in
`app/build.gradle`.

| Artifact | Upstream | Why it is vendored |
|---|---|---|
| `NewPipeExtractor-v0.26.1-zinc.jar` | `com.github.TeamNewPipe:NewPipeExtractor:v0.26.1` (JitPack) | **Patched copy** — see below |
| `ffmpeg-kit-audio-6.0-2.LTS.aar` | Arthenica ffmpeg-kit | Heavier to resolve than to store |
| `smart-exception-common-0.2.1.jar`, `smart-exception-java-0.2.1.jar` | com.arthenica:smart-exception-java | ffmpeg-kit's companion jars |
| `wavy-slider-android-2.2.0.aar` | WavySlider | Not published to Maven Central |

## NewPipeExtractor-v0.26.1-zinc.jar — why patched

`TeamNewPipe/NewPipeExtractor` v0.26.1 (and every later release up to and including
v0.26.5 at the time of writing) implements
`org.schabi.newpipe.extractor.utils.Utils#decodeUrlUtf8` and `#encodeUrlUtf8` with the
Java 10+ overloads `URLDecoder.decode(String, Charset)` / `URLEncoder.encode(String, Charset)`.

Those overloads only exist on **Android API 33+** (Android 13). Zinc Music ships with
`minSdk 25`, so on Android 12 and below any stream extraction crashed with a fatal
`java.lang.NoSuchMethodError` inside `StreamInfo.getInfo(...)` (reported on an LG
device running Android 10). Core library desugaring does **not** cover
`java.net.URLDecoder`/`URLEncoder` (their `j$.net` rewrite set is not part of
`desugar_jdk_libs` 2.1.5), so the only robust fix was to patch the library class.

The patch replaces the two Charset-overload calls with the classic, API-1-safe
String-charset overloads — exactly the fix recommended in the crash report:

```java
public static String encodeUrlUtf8(final String string) {
    try {
        return URLEncoder.encode(string, "UTF-8");   // was: URLEncoder.encode(string, StandardCharsets.UTF_8)
    } catch (final java.io.UnsupportedEncodingException e) {
        throw new AssertionError(e);                 // UTF-8 is guaranteed on every runtime
    }
}

public static String decodeUrlUtf8(final String url) {
    try {
        return URLDecoder.decode(url, "UTF-8");      // was: URLDecoder.decode(url, StandardCharsets.UTF_8)
    } catch (final java.io.UnsupportedEncodingException e) {
        throw new AssertionError(e);
    }
}
```

Everything else in the jar is byte-identical to the upstream v0.26.1 artifact:
exactly one entry (`org/schabi/newpipe/extractor/utils/Utils.class`) differs; all
other 471 entries are unchanged, and the public API of `Utils` is unchanged.

### How it was built (for future maintenance)

1. Download upstream jar:
   `https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/v0.26.1/NewPipeExtractor-v0.26.1.jar`
2. Fetch the matching `Utils.java` source (tag `v0.26.1`) from
   `https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.1/extractor/src/main/java/org/schabi/newpipe/extractor/utils/Utils.java`
   and apply the two-method patch shown above (also drop the now-unused
   `java.nio.charset.StandardCharsets` import).
3. Recompile with Java 11 bytecode (matches upstream; ECJ was used:
   `java -jar ecj.jar -source 11 -target 11 -cp <upstream.jar>:jsr305-3.0.2.jar Utils.java`).
4. Replace `org/schabi/newpipe/extractor/utils/Utils.class` inside a copy of the jar:
   `zip NewPipeExtractor-v0.26.1-zinc.jar org/schabi/newpipe/extractor/utils/Utils.class`

### Former transitive dependencies (now declared explicitly)

The JitPack `com.github.TeamNewPipe:NewPipeExtractor` module pulled six runtime
dependencies. Because a vendored jar carries no POM, `app/build.gradle` declares them
explicitly with the exact versions the upstream module metadata lists — keep them in
sync when bumping this artifact:

- `com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996`
- `org.jsoup:jsoup:1.22.1`
- `com.google.code.findbugs:jsr305:3.0.2`
- `com.google.protobuf:protobuf-javalite:4.34.1`
- `org.mozilla:rhino:1.8.1`
- `org.mozilla:rhino-engine:1.8.1`

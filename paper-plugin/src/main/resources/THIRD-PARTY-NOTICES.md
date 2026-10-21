# Third-party notices for TikTokMinecraft

TikTokMinecraft itself is distributed under the MIT License (see `LICENSE`).
The distribution JAR bundles the components listed below. Their own licence
terms apply to those components and to the files they contributed.

## TikTok LIVE Java SDK

`io.github.nglmercer:tiktok-live-client` and
`io.github.nglmercer:tiktok-live-jvm-signer`, from
<https://github.com/nglmercer/tiktok-signer>. MIT licensed, except for the
TikTok webcast protobuf schema material described below.

The SDK's own notices are preserved verbatim inside the JAR:

* `META-INF/LICENSE.protobuf` — the full text of the modified AGPL-3.0 that
  covers the vendored webcast schemas.
* `META-INF/THIRD_PARTY_NOTICES.md` — the SDK's account of where the schemas
  came from and what the additional permissions mean.

### TikTok webcast protobuf schemas

The `webcast.*` classes bundled in the JAR are generated from schemas vendored
from [isaackogan/TikTok-Webcast-Protobuf](https://github.com/isaackogan/TikTok-Webcast-Protobuf)
(path `src/slim/v3`). They are **not** MIT licensed. They are covered by a
modified AGPL-3.0: the stock AGPL text plus Section 7 additional permissions,
reproduced in `META-INF/LICENSE.protobuf`.

Section 18 permits the schemas and their generated bindings to be integrated
into downstream client libraries and applications, commercial or not, without
those downstream projects becoming AGPL. Section 19 withdraws that exception
where the code powers a commercial, closed-source or hosted service offered to
third parties over a network, such as a WebSocket relay or a managed API; in
that case the unmodified AGPL, including its network-use provision, applies.

**TikTokMinecraft is a self-hosted server plugin.** It connects directly to
TikTok LIVE from the operator's own machine and does not relay or resell
access to anyone else, so Section 19 is not the operative clause for this
distribution. Section 18's exception covers it.

Redistributing this JAR therefore means redistributing AGPL-licensed schema
material. The full licence text and the SDK's analysis are shipped in the JAR
so that anyone receiving the plugin has them.

## Protocol Buffers (Java runtime)

`com.google.protobuf:protobuf-java`. BSD 3-Clause License. The licence text is
available in the upstream distribution and the upstream project is the source
of the applicable terms.

## Jackson

`com.fasterxml.jackson.core:jackson-databind`, `jackson-core` and
`jackson-annotations`. Apache License 2.0.

## OkHttp and Okio

`com.squareup.okhttp3:okhttp` and `com.squareup.okio:okio`. Apache License
2.0. Their licence texts are preserved in the JAR as
`META-INF/thirdparty-LICENSE` and `META-INF/FastDoubleParser-LICENSE`.

## Kotlin standard library

`org.jetbrains.kotlin:kotlin-stdlib` and the related artifacts pulled in by
OkHttp. Apache License 2.0.

## GraalVM Polyglot and GraalJS

`org.graalvm.polyglot:polyglot`, `org.graalvm.js:js-language` and
`org.graalvm.truffle:truffle-runtime` are **not** bundled in this JAR. They
are declared in `plugin.yml` and are loaded at runtime by the Paper server's
own library loader from Maven Central. GraalJS is dual licensed under the
Universal Permissive License 1.0 and MIT; Truffle under UPL 1.0 and Apache
2.0.

# Android Network Observe

This retained legacy loader family combines network-facing observation and
SSL/TLS bypass behavior.

Current internal strategies:

- `ssl-bypass.ts`
- `http-tracer.ts`

Legacy selection operates on the whole `android/network-observe` family.
On Android with Java available, even `enable: ["http"]` selects this family,
whose `index.ts` attempts SSL bypass before HTTP tracing.
`ssl.quiet: true` suppresses the bypass messages routed through `quietSend`.
`ssl.comprehensive: false` selects the basic SSLContext, OkHttp 3.x,
HttpsURLConnection and Conscrypt bypasses. Both settings still attempt bypass
installation.

Current tracing surface in `http-tracer.ts`:

- OkHttp 3.x request and response tracing through `RealCall.getResponseWithInterceptorChain()` when available
- OkHttp 3.x fallback sync tracing through `RealCall.execute()`
- OkHttp 2.x sync tracing
- `HttpURLConnection` request and response tracing
- Volley request and response tracing
- Cronet builder/request tracing, including deferred install for late-loaded Cronet classes
- `java.net.Socket.connect(...)` transport fallback tracing through `network/socket/connect`
- `SSLSocket`/Conscrypt handshake fallback tracing through `network/tls/handshake`
- structured failure events for Java HTTP call errors

Useful flags under `configs/targets/*.json`:

- `http.captureHeaders`
- `http.captureResponseHeaders`
- `http.captureBodyMeta`
- `http.captureBody`

Interpretation rule for agents:

- Prefer `network/http/request` and `network/http/response` when present.
- If a target uses native Cronet, QUIC, or another hidden transport path, HTTP-level request objects may not appear in Java hooks.
- In that case, treat `network/socket/connect` and `network/tls/handshake` as lower-layer call evidence. Inspect the event phase and failure details before drawing conclusions about successful traffic.

The family and its strategies expose no `deactivate` callback. Loader shutdown
therefore has no family-specific restoration step, and the HTTP tracer's
deferred Cronet timers have no cancellation handles. Their hook and timer
lifetime currently relies on Script unload. The implementation provides no
rollback for trust managers already passed to `SSLContext.init` or other
App-owned object state. Field assignments made by accompanying spoofing
strategies also need their own restoration. Recovery claims require actual
target checks after unload and, where process state persists, a fresh process.

Reusable network mechanisms justified by actual use belong in this family.
Public target integration and acceptance belong in `apps/<sample>/` and
`cases/`. Private protocol logic, target assertions and reports stay with
their consuming project.

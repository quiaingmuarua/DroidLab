# Local Messages RPC bindings

`dev.dexlineage.messages.RpcBindingReader.read(artifact, anchor)` is a small typed
library entrypoint in `:dexlineage:jadx`. It accepts a current `UNIQUE`
`messages.rpc-descriptor` METHOD support anchor and rereads that APK. The anchor
must have the current engine build identity and its original complete rule and
proof. Old proofs, a different APK, or a caller-selected method are input errors.

The result describes a static, zero-argument, cached Descriptor getter. It exposes
the cache and Descriptor role fields, Builder/build/constructor, request/response
message types, and the declared and implemented marshaller parse/stream methods.
All addresses are original `ClassRef`/`MethodRef` values or typed field references.
No JADX object crosses the API. Fields are local binding data, not schema-4
knowledge entities, rename targets, or a new strategy language.

The reader reuses the existing RPC construction interpretation: service/name,
Builder state, Descriptor constructor transfer and marshaller default instances
must belong to one construction. Getter return paths must resolve to that build
result or the same static volatile cache. Only an exact monitor-exit/rethrow
cleanup handler is excluded from exceptional-path uncertainty. Other unhandled
paths remain `UNKNOWN`. A complete relationship is not inferred from independent
member occurrences or from the `relatedRefs` evidence set.

`RpcBindingReadResult` contains the APK and anchor identities, status, typed
bindings, diagnostics and code locations. `UNIQUE` permits a consumer to perform
its explicitly scoped local loading/codec test; it does not approve a Rename,
create a Channel, establish authentication, or prove network callbacks occurred.
Consumers must check `status` before using any diagnostic candidates and verify
the installed artifact separately. The API does not perform runtime calls.

Public `RpcBindingIntegrationTest` resolves actual R8 fixture methods through CLI
subprocesses before reading bindings. Its independent Mapping oracle checks every
method's complete signature and each role field. The second version reorders
constructor arguments and fields and wraps marshaller construction in a factory.
A separate getter that stores one Descriptor but returns an unrelated cache is
rejected. Unit tests cover stale/wrong proof inputs and the narrowly allowed
monitor cleanup shape. No private APK or business service name is required.

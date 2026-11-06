# Examples

All rule files use schema 4 and fictional inputs. Copy them into your project before adding private values.

- `analysis-rules.json`: direct exact-string matching. A suggested name is not acceptance.
- `logger-profile.json`: built-in field-initializer conditions and capture-based rule drafts; no application-specific handler is required.
- `profile-workspace.json`: input to a single `analysis run --method profile`; its historical filename does not create a recoverable workspace.
- `migration-overrides.json`: replace a known target's whole locator set in a new run; unknown semantic IDs fail.
- [External consumer](external/README.md): implement a handler and an AnalysisMethod outside production modules, compile using installed JARs, then invoke the same CLI and lifecycle.

Project-specific RPC/Proto examples and recipes are maintained by the project that provides those extensions. The core CLI does not silently install them. A confirmed Mapping always requires unique code matching and valid semantic Review; changing an alias cannot resolve ambiguity.

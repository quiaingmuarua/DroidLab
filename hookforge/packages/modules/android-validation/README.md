# Android Validation

This retained legacy loader family contains validation flows for Frida-side
spoofing experiments.

Current internal strategies:

- `spoof-validator.ts`

The strategy runs only with `validator.enabled: true`. It reads selected
TelephonyManager, Build and Settings values and compares them with the legacy
spoof configuration. With `validator.detectApp: true`, it also directly calls
the sample's `SettingPropInfoCollect.getAllInfo` and interprets its output.
The current `spoof-validation-report` hardcodes `com.detect.app.sample` as
`appPackage`, and each result's `originalValue` is the value observed during
validation. Use the report within that readback scope and retain separate
evidence of the target's naturally triggered behavior and recovery.

The family and strategy expose no `deactivate` callback. Initial validation,
Context retries and delayed reporting use timers without retained cancellation
handles, so loader shutdown leaves them pending while the Script remains
loaded. Script unload ends their lifetime. The validator reads spoofed fields
and owns no field restoration. Strategies that changed App fields or cached
state need their own restoration and target checks.

Future shared validation mechanisms and report helpers belong here when actual
reuse justifies them. Public target triggers, target-specific assertions and
acceptance reports belong in `apps/<sample>/` and `cases/`. Private consumer
validation and research stay with their consuming project.

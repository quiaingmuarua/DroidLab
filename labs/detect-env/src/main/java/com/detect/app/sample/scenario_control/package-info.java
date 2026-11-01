/**
 * Scenario-facing control-surface helpers for the detect-env app.
 *
 * <p>This package is intentionally narrow. It groups the pieces that exist only to expose
 * detect-env checks through the embedded capability HTTP surface and configured Worker Delivery.
 *
 * <p>Keep these boundaries stable:
 *
 * <ul>
 *   <li>{@code DetectEnvWorkerEvents}: local event-definition boundary for the scenario-facing
 *       HTTP surface
 *   <li>{@code DetectEnvCheckHandlers}: app-side check execution and verdict shaping
 *   <li>{@code DetectEnvGeneratedContracts}: generated scenario definitions returned by
 *       {@code extension.worker.detect-env.scenario-catalog}
 *   <li>{@code DetectEnvScenarioModels}: serialization-only DTO container for the control surface
 * </ul>
 *
 * <p>Do not move general detect-env feature logic here. Real collectors and detect points still belong
 * under feature packages such as {@code risk_detect}, {@code fingerprint_detect}, and
 * {@code private_info_detect}.
 *
 * <p>If a change is about what the target app detects, start in the owning feature package first. If a
 * change is about how a scenario-facing event is exposed or how a generated scenario is described,
 * start here.
 */
package com.detect.app.sample.scenario_control;

/**
 * Scenario-facing control-surface helpers for the android-lab app.
 *
 * <p>This package is intentionally narrow. It groups the pieces that exist only to expose
 * android-lab demos through the embedded local capability HTTP surface.
 *
 * <p>Keep these boundaries stable:
 *
 * <ul>
 *   <li>{@code AndroidLabWorkerEvents}: local event-definition boundary for the scenario-facing
 *       HTTP surface
 *   <li>{@code AndroidLabCheckHandlers}: app-side check execution and verdict shaping
 *   <li>{@code AndroidLabGeneratedContracts}: generated scenario definitions returned by
 *       {@code extension.worker.android-lab.scenario-catalog}
 *   <li>{@code AndroidLabScenarioModels}: serialization-only DTO container for the control surface
 * </ul>
 *
 * <p>Do not move general android-lab feature logic here. Real collectors and demos still belong
 * under feature packages such as {@code system}, {@code network}, and {@code runtime}.
 */
package com.app.androidlab.sample.scenario_control;

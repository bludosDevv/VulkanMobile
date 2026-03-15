# VulkanMobile Architecture Documentation

This document describes the planned architecture for a mobile-first Vulkan Minecraft mod with SPIR-V shader pack support.

> **Important:** This is an architecture/specification document. It intentionally does not implement all systems at once.

---

## 1) Problem Context (Helio G85 / Mali-G52 / Vulkan 1.1)

Observed issues:
- Distance-related chunk and village flicker/corruption.
- Water intermittently becoming fully transparent.
- Held item black flicker while moving/idle.
- UI flicker across frames.

Likely classes of root causes:
1. Swapchain/present synchronization gaps.
2. Render pass dependency and image layout transition hazards.
3. Depth precision and/or depth attachment lifecycle issues.
4. Transparent pass ordering hazards and attachment load/store mismatches.
5. Mobile-specific driver sensitivity to weak barriers or undefined contents.

---

## 2) System Architecture Overview

### 2.1 Core layers

1. **Render Core Layer**
   - Swapchain lifecycle
   - Command buffers + frame graph orchestration
   - Render pass setup and pipeline binding

2. **Mobile Stability Layer**
   - Device capability/profile probing
   - Conservative synchronization policy for known-sensitive GPUs
   - Safety fallbacks (feature flags, pass simplification)

3. **Shader Pack Runtime Layer**
   - Shader pack discovery (`shaderpacks` directory)
   - SPIR-V package loader
   - Reflection-driven descriptor/push constant binding
   - Pipeline cache and permutation manager

4. **User Configuration Layer**
   - Vulkan options UI + new “Shaders” category
   - Preset/profile toggles for Mali-safe settings
   - Runtime validation diagnostics

---

## 3) Rendering Stability Strategy (Phase 1)

### 3.1 Synchronization policy
- Maintain frame fences and per-swapchain-image ownership tracking.
- Strengthen external subpass dependencies around `PRESENT_SRC` transitions.
- Enforce explicit access masks for color/depth/transparent transitions.
- Validate stage masks against actual producer/consumer stages.

### 3.2 Depth and transparency policy
- Audit depth attachment format and store/load operations for tiled GPUs.
- Ensure transparent pass reads depth in a valid layout and access scope.
- Add strict ordering between opaque -> transparent -> post/UI.
- Add optional mobile depth bias/precision profile (configurable).

### 3.3 UI pass isolation
- Ensure UI render pass has stable clear/load semantics.
- Avoid accidental dependency overlap between world and UI attachments.
- Verify presentation path always observes completed UI color writes.

### 3.4 Instrumentation
- Add targeted debug counters and optional overlays for:
  - pass transitions,
  - layout states,
  - semaphore/fence timing,
  - pipeline rebind churn.

---

## 4) Shader GUI Architecture (Phase 2)

### 4.1 Menu integration
- Add **Shaders** entry to left navigation in Vulkan options.
- Sub-sections:
  - Installed packs,
  - Active pack,
  - Compatibility profile,
  - Advanced diagnostics.

### 4.2 `shaderpacks` folder lifecycle
- On startup/options open:
  - create `.minecraft/shaderpacks` if missing,
  - index supported pack manifests,
  - cache metadata for quick UI display.

### 4.3 UX constraints
- Never hard-crash when pack load fails.
- Use transactional apply/revert flow:
  1. validate pack,
  2. compile/load pipelines,
  3. activate only on success,
  4. auto-rollback on failure.

---

## 5) SPIR-V Pipeline Architecture (Phase 3)

### 5.1 Runtime contract
- Runtime accepts **SPIR-V modules only**.
- GLSL conversion is an offline/import-time step.
- All shader stages must pass capability/profile validation before activation.

### 5.2 Pack format (planned)

A shader pack should contain:
- `pack.json` (metadata, version, supported profiles),
- stage modules (`*.spv`),
- pipeline descriptors,
- optional material/feature toggles.

Current lightweight `pack.json` contract used by runtime overrides:

```json
{
  "name": "E-LITE Mobile",
  "format": 1,
  "description": "Precompiled SPIR-V pack for Mali Vulkan 1.1",
  "pipelines": {
    "terrain": {
      "vertex": "pipelines/terrain/terrain.vert.spv",
      "fragment": "pipelines/terrain/terrain.frag.spv"
    },
    "terrain_earlyZ": {
      "vertex": "pipelines/terrain_earlyZ/terrain_earlyZ.vert.spv",
      "fragment": "pipelines/terrain_earlyZ/terrain_earlyZ.frag.spv"
    },
    "blit": {
      "vertex": "pipelines/blit/blit.vert.spv",
      "fragment": "pipelines/blit/blit.frag.spv"
    },
    "clouds": {
      "vertex": "pipelines/clouds/clouds.vert.spv",
      "fragment": "pipelines/clouds/clouds.frag.spv"
    }
  }
}
```

### 5.3 Reflection + binding
- Use SPIR-V reflection metadata to map:
  - descriptor sets and bindings,
  - push constants,
  - specialization constants.
- Enforce engine-owned binding slots to avoid pack collisions.

### 5.4 Conversion pipeline (E-LITE baseline)
1. Import GLSL source pack (e.g., E-LITE).
2. Convert using `glslc`/`glslangValidator` with strict target profile.
3. Run compatibility validator for Mali-safe subset.
4. Generate pack manifest + pipeline config.
5. Ship validated SPIR-V artifacts as built-in default pack.

### 5.5 Stability guardrails
- Disallow unsupported ops/extensions at pack load.
- Budget descriptor count and texture samplers per profile.
- Compile pipelines asynchronously with fallback pipeline retained.
- Cache pipeline binaries; invalidate on driver/version/hash change.

---

## 6) Planned Player Workflow for Custom SPIR-V Packs

1. Player drops pack in `.minecraft/shaderpacks`.
2. Mod indexes pack and validates manifest.
3. Mod validates SPIR-V capabilities against active device profile.
4. Mod reflects required resources and checks binding limits.
5. Mod compiles pipeline set in staging mode.
6. If successful, user can activate; if not, detailed error is shown.
7. Activation persists in config with automatic rollback safety.

---

## 7) Compatibility Profiles (Planned)

Example profile tiers:
- **Mobile Safe (default)**: strongest barriers, conservative effects.
- **Balanced**: moderate effects and reduced strictness.
- **Experimental**: desktop-like features, lower stability guarantees.

Each profile controls:
- synchronization strictness,
- pass ordering constraints,
- optional effects,
- descriptor and memory budgets.

---

## 8) Non-Goals (for initial rollout)

- Full GLSL runtime parsing/compilation on-device.
- Unbounded desktop shader feature parity from day one.
- Massive one-shot refactors without per-phase validation.

---

## 9) Deliverable Sequence

1. Phase 1: render artifact stabilization and telemetry.
2. Phase 2: shader UI and folder/pack management.
3. Phase 3: SPIR-V runtime, conversion tooling, and default E-LITE pack integration.

Implementation should proceed in small validated increments per phase.

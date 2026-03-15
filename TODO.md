# Technical TODO Roadmap

This roadmap breaks down Phases 1, 2, and 3 into actionable, testable steps.

---

## Phase 1 — Bug Fixing (Helio G85 Artifact Stabilization)

### A. Reproduction & Baseline
- [ ] Create a reproducible test matrix:
  - [ ] Render distances: 8 / 12 / 16+
  - [ ] Biomes and structures: villages, water-heavy scenes, foliage-heavy scenes
  - [ ] Camera states: idle, walking, sprinting, rotating
- [ ] Capture baseline evidence per scenario:
  - [ ] screenshot pairs
  - [ ] frame-time and FPS logs
  - [ ] Vulkan validation/log markers (if available)

### B. Synchronization Audit
- [ ] Map complete frame synchronization chain:
  - [ ] image acquire semaphore -> graphics queue submit -> present wait semaphore
  - [ ] per-frame fence and per-image fence ownership
- [ ] Verify all pass transitions include correct stage + access masks:
  - [ ] color attachment writes -> present
  - [ ] color/depth writes -> shader reads
  - [ ] transparent pass dependencies
- [ ] Validate command buffer submission order and queue-family assumptions on mobile drivers.

### C. Depth/Transparency Pipeline Audit
- [ ] Confirm depth format suitability and fallback behavior on Vulkan 1.1 devices.
- [ ] Verify depth load/store ops for all passes touching world + water + held items.
- [ ] Check transparent sorting path and ordering stability for water and alpha surfaces.
- [ ] Add/verify explicit barriers when reusing depth/color in later passes.

### D. UI Flicker Isolation
- [ ] Validate UI pass attachment lifecycle:
  - [ ] clear/load/store semantics
  - [ ] dependency with world/post passes
- [ ] Ensure UI draw data is not sampled from stale image contents.
- [ ] Confirm final present layout transition occurs after UI completion.

### E. Mobile Safety Profiles (Config)
- [ ] Add internal profile toggles (no broad UI exposure yet):
  - [ ] strict barriers
  - [ ] conservative transparent path
  - [ ] optional reduced effect path
- [ ] Detect known-sensitive GPUs (Mali-G52 family) and apply safe defaults.

### F. Validation & Exit Criteria
- [ ] Artifact pass criteria:
  - [ ] no chunk/village flicker at target render distances
  - [ ] no random full-transparent water events
  - [ ] no black held-item flicker over 10-minute test
  - [ ] stable UI without frame-to-frame flashing
- [ ] Document regression checklist and device metadata.

---

## Phase 2 — Shader GUI Implementation

### A. UI Information Architecture
- [ ] Add `Shaders` category in left Vulkan options navigation.
- [ ] Design screens:
  - [ ] Installed Packs
  - [ ] Active Pack + Apply/Revert
  - [ ] Compatibility Profile
  - [ ] Error/Validation Log View

### B. Filesystem Lifecycle
- [ ] Ensure `.minecraft/shaderpacks` is created at startup/options open.
- [ ] Build pack scanner:
  - [ ] detect manifests
  - [ ] detect SPIR-V stage assets
  - [ ] cache metadata

### C. Pack Management UX
- [ ] Implement non-destructive apply flow:
  - [ ] preload + validate
  - [ ] stage pipelines
  - [ ] activate on success
  - [ ] rollback on failure
- [ ] Add user-friendly error mapping for pack validation failures.

### D. Config Persistence
- [ ] Persist selected pack + profile.
- [ ] On startup, fallback to last-known-good if current pack fails load.

### E. Validation & Exit Criteria
- [ ] UI functions without crash across enable/disable cycles.
- [ ] Broken pack does not break game launch.
- [ ] Shaderpacks directory is consistently maintained.

---

## Phase 3 — SPIR-V Shader Pipeline

### A. Runtime SPIR-V Contract
- [ ] Define accepted SPIR-V version and capabilities.
- [ ] Reject unsupported extensions at load-time with explicit diagnostics.

### B. Pack Format Specification
- [ ] Finalize `pack.json` schema:
  - [ ] metadata
  - [ ] supported profile(s)
  - [ ] stage mapping
  - [ ] option definitions
- [ ] Define required/optional resource declarations.

### C. Reflection and Resource Binding
- [ ] Integrate reflection path for descriptor sets and push constants.
- [ ] Implement binding allocator to map pack resources into engine slots safely.
- [ ] Add limits validation against active device caps.

### D. Conversion Toolchain (GLSL -> SPIR-V)
- [ ] Create offline conversion scripts using `glslc`/`glslangValidator`.
- [ ] Define compile profiles per target (Mobile Safe/Balanced/Experimental).
- [ ] Build validation stage that rejects unstable permutations.

### E. E-LITE Integration
- [ ] Import E-LITE shader sources.
- [ ] Produce validated SPIR-V output pack.
- [ ] Add default built-in pack registration and fallback path.

### F. Pipeline Caching + Robustness
- [ ] Add pipeline cache keying by:
  - [ ] driver/version/device
  - [ ] pack hash
  - [ ] profile
- [ ] Add async compilation and temporary fallback rendering path.

### G. Validation & Exit Criteria
- [ ] Stable activation/deactivation across sessions.
- [ ] No GPU crash on Mali-G52 with default pack + safe profile.
- [ ] Documented process for third-party pack authors.

---

## Cross-Phase Engineering Hygiene

- [ ] Keep each change set small and benchmarkable.
- [ ] Require repro steps + expected outcome for every bugfix PR.
- [ ] Maintain device-specific issue tracker entries (GPU, driver, Android version).
- [ ] Update docs after each completed milestone.

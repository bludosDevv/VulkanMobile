# VulkanMobile (Helio G85 / Mali-G52 Focus)

VulkanMobile is a mobile-oriented fork/variant of VulkanMod focused on improving rendering stability on Android-class Vulkan 1.1 GPUs (especially MediaTek Helio G85 devices with Mali-G52).

This project keeps Vulkan as the rendering backend and prioritizes:
- frame stability over peak desktop-style throughput,
- predictable synchronization on strict mobile drivers,
- a shader system designed for SPIR-V-first compatibility.

---

## Project Goals

1. **Fix severe mobile rendering artifacts** on Vulkan 1.1 devices (flicker, chunk corruption, transparent water, black item flashes, UI instability).
2. **Add a shader management UX** directly into the Vulkan options UI, including a `shaderpacks` folder lifecycle.
3. **Implement a robust SPIR-V shader pipeline** (no raw runtime GLSL dependency), with E-LITE as the default bundled shader baseline.

---

## Current Development Status

This repository is currently in a **design + staged implementation** workflow:
- ✅ Planning and architecture docs are being prepared first.
- 🔄 Phase 1 (artifact fixes) is active and iterative.
- ⏳ Phase 2 and 3 are specified but intentionally not fully implemented in one sweep.

See:
- [`TODO.md`](./TODO.md) for the execution plan.
- [`Documentation.md`](./Documentation.md) for architecture details.

---

## Scope (Mobile-Optimized Behavior)

This branch targets behavior differences commonly seen on mobile Vulkan stacks:
- stricter synchronization requirements,
- precision/layout transition sensitivity,
- tile-based renderer quirks,
- aggressive memory pressure and thermal throttling scenarios.

Design decisions may differ from desktop Vulkan expectations when stability is at risk.

---

## Planned Shader Support Model

- Shader system is **SPIR-V only** at runtime.
- User shader packs are loaded from `.minecraft/shaderpacks`.
- A validated, profile-limited pipeline is used to avoid GPU crashes on Mali-class devices.
- E-LITE shaders are intended as the baseline default pack after conversion/validation.

---

## Contributing

If you are contributing fixes, prioritize:
1. reproducible device logs,
2. minimal, testable synchronization/render-pass changes,
3. fallback behavior for Vulkan 1.1 limitations.

When possible, include:
- before/after captures,
- impacted render stage,
- affected GPU/driver build details.

---

## Disclaimer

This is an experimental rendering fork tuned for mobile Vulkan behavior. Feature parity with desktop-focused shader mod ecosystems is not guaranteed during early phases.

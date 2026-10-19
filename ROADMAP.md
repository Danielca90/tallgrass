# Graphics roadmap — better sprites, still 100% code

> **Update:** the overworld now runs in the HD style (480x320, 32px tiles, full color) from a texture
> pack baked from code — see `HdArt`, `Textures`, `TextureBaker`, `AreaRenderer`. Terrain edges are
> blended procedurally, trees/buildings/characters are depth-sorted sprites. Next HD passes: battles,
> menus, creature sprites at 128px, characters' walk polish.

Tallgrass draws everything procedurally: no PNGs, no asset packs. This roadmap keeps that rule and
raises the art from "programmer pixels" to something close to a real GBA game. The trick is to stop
drawing shapes straight onto images and instead build a small **art pipeline in code**: shared
palettes, a pixel canvas with real pixel-art operations, reusable part templates, and post-processing.

Ordered by impact per effort. Each phase ships on its own.

| # | Phase | Effort | Visual impact |
|---|-------|--------|---------------|
| 1 | ✅ Palette + pixel pipeline | S | Everything looks more cohesive at once |
| 2 | Autotiled maps + 2x2 trees | M | Biggest jump for the overworld |
| 3 | Shaded, part-based creatures | L | Creatures stop looking like cut-outs |
| 4 | Characters and trainer portraits | M | Battles feel like real trainer fights |
| 5 | Battle backgrounds + move animations | M | Battles come alive |
| 6 | Lighting, weather, day/night | M | Atmosphere per biome |
| 7 | Art tooling | S | Makes all of the above faster to iterate |

---

## 1. Palette and pixel pipeline (foundation) — ✅ done

Shipped: `Palette` (hue-shifted 5-shade ramps, GBA 15-bit quantizing of every tile, sprite and final
frame), `PixelCanvas` (material-indexed canvas with distance-field volume shading, selective outlines,
inner seams, orphan cleanup, optional Bayer dithering), and `Icons` (ASCII templates for orbs and emotes,
canvas-shaded potions). Creatures, characters, trees, rocks, cacti, grass, paths and water use them.

- **`Palette` class.** Replace ad-hoc `lighter()/darker()` with 4–5 step color *ramps* per material
  (grass, water, stone, wood, skin, each creature color). Hue-shift the ramps: shadows lean cooler/bluer,
  highlights warmer/yellower. That alone is the difference between flat and "painted" pixel art.
- **Quantize to the GBA's 15-bit color** (5 bits per channel) on every generated image. Instantly
  more authentic and keeps colors from drifting.
- **`PixelCanvas`** (an `int[]` buffer of palette indices) with the operations pixel artists actually use:
  - *selective outline* ("sel-out"): outline each region with a darker shade of its own color instead of black, lighter where the light hits;
  - *pixel-perfect lines* (remove L-shaped corners), orphan-pixel cleanup;
  - ordered dithering (2x2/4x4 Bayer) for gradients on large areas such as skies, water and roofs;
  - mirror, flood fill, ellipse/capsule rasterizers.
- **ASCII templates.** For details shapes do badly (faces, hands, item icons, windows, doors), define
  tiny sprites as string arrays in code, exactly like `Font.java` already does. Template + palette
  swap = lots of variety from little code.

## 2. Maps: autotiling, bigger props, texture

- **Autotiling** with 4-bit marching squares (16 variants) or the 47-tile blob set: paths get rounded
  edges, ponds get shorelines and foam, tall-grass patches get soft borders. Each variant is still
  generated: draw the base, then mask edges with a noise-jittered curve.
- **2x2 trees (32x32)** that overlap the row above and are drawn after the player, so you walk *behind*
  canopies. Forest borders become a continuous canopy instead of repeated circles.
- **Ledges and cliffs** with a lit top edge and a shaded face (also a GBA gameplay feature: one-way jumps).
- **Value noise** for large-scale grass/sand color variation and Poisson-disk placement for pebbles,
  flowers and shells, so no two screens tile visibly.
- **Modular buildings:** roof kits (shingles, tiles, thatch per biome), walls with window frames and
  shutters, awnings, signboards, chimneys with animated smoke. Gyms get a type emblem drawn from the
  type's icon template.
- Animation: shoreline foam, tall-grass rustle when stepped in, swaying flowers, flickering lanterns.

## 3. Creatures: from silhouettes to shaded, posed sprites

- **Part library.** Heads, muzzles, ears, horns, crests, wings, tails, arms, legs, feet, eye styles and
  mouths, each a small function or template with anchor points. A species picks parts by type and shape,
  so evolutions share parts and grow new ones.
- **3/4 view instead of straight-on.** GBA front sprites face slightly sideways. Build the body from
  3D primitives (spheres, capsules, cones) as signed-distance functions, rotate ~25°, and rasterize at
  64x64. You get perspective, overlap and real *back* sprites from the same model.
- **Distance-field shading.** Compute each pixel's distance to the silhouette edge and surface normal
  from the SDF, then map lighting onto the color ramp, plus a rim light and eye highlights. Every random
  shape becomes volumetric automatically.
- **Asymmetry and texture:** tails to one side, a raised paw, stripes, spots and scales via patterns
  clipped to body parts, and type touches (flame tips, droplets, leaves, sparks, frost).
- **Animation:** 2-frame idle (breathe + blink), an entrance animation per shape in battle, and a hit
  flash.
- **Dedicated 32x32 icons**, drawn simplified rather than downscaled, with a 2-frame party wiggle.

## 4. Characters and trainers

- Body templates (kid, adult, big, small) × outfit overlays (hiker backpack, swimmer, lab coat, uniform)
  × palette from the character creator, plus 4-frame walk and run cycles.
- **64x64 trainer battle portraits** generated from the same look parameters: the trainer slides in
  at battle start, and the player's back sprite throws the orb, exactly like the GBA games.
- Emotes (!, ?, music note, heart) as templates over NPC heads.

## 5. Battles

- **Parallax backgrounds** per biome: sky gradient, noise-generated mountain or tree lines, ground
  texture, and platforms with grass tufts, all from the biome palette.
- **Move animations from a particle system in code:** per-type particle templates (flame, bubble,
  leaf, spark, rock, wind streak, shadow wisp, snowflake, star, gear) + simple motion scripts
  (projectile, burst, rain-down, slash). Add screen shake and a white flash on critical hits.
- Catch sparkles, level-up stat window, status icons, an animated EXP bar fill sound-sync later.

## 6. Atmosphere

- **Day/night cycle** as color grading on the final 240x160 frame (a palette lookup table per hour),
  with windows lighting up at night.
- **Weather per biome:** snowfall in Snowfields, sandstorm streaks in Dunes, rain and mist in the
  Swamp, heat shimmer in Canyons.
- **Caves:** darkness with a light radius around the player (a "Flash" upgrade widens it).
- Drop shadows under characters, water reflections, and varied battle-transition wipes (wild vs trainer
  vs gym leader).

## 7. Tooling that makes code-art practical

- **Art Lab scene (debug key):** browse every generated creature, tile, building and character by seed,
  cycle palettes, toggle shading passes, and export a sheet PNG. The headless smoke test already
  renders species sheets; this brings it in-game.
- **Golden-image tests:** render fixed-seed sheets in CI and diff against approved images, so a tweak
  to one generator can't silently break another.
- **Fast iteration:** `./gradlew run --continuous` plus a "reload art" key that clears the sprite caches.

---

### Suggested order

1. Phase 1 (palette + `PixelCanvas`), since everything else builds on it.
2. Phase 2 autotiling and 2x2 trees, the most visible change for the least code.
3. Phase 3 in two steps: distance-field shading on the current shapes first, then 3/4-view parts.
4. Phases 4–6 in whatever order is most fun; Phase 7 alongside, whenever iteration starts to feel slow.

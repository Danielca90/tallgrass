# Tallgrass v2 — design snapshots (phase 1 review)

Nothing here is in the game yet. These are the snapshots to approve or change first:

| File | What it is | Edit | Regenerate |
|---|---|---|---|
| `world_map.png` | The fixed open world: 8 zones, level bands 1–60, towns, gyms, points of interest, creature areas, levelling route | `design/world.txt` | `WorldPreview` |
| `zone_meadow.png` | Starting zone in detail | `design/world.txt` | `WorldPreview` |
| `creatures_sheet.png` | The closed roster: 80 species, 18 types | `design/creatures.txt` | `CreaturePreview` |

```bash
javac -cp build/libs/Tallgrass.jar -d /tmp/v2 proposal/v2/*.java
java -cp build/libs/Tallgrass.jar:/tmp/v2 WorldPreview design/world.txt docs/v2
java -cp build/libs/Tallgrass.jar:/tmp/v2 CreaturePreview design/creatures.txt docs/v2
```

Once approved, the game reads these same two files, so a change later is still just an edit here.

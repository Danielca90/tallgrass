# Sprites de criaturas (PixelLab)

Modelo: create_image_pixen, 96x96, no_background, outline "single color black outline",
detail "medium detail", direction south-east, view side. Coste: 1 generación cada uno.

Lección: NO usar palabras como "monster", "starter", "Grass type", "battle sprite", "game boy advance":
arrastran el resultado hacia Pokémon (Sproutle v1 salió como Bulbasaur). Usar "cute original fantasy critter"
+ un animal real concreto + colores hex de creatures.txt + "3/4 front view, full body, clean pixel art sprite".

| # | Archivo | Seed | Prompt |
|---|---------|------|--------|
| 4 | 004_cindrel.png | 204 | cute original fantasy critter, small ember squirrel standing upright on two hind legs, orange fur (#f07830), pale cream chest and belly (#f8e2a8), huge bushy curled tail made of bright yellow-orange fire (#ffd040), pointed ears with tiny ember tufts, round dark eyes, small paws held up, 3/4 front view, full body, clean pixel art sprite |
| 7 | 007_pudlet.png | 207 | cute original fantasy critter, small four-legged axolotl-like newt, sky blue smooth skin (#58aee8), cream belly (#f4e2b0), a ridged brown scallop seashell worn on its back (#a8784a), short frilly gills on the sides of its head, long tail curled into a spiral, big shiny round eyes, stubby legs, 3/4 front view, full body, clean pixel art sprite |
| 1 | rejected/001_sproutle_v1_parece_bulbasaur.png | 101 | (rechazado) prompt con "Grass type starter... monster battle sprite" |
| 1 | 001_sproutle.png | 201 | cute original fantasy critter, small chubby four-legged hedgehog-like seedling creature, light green leafy fur (#6cc24a), cream yellow face and belly (#f4f0c4), a single large curled sprout leaf growing from the top of its head, dark green speckles along its back (#2f8a3a), tiny pink nose, big friendly dark eyes, short stubby legs, 3/4 front view, full body, clean pixel art sprite |

## Cómo los usa el juego
- `./gradlew jar` copia `art/creatures/NNN_nombre.png` al jar como `/creatures/nombre.png` (CreatureArt busca por nombre de especie;
  también acepta overrides en `~/.tallgrass/resourcepack/creatures/nombre.png`). Sin PNG, la especie usa SpriteGen.
- En batalla, resumen, Pokédex y el laboratorio el arte se dibuja 1:1 sobre el framebuffer de 480x320 (HdSprite), sin reescalar.
  El rival se ve en espejo; el tuyo, de frente sin espejo (aún no hay sprites de espalda).
- Los mundos nuevos usan las líneas iniciales de la v2 (Sproutle/Cindrel/Pudlet y sus evoluciones, con los colores de creatures.txt).
  Los mundos guardados antes de este cambio conservan sus iniciales generadas.
- Generaciones futuras: 128x128 encaja 1:1 en el hueco de 64x64 de la batalla clásica (2x); 96x96 se ve un poco pequeño.

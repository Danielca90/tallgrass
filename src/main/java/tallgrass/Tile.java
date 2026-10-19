package tallgrass;

public enum Tile {
    GRASS(false), TALL(false), PATH(false), TREE(true), WATER(true), SAND(false), FLOWER(false),
    ROCK(true), FENCE(true), BLD(true), DOOR(false), FLOOR(false), WALL(true), COUNTER(true),
    MAT(false), TABLE(true), PLANT(true), SHELF(true), BED(true), RUG(false), CAVE_WALL(true),
    SIGN(true), VOID(true);

    public final boolean solid;

    Tile(boolean solid) { this.solid = solid; }
}

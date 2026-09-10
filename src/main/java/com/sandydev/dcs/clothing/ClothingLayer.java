package com.sandydev.dcs.clothing;

/**
 * Logical clothing layers. Higher layers are composited later and therefore appear above lower layers.
 */
public enum ClothingLayer {
    UNDER(10),
    SHIRT(20),
    BASE(30),
    VEST(40),
    JACKET(50),
    COAT(60),
    OUTER(70);

    private final int priority;

    ClothingLayer(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public static ClothingLayer parse(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "under", "underwear", "нижний" -> UNDER;
            case "shirt", "рубашка", "футболка" -> SHIRT;
            case "base", "normal", "основной" -> BASE;
            case "vest", "жилет" -> VEST;
            case "jacket", "куртка", "пиджак" -> JACKET;
            case "coat", "пальто" -> COAT;
            case "outer", "top", "верхний" -> OUTER;
            default -> throw new IllegalArgumentException(
                    "Unknown layer '" + value + "'. Allowed: under, shirt, base, vest, jacket, coat, outer");
        };
    }
}

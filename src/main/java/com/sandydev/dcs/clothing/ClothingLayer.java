package com.sandydev.dcs.clothing;

/**
 * Логические слои одежды. Чем выше приоритет, тем позже слой накладывается на скин
 * и тем выше он оказывается визуально - см. {@link com.sandydev.dcs.clothing.client.ClothingTextureComposer}.
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
                    "Неизвестный layer '" + value + "'. Допустимые значения: under, shirt, base, vest, jacket, coat, outer");
        };
    }
}

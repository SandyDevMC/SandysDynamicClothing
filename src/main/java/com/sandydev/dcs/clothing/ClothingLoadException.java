package com.sandydev.dcs.clothing;

/**
 * Ошибка загрузки одного предмета одежды из архива.
 * <p>
 * Всегда перехватывается на уровне {@link ClothingLoader} для конкретного архива/предмета -
 * никогда не прерывает сканирование остальных архивов в {@code .minecraft/clothes/}.
 */
public class ClothingLoadException extends Exception {

    public enum Reason {
        MISSING_ITEM_JSON,
        INVALID_JSON,
        MISSING_FIELD,
        MISSING_TEXTURE,
        MISSING_ICON,
        INVALID_TEXTURE_SIZE,
        INVALID_ID,
        UNKNOWN_SLOT_FORMAT,
        DUPLICATE_ID,
        IO_ERROR
    }

    private final Reason reason;

    public ClothingLoadException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ClothingLoadException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}

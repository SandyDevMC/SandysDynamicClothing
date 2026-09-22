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
        IO_ERROR,
        /** Имя записи в архиве не прошло проверку безопасности (см. {@link ClothingArchiveSecurity}). */
        UNSAFE_ENTRY_NAME,
        /** Сам файл архива на диске превышает {@link ClothingArchiveSecurity#MAX_ARCHIVE_FILE_SIZE}. */
        ARCHIVE_TOO_LARGE,
        /** В архиве больше записей, чем {@link ClothingArchiveSecurity#MAX_ENTRY_COUNT}. */
        TOO_MANY_ENTRIES,
        /** Одна запись (распакованная) превышает {@link ClothingArchiveSecurity#MAX_ENTRY_SIZE},
         *  либо весь архив суммарно превысил {@link ClothingArchiveSecurity#MAX_TOTAL_UNCOMPRESSED_SIZE}. */
        ENTRY_TOO_LARGE
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

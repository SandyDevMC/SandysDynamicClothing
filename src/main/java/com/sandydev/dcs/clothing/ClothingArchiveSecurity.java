package com.sandydev.dcs.clothing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Всё, что защищает разбор пользовательских архивов одежды ({@code .cloth}/{@code .zip}) от
 * вредоносного или испорченного содержимого.
 * <p>
 * Ничего из архива никогда не исполняется и не записывается на диск - весь разбор идёт в
 * памяти, целиком внутри {@link ClothingArchiveParser}, и превращается либо в байты текстуры
 * для {@link javax.imageio.ImageIO}, либо в текст для {@code Gson}. Классического "zip slip"
 * (запись файла за пределы целевой папки через имя вида {@code ../../evil.txt}) тут в принципе
 * не может случиться, раз ничего не извлекается на диск - но имена записей всё равно
 * проверяются на входе ({@link #isEntryNameSafe}), чтобы это оставалось верным и при будущих
 * изменениях кода, а не только "потому что сейчас так сложилось".
 * <p>
 * Отдельно от путей - защита от "zip bomb": маленький файл на диске, который разворачивается
 * в гигабайты в памяти. {@link #MAX_ARCHIVE_FILE_SIZE}/{@link #MAX_ENTRY_COUNT} отсеивают
 * архив ещё до чтения содержимого, а {@link #readBounded} никогда не доверяет размеру,
 * заявленному в заголовке записи ({@link ZipEntry#getSize()} - его легко подделать), а считает
 * реально прочитанные байты и обрывается, как только становится больше лимита.
 */
public final class ClothingArchiveSecurity {

    /** Максимальный размер самого файла архива на диске. */
    public static final long MAX_ARCHIVE_FILE_SIZE = 64L * 1024 * 1024;

    /** Максимальное число записей в архиве. */
    public static final int MAX_ENTRY_COUNT = 2000;

    /** Максимальный распакованный размер одной записи, читаемой в память. */
    public static final long MAX_ENTRY_SIZE = 32L * 1024 * 1024;

    /** Максимальный суммарный распакованный объём за один разбор всего архива. */
    public static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 128L * 1024 * 1024;

    private ClothingArchiveSecurity() {
    }

    /** Бюджет суммарного распакованного объёма на один архив - создаётся один раз в начале {@link ClothingArchiveParser#parse}. */
    static final class ReadBudget {
        private long remaining;

        ReadBudget(long total) {
            this.remaining = total;
        }

        void consume(long bytes, String archiveName) throws IOException {
            remaining -= bytes;
            if (remaining < 0) {
                throw new IOException("архив '" + archiveName + "' превысил суммарный лимит распакованных данных "
                        + MAX_TOTAL_UNCOMPRESSED_SIZE + " байт (подозрение на zip bomb)");
            }
        }
    }

    public static boolean isArchiveFileSizeSafe(Path archivePath) throws IOException {
        return Files.size(archivePath) <= MAX_ARCHIVE_FILE_SIZE;
    }

    public static boolean isEntryCountSafe(ZipFile zip) {
        return zip.size() <= MAX_ENTRY_COUNT;
    }

    /**
     * Имя записи архива (или значение поля вроде {@code texture}/{@code icon} в item.json,
     * которое станет частью такого имени) безопасно, если это простой относительный путь без
     * выхода за пределы архива: без абсолютных путей, без {@code \}, без нулевых байт и без
     * сегмента {@code ..} в любом месте пути (сегмент вида {@code ..secret} - не traversal и
     * разрешён, важен именно отдельный сегмент {@code ..}).
     */
    public static boolean isEntryNameSafe(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return false;
        }
        if (entryName.indexOf('\0') >= 0 || entryName.indexOf('\\') >= 0) {
            return false;
        }
        if (entryName.startsWith("/")) {
            return false;
        }
        for (String segment : entryName.split("/", -1)) {
            if (segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Читает содержимое zip-записи в память с жёстким лимитом байт, никогда не доверяя
     * заявленному в заголовке размеру - см. класс-javadoc. Дополнительно списывает прочитанное
     * с общего {@code budget} архива, чтобы много некрупных записей суммарно не превысили
     * {@link #MAX_TOTAL_UNCOMPRESSED_SIZE}.
     */
    static byte[] readBounded(ZipFile zip, ZipEntry entry, ReadBudget budget, String archiveName) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            int initialCapacity = (int) Math.max(64, Math.min(entry.getSize(), 8192));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(initialCapacity);
            byte[] chunk = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) >= 0) {
                total += read;
                if (total > MAX_ENTRY_SIZE) {
                    throw new IOException("запись '" + entry.getName() + "' в архиве '" + archiveName
                            + "' превышает предельный размер " + MAX_ENTRY_SIZE + " байт (подозрение на zip bomb)");
                }
                buffer.write(chunk, 0, read);
            }
            budget.consume(total, archiveName);
            return buffer.toByteArray();
        }
    }
}

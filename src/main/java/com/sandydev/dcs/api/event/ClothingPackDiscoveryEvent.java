package com.sandydev.dcs.api.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Отправляется один раз при сканировании одежды - до того, как прочитан хоть один архив.
 * Позволяет аддонам подмешать свои архивы одежды (например, временный файл, распакованный
 * из ресурсов собственного jar) в общий конвейер загрузки {@code ClothingLoader}, не трогая
 * папку {@code .minecraft/clothes/} самого пользователя.
 * <p>
 * Отправляется на {@link NeoForge#EVENT_BUS} из обработчика {@code FMLConstructModEvent} этого
 * мода - то есть уже после того, как отработали конструкторы ВСЕХ модов. Слушатель этого
 * события в аддоне нужно регистрировать в конструкторе самого аддона (как обычно и делается
 * для {@code NeoForge.EVENT_BUS}) - тогда порядок конструирования модов друг относительно
 * друга не важен: аддон уже успеет подписаться к моменту отправки этого события, в каком бы
 * порядке моды ни грузились.
 * <p>
 * Каждый добавленный архив проходит те же проверки безопасности и тот же разбор (включая
 * необязательный {@code manifest.json}), что и архивы из {@code clothes/} - см. {@code
 * ClothingArchiveSecurity} и {@code ClothingArchiveParser}.
 */
public final class ClothingPackDiscoveryEvent extends Event {

    private final List<Path> discoveredArchives;
    private final List<Path> extraArchives = new ArrayList<>();

    public ClothingPackDiscoveryEvent(List<Path> discoveredArchives) {
        this.discoveredArchives = List.copyOf(discoveredArchives);
    }

    /** Архивы, уже найденные в {@code .minecraft/clothes/} на момент отправки события. */
    public List<Path> getDiscoveredArchives() {
        return discoveredArchives;
    }

    /** Добавляет ещё один архив ({@code .cloth} или {@code .zip}) в очередь на разбор. */
    public void addArchive(Path archive) {
        extraArchives.add(Objects.requireNonNull(archive, "archive"));
    }

    /** Архивы, добавленные аддонами через {@link #addArchive(Path)}. */
    public List<Path> getExtraArchives() {
        return List.copyOf(extraArchives);
    }
}

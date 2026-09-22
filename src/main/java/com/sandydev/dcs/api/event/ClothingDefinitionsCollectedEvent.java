package com.sandydev.dcs.api.event;

import com.sandydev.dcs.clothing.ClothingDefinition;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Отправляется один раз после того, как все архивы одежды (из {@code .minecraft/clothes/} и
 * добавленные через {@link ClothingPackDiscoveryEvent}) разобраны в {@link ClothingDefinition},
 * но ДО того, как они попадут в {@code ClothingRegistry} и на них будут созданы игровые
 * предметы.
 * <p>
 * {@link #getDefinitions()} возвращает изменяемый список, с которым можно делать что угодно:
 * добавить свои определения в обход архивов вообще (например, собранные из ресурсов
 * собственного jar-файла аддона - текстуры {@link ClothingDefinition#textureBytes()}/{@link
 * ClothingDefinition#iconBytes()} должны быть валидным PNG нужного размера, как и для обычных
 * паков), убрать чужие ({@code removeIf}), или заменить существующее определение на своё с тем
 * же {@link ClothingDefinition#id()} (тогда при регистрации выигрывает то, что осталось в
 * списке последним под этим id).
 * <p>
 * Как и {@link ClothingPackDiscoveryEvent}, отправляется на {@link NeoForge#EVENT_BUS} во время
 * {@code FMLConstructModEvent} - слушатель в аддоне нужно регистрировать в конструкторе аддона.
 */
public final class ClothingDefinitionsCollectedEvent extends Event {

    private final List<ClothingDefinition> definitions;

    public ClothingDefinitionsCollectedEvent(List<ClothingDefinition> definitions) {
        this.definitions = new ArrayList<>(definitions);
    }

    /** Изменяемый список - можно свободно add()/remove()/removeIf() и т.д. */
    public List<ClothingDefinition> getDefinitions() {
        return definitions;
    }
}

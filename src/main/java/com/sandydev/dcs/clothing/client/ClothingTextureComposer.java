package com.sandydev.dcs.clothing.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.sandydev.dcs.clothing.ClothingDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

/**
 * Композиция итоговой skin-текстуры игрока: оригинальный скин + текстуры одежды = финальный скин.
 * <p>
 * Работаем всегда со свежей копией оригинального скина, не накапливаем изменения между кадрами -
 * см. {@link #compose}. Слои одежды накладываются по возрастанию priority (нижний слой первым),
 * и у каждого слоя меняются только пиксели с alpha &gt; 0. Перед копированием слоя
 * очищается только соответствующий overlay старого содержимого, если одежда действительно
 * рисует этот base-пиксель. Так overlay самой одежды не стирается.
 * <p>
 * Раскладка UV 64x64 skin-формата стабильна с версии 1.8 и не зависит от версии Curios/NeoForge:
 * для каждой из 6 частей тела прямоугольник первого слоя связан с прямоугольником второго слоя
 * фиксированным смещением - см. {@link #LAYER_PAIRS}.
 */
public final class ClothingTextureComposer {

    private static final int SKIN_SIZE = 64;

    /**
     * Связь одного прямоугольника базового слоя с соответствующим прямоугольником
     * overlay-слоя в стандартной 64x64 player-skin UV-развёртке.
     *
     * Важно: нельзя связывать целые 16x16/32x16 блоки - внутри них находятся
     * разные грани соседних частей тела. Именно такая широкая маска была причиной
     * случайного удаления пикселей в других местах текстуры.
     */
    private record LayerPair(int baseX, int baseY, int w, int h, int overlayX, int overlayY) {
    }

    /**
     * Точные соответствия base -> overlay для всех шести граней каждой части тела.
     *
     * Формат 64x64:
     * - head:       base  (0..31, 0..15)   -> hat    (32..63, 0..15)
     * - torso:      base (16..39,16..31)   -> jacket (16..39,32..47)
     * - right arm:  base (40..55,16..31)   -> sleeve (40..55,32..47)
     * - left arm:   base (32..47,48..63)   -> sleeve (48..63,48..63)
     * - right leg:  base  (0..15,16..31)   -> pants  (0..15,32..47)
     * - left leg:   base (16..31,48..63)   -> pants   (0..15,48..63)
     */
    private static final List<LayerPair> LAYER_PAIRS = List.of(
            // Head -> hat
            new LayerPair(8, 0, 8, 8, 40, 0),
            new LayerPair(16, 0, 8, 8, 48, 0),
            new LayerPair(0, 8, 8, 8, 32, 8),
            new LayerPair(8, 8, 8, 8, 40, 8),
            new LayerPair(16, 8, 8, 8, 48, 8),
            new LayerPair(24, 8, 8, 8, 56, 8),

            // Torso -> jacket
            new LayerPair(20, 16, 8, 4, 20, 32),
            new LayerPair(28, 16, 8, 4, 28, 32),
            new LayerPair(16, 20, 4, 12, 16, 36),
            new LayerPair(20, 20, 8, 12, 20, 36),
            new LayerPair(28, 20, 4, 12, 28, 36),
            new LayerPair(32, 20, 8, 12, 32, 36),

            // Right arm -> right sleeve
            new LayerPair(44, 16, 4, 4, 44, 32),
            new LayerPair(48, 16, 4, 4, 48, 32),
            new LayerPair(40, 20, 4, 12, 40, 36),
            new LayerPair(44, 20, 4, 12, 44, 36),
            new LayerPair(48, 20, 4, 12, 48, 36),
            new LayerPair(52, 20, 4, 12, 52, 36),

            // Left arm -> left sleeve
            new LayerPair(36, 48, 4, 4, 52, 48),
            new LayerPair(40, 48, 4, 4, 56, 48),
            new LayerPair(32, 52, 4, 12, 48, 52),
            new LayerPair(36, 52, 4, 12, 52, 52),
            new LayerPair(40, 52, 4, 12, 56, 52),
            new LayerPair(44, 52, 4, 12, 60, 52),

            // Right leg -> right pants
            new LayerPair(4, 16, 4, 4, 4, 32),
            new LayerPair(8, 16, 4, 4, 8, 32),
            new LayerPair(0, 20, 4, 12, 0, 36),
            new LayerPair(4, 20, 4, 12, 4, 36),
            new LayerPair(8, 20, 4, 12, 8, 36),
            new LayerPair(12, 20, 4, 12, 12, 36),

            // Left leg -> left pants
            new LayerPair(20, 48, 4, 4, 4, 48),
            new LayerPair(24, 48, 4, 4, 8, 48),
            new LayerPair(16, 52, 4, 12, 0, 52),
            new LayerPair(20, 52, 4, 12, 4, 52),
            new LayerPair(24, 52, 4, 12, 8, 52),
            new LayerPair(28, 52, 4, 12, 12, 52)
    );
    private ClothingTextureComposer() {
    }

    /**
     * Строит итоговую 64x64 текстуру. Никогда не изменяет {@code originalSkin} - возвращает
     * новый {@link NativeImage}, вызывающая сторона отвечает за закрытие как исходного, так
     * и промежуточных изображений одежды (сам метод закрывает то, что декодирует сам).
     *
     * @param originalSkin        оригинальный скин игрока (64x64), не модифицируется.
     * @param equippedByPriority  надетая одежда, ОБЯЗАТЕЛЬНО отсортированная по возрастанию
     *                            priority (см. {@code ClothingManager#getEquippedClothingSortedByPriority}).
     */
    public static NativeImage compose(NativeImage originalSkin, List<ClothingDefinition> equippedByPriority) {
        NativeImage result = new NativeImage(NativeImage.Format.RGBA, SKIN_SIZE, SKIN_SIZE, false);
        copyInto(originalSkin, result);

        for (ClothingDefinition definition : equippedByPriority) {
            applyLayer(result, definition);
        }
        return result;
    }

    private static void copyInto(NativeImage source, NativeImage target) {
        int w = Math.min(source.getWidth(), SKIN_SIZE);
        int h = Math.min(source.getHeight(), SKIN_SIZE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                target.setPixelRGBA(x, y, source.getPixelRGBA(x, y));
            }
        }
    }

    private static void applyLayer(NativeImage result, ClothingDefinition definition) {
        NativeImage clothing;
        try {
            clothing = NativeImage.read(new ByteArrayInputStream(definition.textureBytes()));
        } catch (IOException e) {
            // Уже провалидировано ClothingArchiveParser при загрузке архива - сюда доходить не должно,
            // но на всякий случай не роняем рендер игрока из-за одного повреждённого предмета.
            return;
        }

        try {
            /*
             * Сначала убираем overlay предыдущего содержимого там, где текущая одежда
             * реально рисует base-фасад. Делать это нужно ДО копирования самой одежды.
             *
             * Раньше очистка шла сразу после setPixelRGBA(). При таком порядке пиксели
             * overlay самой же одежды успевали записаться раньше соответствующего base-пикселя
             * и затем стирались. На левой штанине это проявлялось особенно заметно.
             */
            clearMappedOverlaysForLayer(result, clothing);

            int w = Math.min(clothing.getWidth(), SKIN_SIZE);
            int h = Math.min(clothing.getHeight(), SKIN_SIZE);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pixel = clothing.getPixelRGBA(x, y);
                    int alpha = (pixel >>> 24) & 0xFF;
                    if (alpha <= 0) {
                        continue;
                    }
                    result.setPixelRGBA(x, y, pixel);
                }
            }
        } finally {
            clothing.close();
        }
    }

    private static void clearMappedOverlaysForLayer(NativeImage result, NativeImage clothing) {
        int w = Math.min(clothing.getWidth(), SKIN_SIZE);
        int h = Math.min(clothing.getHeight(), SKIN_SIZE);

        for (LayerPair pair : LAYER_PAIRS) {
            int maxX = Math.min(pair.baseX() + pair.w(), w);
            int maxY = Math.min(pair.baseY() + pair.h(), h);
            if (pair.baseX() >= maxX || pair.baseY() >= maxY) {
                continue;
            }

            for (int y = pair.baseY(); y < maxY; y++) {
                for (int x = pair.baseX(); x < maxX; x++) {
                    int pixel = clothing.getPixelRGBA(x, y);
                    if (((pixel >>> 24) & 0xFF) <= 0) {
                        continue;
                    }

                    int ox = pair.overlayX() + (x - pair.baseX());
                    int oy = pair.overlayY() + (y - pair.baseY());
                    int existing = result.getPixelRGBA(ox, oy);
                    result.setPixelRGBA(ox, oy, existing & 0x00FFFFFF);
                }
            }
        }
    }

}

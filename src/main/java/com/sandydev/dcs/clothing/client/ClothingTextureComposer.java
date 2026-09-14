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
 * и у каждого слоя меняются только пиксели с alpha &gt; 0. Если слой одежды перекрывает пиксель
 * первого (базового) слоя скина, соответствующий пиксель второго слоя (hat/jacket/sleeve/pants
 * overlay) обнуляется по alpha, чтобы не "просвечивал" поверх одежды.
 * <p>
 * Раскладка UV 64x64 skin-формата стабильна с версии 1.8 и не зависит от версии Curios/NeoForge:
 * для каждой из 6 частей тела прямоугольник первого слоя связан с прямоугольником второго слоя
 * фиксированным смещением - см. {@link #LAYER_PAIRS}.
 */
public final class ClothingTextureComposer {

    private static final int SKIN_SIZE = 64;

    /** Пара прямоугольников (первый слой -> второй/overlay слой) одной части тела 64x64-скина. */
    private record LayerPair(int baseX, int baseY, int w, int h, int overlayX, int overlayY) {
    }

    private static final List<LayerPair> LAYER_PAIRS = List.of(
            new LayerPair(0, 0, 32, 16, 32, 0),     // голова -> шляпа (hat)
            new LayerPair(16, 16, 24, 16, 16, 32),  // торс -> куртка (jacket)
            new LayerPair(40, 16, 16, 16, 40, 32),  // правая рука -> правый рукав
            new LayerPair(32, 48, 16, 16, 48, 48),  // левая рука -> левый рукав
            new LayerPair(0, 16, 16, 16, 0, 32),    // правая нога -> правая штанина
            new LayerPair(16, 48, 16, 16, 0, 48)    // левая нога -> левая штанина
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
                    clearMappedOverlayPixel(result, x, y);
                }
            }
        } finally {
            clothing.close();
        }
    }

    /**
     * Если (x, y) попадает в прямоугольник первого слоя одной из частей тела - обнуляет alpha
     * соответствующего пикселя во втором (overlay) слое той же части тела, чтобы он не
     * просвечивал поверх только что наложенной одежды. Точка вне известных прямоугольников
     * (например уже сам overlay-регион) не трогается - см. класс-описание.
     */
    private static void clearMappedOverlayPixel(NativeImage image, int x, int y) {
        for (LayerPair pair : LAYER_PAIRS) {
            if (x >= pair.baseX() && x < pair.baseX() + pair.w()
                    && y >= pair.baseY() && y < pair.baseY() + pair.h()) {
                int ox = pair.overlayX() + (x - pair.baseX());
                int oy = pair.overlayY() + (y - pair.baseY());
                int existing = image.getPixelRGBA(ox, oy);
                int cleared = existing & 0x00FFFFFF; // alpha = 0, остальные каналы не важны
                image.setPixelRGBA(ox, oy, cleared);
                return;
            }
        }
    }
}

package com.netherrack.server.player;

import org.cloudburstmc.protocol.bedrock.data.skin.AnimatedTextureType;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationData;
import org.cloudburstmc.protocol.bedrock.data.skin.AnimationExpressionType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceData;
import org.cloudburstmc.protocol.bedrock.data.skin.PersonaPieceTintData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Builds a player's skin from the client data their client sends at login (the JSON
 * payload of LoginPacket's client JWT). Other players' clients need the full skin to draw
 * the player, so it's passed on as-is.
 * <p>
 * Images and geometry are base64 in the client data; everything else is plain JSON. A
 * missing field falls back to an empty value rather than failing the whole skin.
 */
public final class SkinParser {

    private static final String DEFAULT_GEOMETRY = "{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}";

    private SkinParser() {
    }

    public static SerializedSkin parse(Map<String, Object> clientData) {
        return SerializedSkin.builder()
                .skinId(string(clientData, "SkinId"))
                .playFabId(string(clientData, "PlayFabId"))
                .skinResourcePatch(base64String(clientData, "SkinResourcePatch"))
                .skinData(image(clientData, "SkinImageWidth", "SkinImageHeight", "SkinData"))
                .animations(animations(clientData))
                .capeData(image(clientData, "CapeImageWidth", "CapeImageHeight", "CapeData"))
                .geometryData(base64String(clientData, "SkinGeometryData"))
                .geometryDataEngineVersion(base64String(clientData, "SkinGeometryDataEngineVersion"))
                .animationData(base64String(clientData, "SkinAnimationData"))
                .premium(bool(clientData, "PremiumSkin"))
                .persona(bool(clientData, "PersonaSkin"))
                .capeOnClassic(bool(clientData, "CapeOnClassicSkin"))
                .primaryUser(true)
                .capeId(string(clientData, "CapeId"))
                .fullSkinId(string(clientData, "SkinId"))
                .armSize(string(clientData, "ArmSize"))
                .skinColor(string(clientData, "SkinColor"))
                .personaPieces(personaPieces(clientData))
                .tintColors(tintColors(clientData))
                .overridingPlayerAppearance(bool(clientData, "OverrideSkin"))
                .trusted(true)
                .build();
    }

    /**
     * A plain stand-in for players whose client data couldn't be read: a blank 64x64
     * skin on the standard player model, so they still show up as something.
     */
    public static SerializedSkin fallback() {
        return SerializedSkin.builder()
                .skinId("netherrack.fallback")
                .playFabId("")
                .skinResourcePatch(DEFAULT_GEOMETRY)
                .skinData(ImageData.of(64, 64, new byte[64 * 64 * 4]))
                .animations(List.of())
                .capeData(ImageData.EMPTY)
                .geometryData("")
                .geometryDataEngineVersion("")
                .animationData("")
                .primaryUser(true)
                .capeId("")
                .fullSkinId("netherrack.fallback")
                .armSize("wide")
                .skinColor("#0")
                .personaPieces(List.of())
                .tintColors(List.of())
                .trusted(true)
                .build();
    }

    private static List<AnimationData> animations(Map<String, Object> clientData) {
        List<AnimationData> animations = new ArrayList<>();
        for (Map<String, Object> animation : objects(clientData, "AnimatedImageData")) {
            animations.add(new AnimationData(
                    image(animation, "ImageWidth", "ImageHeight", "Image"),
                    AnimatedTextureType.from(integer(animation, "Type")),
                    (float) number(animation, "Frames"),
                    AnimationExpressionType.from(integer(animation, "AnimationExpression"))));
        }
        return animations;
    }

    private static List<PersonaPieceData> personaPieces(Map<String, Object> clientData) {
        List<PersonaPieceData> pieces = new ArrayList<>();
        for (Map<String, Object> piece : objects(clientData, "PersonaPieces")) {
            pieces.add(new PersonaPieceData(
                    string(piece, "PieceId"),
                    string(piece, "PieceType"),
                    string(piece, "PackId"),
                    bool(piece, "IsDefault"),
                    string(piece, "ProductId")));
        }
        return pieces;
    }

    private static List<PersonaPieceTintData> tintColors(Map<String, Object> clientData) {
        List<PersonaPieceTintData> tints = new ArrayList<>();
        for (Map<String, Object> tint : objects(clientData, "PieceTintColors")) {
            List<String> colors = new ArrayList<>();
            if (tint.get("Colors") instanceof List<?> list) {
                for (Object color : list) {
                    colors.add(String.valueOf(color));
                }
            }
            tints.add(new PersonaPieceTintData(string(tint, "PieceType"), colors));
        }
        return tints;
    }

    private static ImageData image(Map<String, Object> data, String widthKey, String heightKey, String imageKey) {
        byte[] image = base64(data, imageKey);
        if (image.length == 0) {
            return ImageData.EMPTY;
        }
        return ImageData.of(integer(data, widthKey), integer(data, heightKey), image);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objects(Map<String, Object> data, String key) {
        List<Map<String, Object>> objects = new ArrayList<>();
        if (data.get(key) instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    objects.add((Map<String, Object>) map);
                }
            }
        }
        return objects;
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : "";
    }

    private static byte[] base64(Map<String, Object> data, String key) {
        String value = string(data, key);
        return value.isEmpty() ? new byte[0] : Base64.getDecoder().decode(value);
    }

    private static String base64String(Map<String, Object> data, String key) {
        return new String(base64(data, key), StandardCharsets.UTF_8);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data.get(key) instanceof Boolean value && value;
    }

    private static double number(Map<String, Object> data, String key) {
        return data.get(key) instanceof Number value ? value.doubleValue() : 0;
    }

    private static int integer(Map<String, Object> data, String key) {
        return (int) number(data, key);
    }
}

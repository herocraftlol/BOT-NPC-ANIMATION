package com.botnpc.plugin.skin;

/**
 * Représente une texture de skin encodée telle qu'attendue par le protocole
 * Minecraft : une valeur base64 (JSON contenant l'URL de texture) et,
 * si disponible, une signature Mojang/Mineskin.
 */
public class SkinData {

    private final String value;
    private final String signature; // peut être null pour une texture non signée

    public SkinData(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public boolean hasSignature() {
        return signature != null && !signature.isEmpty();
    }
}

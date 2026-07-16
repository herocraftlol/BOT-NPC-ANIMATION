package com.botnpc.plugin.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Récupère une texture de skin de deux façons :
 *  - à partir d'un pseudo Minecraft existant (API Mojang, texture signée officielle)
 *  - à partir d'une URL d'image quelconque (API publique Mineskin.org, qui génère
 *    une texture signée à partir d'une image PNG hébergée n'importe où)
 *
 * Toutes les méthodes sont asynchrones (CompletableFuture) : NE JAMAIS les appeler
 * sur le thread principal du serveur, elles font des requêtes HTTP bloquantes.
 */
public class SkinFetcher {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Logger logger;

    public SkinFetcher(Logger logger) {
        this.logger = logger;
    }

    /**
     * Détecte automatiquement si l'entrée est une URL ou un pseudo,
     * et retourne la texture correspondante.
     */
    public CompletableFuture<SkinData> resolve(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return fromUrl(input);
        }
        return fromPlayerName(input);
    }

    /**
     * Récupère la texture officielle d'un joueur existant via l'API Mojang.
     */
    public CompletableFuture<SkinData> fromPlayerName(String playerName) {
        String uuidEndpoint = "https://api.mojang.com/users/profiles/minecraft/" + playerName;

        return sendGet(uuidEndpoint).thenCompose(uuidBody -> {
            if (uuidBody == null) {
                throw new SkinResolutionException("Pseudo Minecraft introuvable : " + playerName);
            }
            JsonObject uuidJson = JsonParser.parseString(uuidBody).getAsJsonObject();
            String uuid = uuidJson.get("id").getAsString();

            String profileEndpoint = "https://sessionserver.mojang.com/session/minecraft/profile/"
                    + uuid + "?unsigned=false";

            return sendGet(profileEndpoint).thenApply(profileBody -> {
                if (profileBody == null) {
                    throw new SkinResolutionException("Impossible de récupérer le profil pour : " + playerName);
                }
                JsonObject profileJson = JsonParser.parseString(profileBody).getAsJsonObject();
                JsonObject textureProperty = profileJson.getAsJsonArray("properties")
                        .get(0).getAsJsonObject();

                String value = textureProperty.get("value").getAsString();
                String signature = textureProperty.has("signature")
                        ? textureProperty.get("signature").getAsString()
                        : null;

                return new SkinData(value, signature);
            });
        });
    }

    /**
     * Convertit une image (URL directe vers un .png de skin 64x64) en texture
     * signée utilisable, via l'API publique gratuite mineskin.org.
     */
    public CompletableFuture<SkinData> fromUrl(String imageUrl) {
        String endpoint = "https://api.mineskin.org/generate/url";

        String jsonBody = "{\"url\":\"" + imageUrl.replace("\"", "\\\"") + "\",\"visibility\":1}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("User-Agent", "BotNPCPlugin/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        logger.warning("Mineskin a répondu " + response.statusCode() + " : " + response.body());
                        throw new SkinResolutionException("Mineskin n'a pas pu générer le skin depuis cette URL (code "
                                + response.statusCode() + "). Vérifie que l'URL pointe bien vers un PNG 64x64 ou 64x32.");
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject texture = json.getAsJsonObject("data").getAsJsonObject("texture");
                    String value = texture.get("value").getAsString();
                    String signature = texture.has("signature") ? texture.get("signature").getAsString() : null;

                    return new SkinData(value, signature);
                });
    }

    private CompletableFuture<String> sendGet(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "BotNPCPlugin/1.0")
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 204 || response.body() == null || response.body().isBlank()) {
                        return null;
                    }
                    if (response.statusCode() / 100 != 2) {
                        return null;
                    }
                    return response.body();
                });
    }

    /** Exception dédiée pour remonter un message clair jusqu'à la commande. */
    public static class SkinResolutionException extends RuntimeException {
        public SkinResolutionException(String message) {
            super(message);
        }
    }
}

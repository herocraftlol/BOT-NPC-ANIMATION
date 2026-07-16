# BotNPCPlugin

Plugin Paper/Spigot **autonome** (aucune autre dépendance de type Citizens ou
ProtocolLib à installer sur le serveur) qui crée de faux joueurs animés :

- **Marche sur un chemin** défini par des points, en regardant autour de lui pendant la marche.
- **Position assise** sur le bloc sous ses pieds, tête levée vers le ciel.
- **Duel à l'épée** scénarisé entre deux bots (approche, coups, réactions).

Skins personnalisables par **pseudo Minecraft existant** (texture officielle
récupérée via l'API Mojang) ou par **URL d'image PNG** (skin généré via
l'API publique gratuite [mineskin.org](https://mineskin.org)).

---

## ⚠️ À lire avant de commencer

Pour exister sans dépendre d'un autre plugin, ce plugin manipule directement
les classes internes du serveur (le "NMS"). Concrètement, ça veut dire deux
choses importantes :

1. **Il faut compiler contre le jar Spigot obtenu via BuildTools**, pas
   contre `paper-api` seul (qui ne donne pas accès au NMS). C'est fait pour
   dans le `pom.xml` fourni.
2. **Le code cible la 1.21.x.** Bonne nouvelle : depuis la 1.20.5, Spigot a
   supprimé le suffixe de version dans le package CraftBukkit (fini les
   `org.bukkit.craftbukkit.v1_20_R3...`, c'est maintenant juste
   `org.bukkit.craftbukkit.*`), donc ce code n'a **pas besoin d'être modifié**
   pour passer d'une révision 1.21.x à une autre (1.21 → 1.21.8 par exemple).
   Il faut juste faire correspondre la **version exacte** dans `pom.xml`
   (voir ci-dessous) à celle de ton serveur. Les classes internes profondes
   (`net.minecraft.*`, ex: `ClientboundAddEntityPacket`) restent globalement
   stables entre versions mineures, mais en cas d'erreur de compilation sur
   un point précis, c'est généralement signe qu'un nom de méthode a
   légèrement changé — l'autocomplétion de ton IDE contre le bon jar Spigot
   te montrera le nom à jour.

C'est le prix à payer pour éviter toute dépendance externe — c'est
exactement la technique qu'utilisent les plugins NPC "légers" en interne.

---

## Compilation

1. **Trouve la version exacte de ton serveur** (tape `/version` en jeu, ou
   regarde dans les logs au démarrage — ex: `1.21.4`, `1.21.8`...).

2. **Génère le jar Spigot avec BuildTools** pour CETTE version précise :
   ```bash
   wget https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar
   java -jar BuildTools.jar --rev 1.21.4
   ```
   (remplace `1.21.4` par ta version exacte). Ça installe automatiquement
   `org.spigotmc:spigot:<ta-version>-R0.1-SNAPSHOT` dans ton dépôt Maven
   local (`~/.m2`).

3. **Mets à jour la version dans `pom.xml`** pour qu'elle corresponde
   exactement (ligne `<version>1.21.4-R0.1-SNAPSHOT</version>`).

4. **Compile le plugin** :
   ```bash
   mvn clean package
   ```
   Le jar final se trouve dans `target/BotNPCPlugin.jar`.

5. Place-le dans le dossier `plugins/` de ton serveur **Paper ou Spigot
   1.21.x**, puis redémarre.

---

## Utilisation en jeu

Toutes les commandes nécessitent la permission `botnpc.admin` (accordée aux
OP par défaut).

```
/bot create <id> <pseudo|url_image>     - crée un bot à ta position
/bot skin <id> <pseudo|url_image>       - change le skin d'un bot existant
/bot remove <id>                        - supprime un bot
/bot list                               - liste tous les bots
/bot tp <id>                            - téléporte un bot à ta position

/bot path <id> add                      - ajoute ta position au chemin du bot
/bot path <id> clear                    - vide le chemin du bot
/bot path <id> start                    - le bot marche en boucle sur le chemin en regardant autour

/bot sit <id>                           - assoit le bot sur le bloc sous toi, regard au ciel

/bot duel <id1> <id2>                   - lance un duel à l'épée scénarisé entre deux bots

/bot stop <id>                          - arrête l'animation en cours d'un bot
```

### Exemple : un bot qui patrouille

```
/bot create garde1 Notch
[se déplacer au point 1] /bot path garde1 add
[se déplacer au point 2] /bot path garde1 add
[se déplacer au point 3] /bot path garde1 add
/bot path garde1 start
```

### Exemple : un bot avec un skin personnalisé par image

```
/bot create pierre https://example.com/mon-skin.png
```
(l'image doit être un PNG de skin classique, 64x64 ou 64x32 pixels)

### Exemple : deux bots qui se battent

```
/bot create chevalier1 Notch
/bot create chevalier2 Jeb_
/bot duel chevalier1 chevalier2
```

---

## Limites connues (et pistes d'amélioration)

- Les rotations de tête sont simplifiées : pour "regarder autour" pendant la
  marche, le bot tourne légèrement la tête indépendamment du corps, mais la
  précision est volontairement approximative pour rester simple et fiable.
- Le duel est une **chorégraphie visuelle** (les bots n'ont pas de vraie vie
  ni de vrais dégâts) — idéal pour une mise en scène, pas pour un vrai combat calculé.
- La sauvegarde (`bots.yml`, générée automatiquement) restaure la position et
  le skin des bots au redémarrage, mais pas l'animation en cours : il faut
  relancer `/bot path ... start`, `/bot sit ...` ou `/bot duel ...` après un
  redémarrage.
- Les skins par URL passent par l'API gratuite mineskin.org (limitée en
  requêtes/minute) — évite de changer les skins en boucle rapide.
- `api-version: '1.21'` dans `plugin.yml` suffit pour Paper/Spigot, mais il
  faut quand même faire correspondre la version Spigot exacte dans `pom.xml`
  comme expliqué plus haut.

Si tu veux que j'adapte le plugin à une autre version précise du serveur, ou
que j'ajoute d'autres animations (ex: bot qui danse, qui pêche, qui salue),
dis-le-moi.

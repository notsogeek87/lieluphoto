# LieluPhoto

[English](./README_EN.md)

LieluPhoto est une application d'appareil photo Android, centrée sur la photo, qui retrouve la prise en main et la qualité d'image d'un appareil photo numérique sans miroir moderne.

> **Fork de [PhotonCamera](https://github.com/bjzhou/photoncamera)** de bjzhou, publié sous licence Apache 2.0. Le travail de base (moteur de rendu, LUT, recettes de couleur, empilement multi-images, etc.) est le sien : merci à lui. Ce dépôt suit son évolution (voir [la synchronisation avec upstream](./docs/guides/sync-upstream.md)) et ajoute ses propres changements.

## Ce qui change par rapport à PhotonCamera

* **Identité** : application renommée LieluPhoto (`eu.lielu.photo`), sans Bugly.
* **Modes** : Photo · Portrait · Vidéo, et un interrupteur **Nuit** combinable avec Photo et Portrait.
* **Portrait** : bokeh par IA avec un cadran d'ouverture (ƒ/1 à ƒ/16) au niveau du zoom. Le modèle de profondeur est téléchargé au premier passage en Portrait.
* **IA d'amélioration** : débruitage (NAFNet) et restauration des visages (GFPGAN), activés par défaut. Les modèles sont proposés au téléchargement à l'ouverture, et un rappel propose de réactiver l'IA si elle a été coupée.
* **Mises à jour** : via les [releases GitHub](https://github.com/notsogeek87/lieluphoto/releases) (voir [le guide](./docs/guides/mise-a-jour-app.md)).
* **Passerelle MGC coupée** : aucune autre app (GCam modifiée ou autre) ne peut lire le filtre actif ni ajouter de photo à la galerie. Pour retoucher une photo d'une autre app, la partager vers LieluPhoto.

## Fonctionnalités héritées de PhotonCamera

* **LUT** : import et application de fichiers `.cube`, `.png` (Halfs/Fulls) et `.xmp`, avec aperçu en temps réel.
* **Recettes de couleur** : exposition, contraste, hautes lumières, ombres, saturation, température, teinte, vignettage, grain, effets artistiques, filtres avancés.
* **Photos animées (Motion Photos)** : adaptation à plusieurs constructeurs (Xiaomi, Samsung, Pixel, etc.).
* **Rafale rapide** : sans limite de nombre d'images, avec LUT appliquée en direct.
* **Synthèse multi-images** : empilement pour améliorer la qualité et réduire le bruit en gardant les détails.
* **Mode Phantom** : passe par l'appareil photo du système et applique le moteur de LUT.

## Installation

Télécharger l'APK de la dernière [release](https://github.com/notsogeek87/lieluphoto/releases/latest) et l'installer. Les versions suivantes sont proposées dans l'application.

### Signature

Les releases sont signées par la clé de publication LieluPhoto (`CN=LieluPhoto, O=Lielu, C=FR`), empreinte SHA-256 :

`5A:0B:BE:F8:AA:9F:28:16:90:30:64:7B:5E:C8:10:2B:47:A9:AC:05:E8:EF:AC:C4:48:58:3A:49:3D:9A:2D:C6`

Les versions jusqu'à 1.28.4.292 étaient signées par `app/debug.keystore`, une clé publique : pour passer à une version signée par la clé de publication, désinstaller l'ancienne une fois. `debug.keystore` ne sert plus qu'aux builds locaux et aux APK de test des branches sans secrets.

## Technique

* **UI** : Jetpack Compose
* **API caméra** : Camera2, sans compatibilité descendante
* **SDK minimum** : Android 11 (minSdk 30), code natif C++ (CMake)
* **Documentation** : [docs/README.md](./docs/README.md)

## Licence

Sauf composants tiers, modèles, polices, LUT et autres ressources qui portent leur propre licence dans ce dépôt, le code source est sous [licence Apache 2.0](./LICENSE), comme PhotonCamera.

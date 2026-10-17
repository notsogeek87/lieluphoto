# Guide : pastille bokeh du viseur

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : activer ou couper le bokeh (ouverture virtuelle) directement depuis le viseur.

## Utilisation

- La pastille est en haut à droite du viseur, dans tous les modes sauf vidéo.
- Bokeh actif : elle affiche `f/<valeur>`. Un appui désactive le bokeh.
- Bokeh inactif : elle affiche « Bokeh off » (fond plus transparent).
  - Si le modèle de profondeur est installé, un appui active le bokeh.
  - Sinon, un appui ouvre le dialogue de téléchargement (`DepthModelDownloadDialog`) : **télécharger** (environ 99 Mo, Depth Anything V2) ou **importer** un modèle compatible. Le bokeh s'active automatiquement dès que le modèle est installé.

- **Appui long** sur la pastille (bokeh actif) : ouvre un curseur pour régler l'ouverture (f/1, 1.4, 1.8, 2, 2.8, 4, 5.6, 8, 11, 16). Le choix s'applique tout de suite et devient l'ouverture par défaut (`setDefaultVirtualAperture`).

## Code

```kotlin
// ui/camera/CameraScreen.kt
if (state.isVirtualApertureEnabled) viewModel.setVirtualApertureAuto(false)
else if (DepthModelManager.isInstalled(context)) viewModel.setVirtualApertureAuto(true)
else { pendingBokehEnable = true; showDepthModelDownloadDialog = true }
```

Textes : `virtual_aperture_viewfinder_hint`, `virtual_aperture_viewfinder_off` (en, fr).

Réglage : `VirtualApertureDialog` (même fichier), liste `VirtualApertureStops`.

## Mode Portrait

Le sélecteur du bas propose `Photo · Nuit · Portrait · Vidéo`. **Portrait** = photo avec le bokeh activé (si le modèle de profondeur manque, le dialogue de téléchargement s'ouvre). Passer à Photo ou Nuit coupe le bokeh. Le mode affiché est déduit de l'état : photo + bokeh actif = Portrait, donc la pastille f/ et le sélecteur restent toujours cohérents.

## Balayage, Mpx et menus

- **Balayage horizontal du viseur** : change de mode (`Photo · Nuit · Portrait · Vidéo`), et plus de style/LUT. Les styles se changent avec la rangée de modèles.
- **Pastille « N Mpx »** (haut droite, en photo hors Pro) : un appui passe à la définition suivante (12 / 24 / 50 selon ce que le capteur propose). Réglage `captureMegapixels` ; la caméra est rouverte à chaque changement.
- **Réglages** : « Appareil photo », « Modèles et IA », « Application » en haut ; objectifs/mise au point, Pro/RAW, Phantom et sauvegarde sont repliés dans « Avancé ».

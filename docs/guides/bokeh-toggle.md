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

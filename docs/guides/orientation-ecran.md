# Guide : orientation de l'écran et aperçu caméra

## Pour qui / pourquoi

Pour le mainteneur : comprendre pourquoi l'aperçu tient compte de la rotation de l'écran, et quoi vérifier quand une image sort tournée sur un appareil pliable (Galaxy Z Fold) ou un grand écran.

## Le principe

- Camera2 livre les images de l'aperçu (texture OES) **droites dans l'orientation naturelle** de l'appareil.
- L'écran n'est pas toujours affiché dans cette orientation. `Display.getRotation()` donne l'écart : sur l'écran intérieur déplié d'un Z Fold, tenu en portrait, il n'est pas nul. Avec `targetSdk 36`, Android 16 ignore en plus le verrouillage portrait de l'écran caméra sur les grands écrans.
- L'aperçu est donc tourné de cette rotation. Toutes les textures intermédiaires du rendu sont dans le **repère de l'écran**.

## Où la rotation est prise en compte

| Endroit | Rôle |
|---|---|
| `OrientationObserver.displayRotationDegrees` | Rotation de l'écran (0, 90, 180, 270). Mise à jour par `CameraPreviewGL` via `DisplayManager` (pliage, dépliage, passage de 90° à 270°). |
| `LutRenderer.buildMvpMatrix` | Tourne l'image de la rotation de l'écran moins la calibration, à l'échelle en pixels pour garder les proportions. |
| `LutRenderer.getApplyRotation` | Live Photo : la rotation de l'écran s'ajoute pour revenir à l'orientation naturelle. |
| `CameraViewModel.capturePreviewThumbnailRotation` | Miniatures et masque Portrait tirés de l'aperçu : idem. |
| `PreviewMeteringMapper.mapPoint` | Toucher pour la mise au point et la mesure, mise au point sur l'œil : le point de l'écran est ramené à l'orientation naturelle avant la conversion vers le capteur. |

Les photos, les DNG et les vidéos ne passent pas par ces textures : leur orientation reste calculée dans l'orientation naturelle (`OrientationObserver.captureRotationDegrees`).

## Diagnostic

Le journal de l'app (Réglages, Aide et à propos, Visionneuse de logs) contient à l'ouverture de l'aperçu `Preview display rotation=…`, puis `Display rotation changed to …` à chaque changement. Une image tournée avec une rotation à 0 indique une autre cause : « Calibrage de la caméra » dans les réglages ou orientation du capteur mal déclarée par le fabricant.

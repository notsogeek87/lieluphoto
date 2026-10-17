# Guide : mode Nuit (et retrait des modes Pro / RAW)

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : obtenir des photos nettes et peu bruitées en basse lumière, comme les modes nuit de Samsung et Google, sans passer par le RAW.

## Utilisation

- Le sélecteur du bas propose `Photo · Nuit · Portrait · Vidéo` (balayage horizontal du viseur possible).
- **Nuit** = photo avec l'empilement multi-images YUV (« JPGmax ») renforcé : au moins 15 images alignées puis fusionnées. Garder le téléphone immobile pendant la prise.
- **Éclaircissement selon la scène** : la luminosité est estimée par ISO × temps de pose en exposition automatique. Plus la scène est sombre, plus chaque image est éclaircie, de 0 à +3 EV (`NightExposurePlanner`). En plein jour ou en exposition manuelle, rien n'est modifié. L'aperçu n'est pas éclairci : seule la photo l'est.
- **Pose adaptée au gyroscope** (`NightMotionMonitor`) : pose max = 0,005 / vitesse angulaire (rad/s), bornée entre 1/30 s et 1/3 s. Téléphone posé ou calé : jusqu'à 1/3 s par image (donc ISO bas, image plus propre) ; tenu à la main : environ 1/10 s ; en marchant : 1/30 s. Sans gyroscope : 1/8 s. L'ISO est le plus bas possible pour atteindre la luminosité visée.
- **15 images** empilées au minimum (`MultiFrameConfig.NIGHT_FRAME_COUNT`) : jusqu'à 5 s de prise quand le téléphone est posé, donc ne pas bouger.
- **Rattrapage en post-traitement** : ce que la pose et l'ISO ne peuvent pas apporter (limite du gyroscope, ISO max du capteur, ou caméra sans exposition manuelle comme souvent la caméra frontale) est rattrapé dans la recette de la photo : exposition en plus (jusqu'à +2 EV), ombres +0,5, hautes lumières −0,25. Un débruitage luma 0,6 / chroma 0,8 est aussi appliqué à l'export. Tout reste modifiable dans l'éditeur.
- Après chaque prise, un message affiche le réglage appliqué (`Night ×15 · ISO 1900 · 1/3 s · +3.0 EV capture · +0.0 EV post · 0.004 rad/s`) ou « bright scene, exposure unchanged ».
- Si le réglage « débruitage multi-images » est déjà supérieur à 12 images, la valeur de l'utilisateur est conservée.
- Le mode Nuit n'est pas persistant : à la réouverture de l'app, on revient en Photo. Il coupe le bokeh et le Live Photo (incompatibles).

## Pro / RAW retirés

- Plus de mode Pro dans le sélecteur ni d'entrée « Mode pro » dans les réglages.
- `isRawSupported` est forcé à `false` (`Camera2Controller`) et `useRaw` / `useRawMax` à `false` (`UserPreferencesRepository`, `CameraViewModel`) : aucune sortie RAW n'est ouverte.
- Le code du moteur RAW reste dans le dépôt (inutilisé) pour limiter les conflits au rebase sur `upstream`.

## Code

- `CameraViewModel.setNightMode` / `nightMode` ; `resolveNightFrameCount` ; `MultiFrameConfig.NIGHT_FRAME_COUNT`.
- `CameraScreen.kt` : `CameraShootingMode.NIGHT`, `CaptureModeSwitcher`.
- Texte : `capture_mode_night`.

## Limites / suite possible

Pistes : détection automatique de scène sombre, tone mapping des ombres en fin de traitement, éclaircissement de l'aperçu, compte à rebours « ne bouge pas ».

# Guide : mode Nuit (et retrait des modes Pro / RAW)

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : obtenir des photos nettes et peu bruitées en basse lumière, comme les modes nuit de Samsung et Google, sans passer par le RAW.

## Utilisation

- Le sélecteur du bas propose `Photo · Nuit · Portrait · Vidéo` (balayage horizontal du viseur possible).
- **Nuit** = photo avec l'empilement multi-images YUV (« JPGmax ») renforcé : au moins 12 images alignées puis fusionnées. Garder le téléphone immobile pendant la prise.
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

La durée de pose par image reste celle de l'exposition automatique. Pistes : pose plus longue pilotée par le gyroscope, détection automatique de scène sombre, réglage du rendu final.

# Guide : mode Nuit (et retrait des modes Pro / RAW)

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : obtenir des photos nettes et peu bruitées en basse lumière, comme les modes nuit de Samsung et Google, sans passer par le RAW.

## Utilisation

- Le sélecteur du bas propose `Photo · Nuit · Portrait · Vidéo` (balayage horizontal du viseur possible).
- **Nuit** = photo avec l'empilement multi-images YUV (« JPGmax ») renforcé : au moins 15 images alignées puis fusionnées. Garder le téléphone immobile pendant la prise.
- **Éclaircissement à la prise, modéré** : la luminosité est estimée par ISO × temps de pose en exposition automatique ; plus la scène est sombre, plus chaque image est éclaircie, de 0 à +1 EV (`NightExposurePlanner`). L'essentiel du gain à la prise vient de la pose longue et de l'ISO bas (voir ci-dessous), comme sur Samsung (comparatif : ISO 800 à 1/7 s contre ISO 2900 à 1/25 s chez nous, pour la même quantité de lumière). En plein jour ou en exposition manuelle, rien n'est modifié. 
- **Pose adaptée au gyroscope** (`NightMotionMonitor`) : pose max = 0,015 / vitesse angulaire (rad/s), bornée entre 1/30 s et 1/3 s (calé sur Samsung : 1/4 s à la main). Téléphone posé ou calé : jusqu'à 1/3 s par image (donc ISO bas, image plus propre) ; tenu à la main : environ 1/4 s ; en marchant : 1/30 s. Sans gyroscope : 1/8 s. L'ISO est le plus bas possible pour atteindre la luminosité visée.
- **15 images** empilées au minimum (`MultiFrameConfig.NIGHT_FRAME_COUNT`) : jusqu'à 5 s de prise quand le téléphone est posé, donc ne pas bouger.
- **Rendu Nuit (post-traitement), prudent** : exposition +0,4 EV (plus ce que la pose et l'ISO n'ont pas pu apporter, jusqu'à +0,5 EV), ombres +0,5, hautes lumières −0,15, débruitage luma 0,5 / chroma 1,0 à l'export. Le style (LUT) choisi est conservé. Le rendu n'est appliqué **que si l'empilement multi-images est réellement actif** : éclaircir une image unique ne fait qu'amplifier le bruit.
  - Historique : une version avec ombres +0,8 et style atténué à 60 % a donné des photos très bruitées et violettes (noirs bruités du capteur relevés), car l'exposition longue et l'empilement ne s'appliquaient pas (EXIF : ISO ~2900 à 1/25 s, identique à une photo normale).
- **Aperçu Nuit plus lumineux**, comme Samsung : le viseur applique le même rendu Nuit que la photo (exposition, ombres, hautes lumières), et la cadence de l'aperçu descend jusqu'à ~10 i/s (`CONTROL_AE_TARGET_FPS_RANGE`) pour que l'exposition auto pose plus longtemps. La cadence d'origine est restaurée en quittant Nuit.
- **ZSL désactivé** pour les prises Nuit, avec durée de trame = pose. Avec le ZSL, le Galaxy Z Fold renvoyait des images de l'aperçu en exposition auto et ignorait l'exposition manuelle (diagnostic : plan ISO 3200 à 1/11 s demandé, EXIF ISO 7227 à 1/25 s obtenu).
- **Diagnostic dans la photo** (`NightDiagnostics`) : en mode Nuit, le champ EXIF « Software » contient l'état réel de la prise, par exemple `PhotonCamera Night render[stack=true post=0.0 ctrlNight=true] cap[stack=true jpgmax=true scale=1.0 n=15 raw=false torch=false ae=true manual=true aeOff=true base=2878/40000000] plan[iso=900 t=125000000 lift=1,00 real=1,00 gyro=0.02 n=15 base=2878/40000000]`. Lisible avec n'importe quel lecteur EXIF : c'est ce qu'il faut regarder quand le rendu déçoit.
- Après chaque prise, un message affiche le réglage appliqué (`Night ×15 · ISO 1900 · 1/3 s · +1.0 EV capture · +0.0 EV post · 0.004 rad/s`) ou « bright scene, exposure unchanged ».
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

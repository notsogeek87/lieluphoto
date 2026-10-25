# Guide : mode Portrait, bokeh et ouverture

## Pour qui / pourquoi

Pour l'utilisateur de LieluPhoto : obtenir un arrière-plan flou (ouverture virtuelle) et choisir l'ouverture depuis le viseur. Pour le mainteneur : savoir où vit l'état du Portrait et comment le premier téléchargement du modèle est géré.

## Utilisation

- Le sélecteur du bas propose `Photo · Portrait · Vidéo` (balayage horizontal du viseur possible). **Portrait** = photo avec le bokeh activé ; passer à Photo ou Vidéo le coupe. La Nuit est un interrupteur à part, combinable avec Portrait (voir [mode Nuit](mode-nuit.md)).
- Le mode affiché est déduit de l'état : photo + bokeh actif (ou Portrait en préparation) = Portrait, donc le sélecteur et le viseur restent cohérents.
- **Cadran d'ouverture** (`ApertureDial`), au-dessus de la barre de zoom, avec la rangée de modèles dessous : diaphragme à six lames qui se referme quand le nombre f augmente, valeur `ƒ/2.8`, et règle graduée à toucher ou faire glisser (f/1, 1.4, 1.8, 2, 2.8, 4, 5.6, 8, 11, 16). Le choix s'applique tout de suite et devient l'ouverture par défaut (`setDefaultVirtualAperture`).
- **Pastille `f/<valeur>`** en haut à droite du viseur, affichée seulement quand le bokeh est actif : un appui coupe le bokeh, un appui long ouvre aussi le réglage de l'ouverture (`VirtualApertureDialog`).

## Premier passage en Portrait (modèle absent)

Le modèle de profondeur (Depth Anything V2, environ 99 Mo) n'est pas dans l'APK.

1. Choisir Portrait lance aussitôt le téléchargement et ouvre la fenêtre de progression (`DepthModelDownloadDialog`), sans demander de choisir entre importer et télécharger.
2. Tant que le modèle s'installe, Portrait reste affiché dans le sélecteur, une pastille `Portrait · Téléchargement du modèle… n %` avec barre de progression remplace le cadran (`PortraitModelProgressChip`), et **le déclencheur ne prend pas de photo** : un appui rouvre la fenêtre de progression, la touche de volume est ignorée.
3. Une fois le modèle installé, le bokeh s'active seul et la fenêtre se ferme.
4. En cas d'échec, l'attente est annulée (retour au rendu Photo) ; « Réessayer » ou « Importer » la relancent. Choisir Photo ou Vidéo pendant l'attente l'annule aussi ; le téléchargement continue alors sans activer le bokeh.

## Code

L'attente du Portrait est tenue par le ViewModel, pas par l'écran, pour survivre aux recompositions :

```kotlin
// viewmodel/CameraViewModel.kt
val portraitPreparing: StateFlow<Boolean> = combine(_portraitPending, DepthModelManager.observe(getApplication())) {
    pending, model -> pending && (model is DepthModelDownloadState.Downloading || model is DepthModelDownloadState.Importing)
}.stateIn(viewModelScope, SharingStarted.Eagerly, false)

fun capture() {
    if (portraitPreparing.value) return
    // …
}
```

```kotlin
// ui/camera/CameraScreen.kt, applyBokehForMode
CameraShootingMode.PORTRAIT -> if (!state.isVirtualApertureEnabled) {
    if (DepthModelManager.isInstalled(context)) viewModel.setVirtualApertureAuto(true)
    else { viewModel.setPortraitPending(true); showDepthModelDownloadDialog = true; DepthModelManager.download(context) }
}
```

- `portraitPreparing` est lu par délégation (`by collectAsState()`) dans l'écran : les gestes lancés une seule fois (déclencheur) voient la valeur courante. Le déclencheur (`CaptureButton`) lit son rappel par `rememberUpdatedState`.
- Cadran et pastille de progression : `ui/camera/ApertureDial.kt`. Valeurs : `VirtualApertureStops`, `formatVirtualApertureFStop` (`CameraScreen.kt`).
- Textes : `virtual_aperture_viewfinder_hint`, `virtual_aperture_dial_description`, `portrait_model_preparing`, `depth_model_*` (en, fr).

## Autres réglages du viseur

- **Pastille « N Mpx »** (haut droite, en photo hors Pro) : un appui passe à la définition suivante (12 / 24 / 50 selon ce que le capteur propose). Réglage `captureMegapixels` ; la caméra est rouverte à chaque changement.
- **Réglages** : « Appareil photo », « Modèles et IA », « Application » en haut ; objectifs/mise au point, Pro/RAW, Phantom et sauvegarde sont repliés dans « Avancé ».
- Le balayage horizontal du viseur change de mode, plus de style/LUT : les styles se changent avec la rangée de modèles.

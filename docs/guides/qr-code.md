# Détection des QR codes dans le viseur

Comme l'appareil photo de Samsung ou de Google : si un QR code est visible, une carte apparaît en haut du viseur avec le texte lu et les boutons **Copier** et **Ouvrir** (Ouvrir seulement si le texte est un lien `http(s)`).

- Code : `ui/camera/QrCodeOverlay.kt`, branché dans `CameraScreen.kt` (après `CountdownOverlay`).
- Principe : toutes les 500 ms, une image de l'aperçu (1920 px max, puis version réduite si l image est floue) est lue via `CameraGLSurfaceView.capturePreviewFrame` puis analysée par ML Kit (`com.google.mlkit:barcode-scanning`, modèle embarqué, hors ligne).
- Actif seulement en mode photo, aperçu démarré, sans enregistrement vidéo ni panneau ouvert.
- La carte disparaît 1,5 s après la perte du code ; « Fermer » la masque tant que ce code reste visible.
- Textes : `qr_code_*` dans `strings.xml` (EN, FR).
- Au rebase upstream, conflit possible dans `CameraScreen.kt` et `app/build.gradle.kts`.

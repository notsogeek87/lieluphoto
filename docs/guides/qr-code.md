# Détection des QR codes dans le viseur

Comme l'appareil photo de Samsung ou de Google : si un QR code est visible, une carte apparaît en bas du viseur, au-dessus du zoom, avec le texte lu et les boutons **Copier** et **Ouvrir** (Ouvrir seulement si le texte est un lien `http(s)`).

- Code : `ui/camera/QrCodeOverlay.kt`, branché dans `CameraScreen.kt` (après `CountdownOverlay`).
- Principe : toutes les 300 ms, une image de l'aperçu (1920 px max) est lue via `CameraGLSurfaceView.capturePreviewFrame` puis analysée par ML Kit (`com.google.mlkit:barcode-scanning`, modèle embarqué, hors ligne). Chaque passage essaie une variante de l'image (normale, inversée, réduite) car ML Kit lit mal les codes clairs sur fond sombre et les codes flous.
- Actif seulement en mode photo, aperçu démarré, sans enregistrement vidéo ni panneau ouvert.
- La carte disparaît 4 s après la perte du code ; « Fermer » la masque tant que ce code reste visible.
- Textes : `qr_code_*` dans `strings.xml` (EN, FR).
- Au rebase upstream, conflit possible dans `CameraScreen.kt` et `app/build.gradle.kts`.
- Une fois un code lu, la variante qui a réussi est réutilisée tant que le code reste visible (3 échecs d'affilée la remettent dans le cycle) : sinon la carte clignoterait.

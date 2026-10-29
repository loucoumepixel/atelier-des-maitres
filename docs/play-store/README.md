# Publier sur le Play Store

## Déjà prêt dans le dépôt
- Application conforme aux exigences actuelles : Android 16 (API 36) ciblé, identifiant définitif `io.github.loucoumepixel.atelierdesmaitres`, numéro de version qui augmente à chaque build.
- Bundle `.aab` (le format exigé par la Play Console) produit à chaque build et joint à chaque Release GitHub.
- Fiche du store en français et en anglais, réponses aux questionnaires : [fiche.md](fiche.md).
- Politique de confidentialité : [../confidentialite.md](../confidentialite.md) (lien public à coller dans la Play Console).
- Audit de publication et points restant à faire : [../audit-publication.md](../audit-publication.md).
- Icône 512 × 512 : [icone-512.png](icone-512.png) ; image de présentation 1024 × 500 en français : [image-presentation-1024x500.png](image-presentation-1024x500.png), en anglais : [feature-graphic-en-1024x500.png](feature-graphic-en-1024x500.png).

## La clé d'envoi
La Play Console exige que le `.aab` soit signé avec une clé d'envoi, toujours la même pour toutes les mises à jour. Google garde ensuite la vraie clé de l'application (« signature d'application Play »), donc une clé d'envoi perdue peut être réinitialisée auprès de Google.

Le build la lit dans deux secrets GitHub (dépôt › Settings › Secrets and variables › Actions) :
- `UPLOAD_KEYSTORE_BASE64` : le fichier de clé `.jks`, encodé en base64 ;
- `UPLOAD_KEYSTORE_PASSWORD` : son mot de passe (le même pour le magasin et la clé, alias `upload`).

Pour créer la clé sur un ordinateur avec Java :
```
keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 upload.jks   # le résultat va dans UPLOAD_KEYSTORE_BASE64
```
Utiliser le même mot de passe pour le magasin et la clé. Conserver `upload.jks` et son mot de passe en lieu sûr.

## Étapes dans la Play Console
1. Créer un compte développeur sur https://play.google.com/console (25 $ une fois, vérification d'identité).
2. Créer l'application « Atelier des Maîtres », langue par défaut français, application gratuite.
3. Remplir la fiche, la confidentialité et les questionnaires avec [fiche.md](fiche.md). Ajouter 2 à 8 captures d'écran prises sur le téléphone.
4. Comptes personnels récents : faire un **test fermé** avec au moins 12 testeurs pendant 14 jours avant de pouvoir publier en production, pas à pas dans [test-ferme.md](test-ferme.md).
5. Avant chaque publication, faire la [vérification avant sortie](../verification-avant-sortie.md) sur un vrai téléphone.
6. Déposer `atelier-des-maitres.aab` de la dernière Release, puis envoyer en examen.

## Droits sur les images
Les vingt œuvres sont dans le domaine public (artistes morts avant 1955). Les reproductions photographiques fidèles d'œuvres en deux dimensions ne créent pas de nouveau droit (directive européenne 2019/790, article 14 ; jurisprudence Bridgeman aux États-Unis). La reproduction de *Maisons et personnage* provient de la collection domaine public de rawpixel (CC0).

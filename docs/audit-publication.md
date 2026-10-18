# Audit de publication — Atelier des Maîtres

*18 octobre 2026, version 1.0.4 (dernière Release de `main`)*

Le regard porté ici est celui d'une revue avant mise en production sur le Play Store : conformité aux règles de Google, robustesse, finition perçue par l'utilisateur, et tout ce qui pèse sur les notes et le classement.

## Verdict

La base est saine et au-dessus de la moyenne des applications de coloriage du store : aucune autorisation sensible (ni Internet, ni stockage : le sélecteur de photos du système suffit), cible Android 16, bundle `.aab` signé par la clé d'envoi, code optimisé par R8, sauvegardes atomiques, thème clair et sombre, moteur de découpage testé. Rien dans le code ne ferait refuser l'application à l'examen.

Les manques relevaient surtout de la finition : pas de partage de la toile (la fonction qui fait connaître ce genre d'application), icône à thème vide sous Android 13+, quelques défauts d'accessibilité, contenus masqués par l'encoche en paysage, rapports de plantage illisibles faute de fichier de désobscurcissement. Ils sont corrigés dans cette version. Ce qui reste demande une action de votre part (fiche du store) ou une décision produit.

## Corrigé dans cette version

| Domaine | Constat | Correction |
|---|---|---|
| Fonction | Impossible de montrer sa toile terminée à quelqu'un | **Partager la toile** depuis le menu ⋮ et l'écran de fin : image PNG encadrée d'une marge de papier, sans le hachurage de la couleur active, via le menu de partage d'Android (aucune autorisation ajoutée) |
| Icône | L'icône à thème (Android 13+) n'affichait qu'une tache unie : les touches de couleur disparaissaient | Icône monochrome dédiée, touches découpées en creux |
| Écran | En paysage, la galerie et l'atelier passaient sous l'encoche et la barre de navigation latérale | Marges calculées sur les zones sûres de l'écran |
| Accessibilité | Le bouton « voir l'original » était inutilisable avec TalkBack (il fallait maintenir) ; chaque toile de la galerie était annoncée deux fois | Action TalkBack qui bascule l'original, état lu à voix haute ; titre annoncé une fois, actions nommées (« Choisir le niveau », « Retirer cette image ») |
| Découvrabilité | Retirer une image importée n'était possible que par un appui long que rien ne signale | Bouton « Retirer cette image » dans la fiche de la toile (l'appui long reste) |
| Ressenti | Aucune sensation sous le doigt | Petit « tic » haptique à chaque zone peinte, vibration franche quand une couleur est terminée |
| Confiance | Aucune information dans l'application (version, confidentialité, contact) | Bouton « À propos » : version, politique de confidentialité, signaler un problème |
| Données | Les images importées étaient retrouvées par chemin absolu : perdues à la restauration d'une sauvegarde sur un autre appareil ou profil | Chemin relatif, compatible avec les sauvegardes existantes |
| Détails | Deux imports pouvaient s'appeler « Ma toile n° 2 » après une suppression ; la fin pouvait être célébrée deux fois (annuler puis rétablir) ; le dernier niveau joué d'une image retirée restait en mémoire | Corrigés |
| Publication | Une version de `main` sans les secrets de signature aurait été publiée signée en debug (refusée par la Play Console, et incapable de mettre à jour l'application installée) | Le build de `main` s'arrête si la clé d'envoi manque |
| Publication | Les plantages remontés par la Play Console auraient été illisibles (code obscurci par R8) | `atelier-des-maitres-mapping.txt` joint à chaque Release, à déposer avec le `.aab` |
| Publication | Chaque poussée sur une branche de travail lançait deux builds | Un seul build par pull request, les builds dépassés sont annulés ; notes de Release générées depuis les changements |
| Documentation | README obsolète (dossier `peinture-app`, téléchargement par l'onglet Actions) ; politique de confidentialité muette sur le partage ; aucun guide des captures | README, politique et fiche mis à jour, avec un plan de captures d'écran |

## À faire de votre côté avant la soumission

1. **Adresse e-mail de contact** : obligatoire dans la Play Console et affichée publiquement. Une adresse dédiée est préférable.
2. **Captures d'écran** : 6 à 8 captures portrait, l'ordre et les réglages sont dans [fiche.md](play-store/fiche.md#captures-décran-à-prendre-sur-le-téléphone). C'est l'élément de la fiche qui pèse le plus sur les téléchargements.
3. **Test fermé** : un compte personnel récent doit faire tester l'application par au moins 12 personnes pendant 14 jours avant la production. Pendant ce test, consulter le *rapport de pré-lancement* de la Play Console (Google y lance l'application sur de vrais appareils et signale plantages et défauts d'accessibilité).
4. **Fichier de désobscurcissement** : à chaque `.aab` déposé, déposer aussi le `mapping.txt` de la même Release.
5. **Signature** : l'application du Play Store sera re-signée par Google, celle des Releases GitHub par votre clé d'envoi. On ne peut pas passer de l'une à l'autre sans désinstaller (et donc perdre sa progression) : une fois sur le store, orienter les testeurs vers le store uniquement.
6. **GitHub Pages** : Settings › Pages › *Deploy from a branch*, branche `main`, dossier `/docs`. Les politiques de confidentialité sont alors publiées à `https://loucoumepixel.github.io/atelier-des-maitres/confidentialite.html` et `privacy.html`, les adresses à donner à la Play Console.
7. **Fiche en anglais** : dans la Play Console, ajouter la traduction anglaise de la fiche avec le texte de [fiche.md](play-store/fiche.md#english) et l'image de présentation anglaise.

## Recommandations mises en œuvre (deuxième passe)

| Recommandation | Fait |
|---|---|
| Version anglaise | Tous les textes de l'application sont dans les ressources Android : anglais par défaut, français pour les téléphones en français, langue choisissable par application sous Android 13+. Titres, dates et cartels des toiles traduits. Fiche du store et politique de confidentialité en anglais ([fiche.md](play-store/fiche.md#english), [privacy.md](privacy.md)). |
| Bibliothèques à jour | Kotlin 2.2.21, Compose BOM 2025.06.01, activity 1.10.1, lifecycle 2.9.1, core-ktx 1.16.0, coroutines 1.10.2 ; validé par le build GitHub. |
| Profil de démarrage | `profileinstaller` et `baseline-prof.txt` : le code de l'application est compilé à l'installation, y compris pour l'APK des Releases. |
| Image de présentation | Refaite avec un dégradé sombre et une ombre sous le texte, en français et en anglais. |
| Confidentialité sur GitHub Pages | Site prêt dans `docs/` ; il ne reste qu'à activer GitHub Pages (voir ci-dessous). |
| Plus de toiles | **Bloqué ici** : l'environnement de travail n'a pas accès aux musées ni à Wikimedia Commons pour télécharger les reproductions. Voir ci-dessous. |

## Nouvelles toiles proposées

Toutes dans le domaine public, choisies pour leurs aplats lisibles en zones et leur notoriété :

- Claude Monet, *Impression, soleil levant* (1872)
- Claude Monet, *Les Coquelicots* (1873)
- Johannes Vermeer, *La Jeune Fille à la perle* (vers 1665)
- Gustav Klimt, *Le Baiser* (1908)
- Georges Seurat, *Un dimanche après-midi à l'Île de la Grande Jatte* (1884-1886)
- Katsushika Hokusai, *Le Fuji rouge* (vers 1831)

Il suffit de déposer les reproductions (JPEG, 1600 px environ sur le grand côté, depuis Wikimedia Commons ou le site du musée) dans le projet : je les intègre au catalogue avec titre, date et cartel en deux langues.

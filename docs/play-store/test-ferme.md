# Le test fermé, pas à pas

Google exige qu'un compte développeur personnel récent fasse tester l'application par **au moins 12 personnes pendant 14 jours d'affilée** avant de pouvoir la publier pour tout le monde. C'est ce test qui fixe la date de sortie : autant le lancer tôt.

Coût : rien de plus, si le compte développeur Play Console est déjà créé. Sinon, la création du compte coûte **25 $ une seule fois** (c'est la seule dépense de toute la publication).

## Avant de commencer (une fois)
1. **Activer GitHub Pages** pour publier la politique de confidentialité : sur GitHub, dépôt `atelier-des-maitres` › Settings › Pages › Source : *Deploy from a branch*, branche `main`, dossier `/docs`, Save. Après quelques minutes, la page https://loucoumepixel.github.io/atelier-des-maitres/confidentialite.html s'ouvre.
2. **Choisir une adresse e-mail de contact** : elle sera affichée publiquement sur la fiche.
3. **Remplir la fiche** dans la Play Console avec [fiche.md](fiche.md) (textes, captures, questionnaires).

## Trouver les 12 testeurs
- Famille, amis, collègues : il leur faut un téléphone Android et un compte Google (Gmail).
- En prévoir 15 à 20 : certains oublient d'installer, et Google compte ceux qui ont **gardé** l'application pendant les 14 jours.
- Ils n'ont pas besoin de jouer tous les jours, mais l'ouvrir de temps en temps et donner un avis aide beaucoup.

## Lancer le test dans la Play Console
1. Tests › Test fermé › Créer un canal (par exemple « Amis »).
2. Testeurs › Créer une liste d'adresses e-mail, y coller les adresses Gmail des testeurs.
3. Versions › Créer une version › déposer `atelier-des-maitres.aab` de la dernière Release GitHub, et son fichier `atelier-des-maitres-mapping.txt` (fichier de désobscurcissement).
4. Envoyer en examen. Google vérifie en quelques heures à quelques jours.
5. Une fois validé, copier le **lien d'inscription** (« Rejoindre sur le Web ») et l'envoyer aux testeurs avec le message ci-dessous.

## Message à envoyer aux testeurs
> Bonjour ! J'ai créé un jeu pour Android, Atelier des Maîtres : on repeint des chefs-d'œuvre (Van Gogh, Monet, la Joconde…) zone par zone, en peinture à l'huile. Avant de le publier, Google me demande de le faire tester pendant 14 jours.
>
> 1. Ouvre ce lien depuis ton téléphone Android, avec ton compte Gmail : [lien d'inscription]
> 2. Accepte de devenir testeur, puis installe le jeu depuis le Play Store.
> 3. **Garde-le installé au moins 14 jours**, c'est le plus important.
>
> Si tu vois un bug ou si quelque chose te plaît ou te gêne, dis-le-moi ! Merci beaucoup.

## Pendant les 14 jours
- Consulter le **rapport de pré-lancement** (Tests › Rapport de pré-lancement) : Google lance le jeu gratuitement sur de vrais téléphones et signale plantages, lenteurs et défauts d'accessibilité.
- Consulter Qualité › Android vitals › Plantages.
- Les testeurs installés depuis le Play Store ne doivent pas installer l'APK des Releases GitHub par-dessus : les deux versions ne sont pas signées pareil, il faudrait désinstaller et la progression serait perdue.

## Après les 14 jours
Production › Créer une version › reprendre le même `.aab` (ou un plus récent qui a passé la [vérification avant sortie](../verification-avant-sortie.md)) › Envoyer en examen. Google demande parfois quelques réponses sur le déroulement du test : dire combien de testeurs, ce qu'ils ont remonté et ce qui a été corrigé.

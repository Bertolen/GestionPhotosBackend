# Runner CI/CD scopé au groupe `nuestra-traviesa`

Le déploiement CI/CD des deux projets GestionPhotos (frontend et backend) s'exécute sur une seule Raspberry Pi 3 B+ via un unique runner auto-hébergé. Nous créons un **group runner** `nuestra-traviesa` (tag `rpi`, executor shell) qui remplace le project runner historiquement possédé par le projet frontend : la topologie de groupe sert les deux projets symétriquement, le frontend cesse d'être le projet propriétaire du runner de déploiement, et un futur projet du groupe serait servi d'office.

## Considered Options

- **Activation directe du runner existant pour le backend** (permise par `locked: false`) : zéro toucher sur la Pi, zéro migration, réversible — écartée parce que le déploiement backend serait resté dépendant d'un runner possédé par le projet frontend (asymétrie ; suppression du runner impossible sans désactivation préalable ; disparition du projet frontend = perte du runner backend).
- **Deuxième project runner backend** : même Pi, un service, deux entrées `[[runners]]` — écartée : deux runners pour une seule machine, goulot de polling documenté avec `concurrent = 1`, et la consolidation vers le groupe resterait à faire plus tard.

## Consequences

- Migration avec fenêtre de transition sur la seule chaîne de prod existante (frontend). Ordre sûr : créer le group runner → `register` sur la Pi → `gitlab-runner verify` → **pauser l'ancien avant la vérification** (pendant la coexistence, les deux runners portent le tag `rpi` et le job `deploy` frontend pourrait sinon être pris par l'ancien, ne prouvant rien) → pipeline frontend master verte via le nouveau runner. Retour arrière : résumer l'ancien.
- L'ancien runner n'est retiré (suppression dans l'UI GitLab **et** `gitlab-runner unregister` sur la Pi — deux gestes indépendants, tous deux requis) qu'une fois les **deux** chaînes CI/CD fonctionnelles et testées sur le group runner ; jusqu'alors il reste en pause comme repli instantané du frontend.
- File FIFO commune, un job à la fois (`concurrent = 1` global au config) : un déploiement backend fait attendre un déploiement frontend, et inversement ; aucune priorité entre projets n'est possible.

Décision : ticket « Topologie du runner pour le backend » (https://gitlab.com/nuestra-traviesa/GestionPhotosBackend/-/issues/9), map « CI/CD GitLab du backend GestionPhotos » (https://gitlab.com/nuestra-traviesa/GestionPhotosBackend/-/issues/6).

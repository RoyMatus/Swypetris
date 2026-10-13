# Swypetris 1.8.0

- Update checks now run at most once per hour when the game starts. The version button in the main menu still lets you check manually.
- Manual checks share a rolling limit of 59 attempts per hour on each installation. GitHub rate-limit responses and RuStore check failures pause further attempts until the applicable retry window ends.
- The obsolete update controls have been removed from Settings. Downloading or installing an available update still requires an explicit choice.
- Local delivery and verification have been streamlined without changing gameplay.

Existing game data is preserved when updating from 1.7.0. The in-app check uses RuStore for RuStore installations and GitHub for other installations.

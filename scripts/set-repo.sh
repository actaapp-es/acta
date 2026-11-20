#!/usr/bin/env bash
# Sustituye los marcadores por tus datos reales. Uso:
#   scripts/set-repo.sh TU-USUARIO/acta
# (En Windows, ejecútalo desde "Git Bash", que se instala junto a Git.)
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="${1:?Uso: scripts/set-repo.sh usuario/repositorio}"
FILES=$(grep -rlI --exclude-dir=.git --exclude-dir=build --exclude=set-repo.sh -e 'usuario/acta' . || true)
for f in $FILES; do
  sed -i "s#usuario/acta#$REPO#g" "$f"
done
echo "Listo. Repositorio: $REPO"

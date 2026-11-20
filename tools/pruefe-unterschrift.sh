#!/usr/bin/env bash
# Prueft, dass eine APK mit dem Test-Schluessel des Projekts unterschrieben ist.
#
# Aufruf: tools/pruefe-unterschrift.sh pfad/zur.apk
#
# Android nimmt ein Update nur von derselben Unterschrift an. Traegt ein Build eine andere,
# laesst er sich nicht ueber die installierte App installieren - man muesste sie samt ihren
# Daten loeschen. Genau das ist schon passiert (docs/adr/0009); diese Pruefung sorgt dafuer,
# dass es in der CI auffaellt und nicht erst auf einem Telefon.
set -euo pipefail

apk="${1:?Pfad zur APK fehlt}"
schluessel="$(dirname "$0")/../app/rezeptli-test.keystore"

erwartet="$(keytool -list -v -keystore "$schluessel" -storepass rezeptli-test 2>/dev/null \
  | grep -m1 'SHA256:' | sed 's/.*SHA256: //' | tr -d ': ' | tr 'A-F' 'a-f')"

apksigner="$(ls "${ANDROID_HOME:?ANDROID_HOME ist nicht gesetzt}"/build-tools/*/apksigner | sort -V | tail -1)"
gefunden="$("$apksigner" verify --print-certs "$apk" \
  | grep -m1 'certificate SHA-256 digest' | sed 's/.*: //' | tr -d ' ')"

echo "erwartet: $erwartet"
echo "gefunden: $gefunden"

if [ -z "$erwartet" ] || [ "$erwartet" != "$gefunden" ]; then
  echo "Die APK ist nicht mit dem Test-Schluessel des Projekts unterschrieben." >&2
  exit 1
fi
echo "Unterschrift stimmt."

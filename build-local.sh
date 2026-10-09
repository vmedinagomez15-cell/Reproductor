#!/usr/bin/env bash
set -euo pipefail
# Alternative reproducible packaging route used for this prototype.
# Requires Java 17, ECJ 3.38.0 and Android build-tools 35 / platform 35.
: "${FLAC_ANDROID_JAR:?Set absolute path to Android 35 android.jar}"
: "${FLAC_BUILD_TOOLS:?Set absolute path to Android build-tools 35 directory}"
: "${FLAC_ECJ_JAR:?Set absolute path to ecj-3.38.0.jar}"
cd "$(dirname "$0")"
mkdir -p build/classes build/dex build/generated
"$FLAC_BUILD_TOOLS/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$FLAC_BUILD_TOOLS/aapt2" link -o build/base.apk --manifest app/src/main/AndroidManifest.xml -I "$FLAC_ANDROID_JAR" -A app/src/main/assets --java build/generated --version-code 4 --version-name 0.4.0 build/resources.zip
java -jar "$FLAC_ECJ_JAR" -8 -nowarn -cp "$FLAC_ANDROID_JAR" -d build/classes app/src/main/java/com/victor/flacprueba/*.java build/generated/com/victor/flacprueba/R.java
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
with ZipFile('build/classes.jar','w') as z:
    for f in Path('build/classes').rglob('*.class'): z.write(f, f.relative_to('build/classes'))
PY
"$FLAC_BUILD_TOOLS/d8" --lib "$FLAC_ANDROID_JAR" --min-api 27 --output build/dex build/classes.jar
python3 - <<'PY'
from zipfile import ZipFile
with ZipFile('build/base.apk','a') as z: z.write('build/dex/classes.dex','classes.dex')
PY
"$FLAC_BUILD_TOOLS/zipalign" -f -p 4 build/base.apk build/aligned.apk
"$FLAC_BUILD_TOOLS/apksigner" sign --ks signing/debug.keystore --ks-pass pass:android --key-pass pass:android --out build/FlacPrueba-0.4.apk build/aligned.apk
"$FLAC_BUILD_TOOLS/apksigner" verify --verbose build/FlacPrueba-0.4.apk

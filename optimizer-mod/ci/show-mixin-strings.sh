#!/bin/bash
# Shows what the mixin classes in the built jar say about the game's names, and whether the jar has a refmap.
jar=$(ls build/libs/urinth-optimizer-*.jar 2>/dev/null | head -1)
if [ -z "$jar" ]; then echo "no jar in build/libs"; exit 0; fi
echo "jar: $jar"
unzip -Z1 "$jar" | grep -i refmap || echo "(no refmap file in the jar)"
unzip -p "$jar" urinth_optimizer.mixins.json | grep -i refmap || echo "(the mixin configuration has no refmap entry)"
tmp=$(mktemp -d)
unzip -q "$jar" -d "$tmp"
for f in "$tmp"/dev/urinth/optimizer/mixin/*.class; do
  name=$(basename "$f" .class)
  echo "== $name"
  javap -v -p -cp "$tmp" "dev.urinth.optimizer.mixin.$name" 2>/dev/null \
    | grep -E "= Utf8 +(.*(method_|field_|class_)[0-9]+.*|getRenderDistance|calculateParticleLevel|setSimulationDistance|uploadAllPendingUploads|getViewDistance|isInViewDistance|getRelativeFrom|initializeQueueForFullUpdate|toUpload|.*net/minecraft/.*)$" \
    | sed -E 's/^ +#[0-9]+ = Utf8 +/   /' | sort -u
  echo "   -- annotations as written in the class (a single value 'at=@...' or a list 'at=[@...')"
  javap -v -p -cp "$tmp" "dev.urinth.optimizer.mixin.$name" 2>/dev/null | sed -n -E '/Runtime(In)?[Vv]isibleAnnotations:/,/^$/p' | grep -vE "^ *[0-9]+: #" | sed 's/^/   /'
done

#!/bin/sh
set -e
chmod +x ./mvnw
# Named volume masks image /root/.m2; warm on first start, when pom.xml changes, or after cache wipe.
WARM_MARKER=/root/.m2/.exercises-warm
if [ ! -f "$WARM_MARKER" ] || [ pom.xml -nt "$WARM_MARKER" ]; then
  echo "Refreshing Maven repository (first start, pom.xml changed, or empty cache)..."
  ./mvnw -B dependency:go-offline -DskipTests
  ./mvnw -B clean compile -DskipTests
  touch "$WARM_MARKER"
fi
# excludeDevtools: full JVM restarts pick up new pom dependencies (RestartClassLoader cannot).
exec ./mvnw -B compile spring-boot:run \
  -Dspring-boot.run.excludeDevtools=true \
  -Dspring-boot.run.jvmArguments=-Dserver.address=0.0.0.0

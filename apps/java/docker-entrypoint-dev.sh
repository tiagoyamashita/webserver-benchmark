#!/bin/sh
set -e
chmod +x ./mvnw
# Named volume masks image /root/.m2; warm on first start or after a bad cache.
if [ ! -f /root/.m2/.exercises-warm ]; then
  echo "Warming Maven repository (first start or empty cache)..."
  ./mvnw -B dependency:go-offline -DskipTests
  touch /root/.m2/.exercises-warm
fi
exec ./mvnw -B compile spring-boot:run -Dspring-boot.run.jvmArguments=-Dserver.address=0.0.0.0

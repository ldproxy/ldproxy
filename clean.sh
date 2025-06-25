#!/bin/sh

./gradlew :clean
rm -rf build/
rm -rf */build/
rm -rf */*/build/

cd ../xtraplatform-spatial
./gradlew :clean
rm -rf build/
rm -rf */build/

cd ../xtraplatform
./gradlew :clean
rm -rf build/
rm -rf */build/

cd ../ldproxy

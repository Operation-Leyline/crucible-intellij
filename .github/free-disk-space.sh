#!/usr/bin/env bash
# The IDEs these jobs download unpack to several GB each; a 2-core runner's
# disk fills up. Remove preinstalled toolchains the plugin never uses.
set -u
for dir in /usr/share/dotnet /usr/local/lib/android /opt/ghc /opt/hostedtoolcache/CodeQL; do
  if [ -d "$dir" ]; then
    sudo rm -rf -- "$dir"
  fi
done
df -h /

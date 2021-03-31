#!/bin/bash

# Install script for ErgoMixer and
# WIP: Tested on Ubuntu
# The command:
# curl -s "https://getmixer.ergonaut.space/install.sh" | bash
# Should build the ErgoMixer on all Unix systems


# SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java $(sdk list java | grep -o "8\.[0-9]*\.[0-9]*\.hs-adpt" | head -1)
sdk install sbt

# Graal
wget https://github.com/oracle/graal/archive/refs/tags/vm-21.0.0.2.zip
unzip vm-21.0.0.2.zip
export GRAAL_HOME=$HOME/zkt/graal-vm-21.0.0.2
echo "GRAAL_HOME set to..." $GRAAL_HOME
export PATH=$PATH:${GRAAL_HOME}/bin

# TODO: Generalise this for the latest version / cross-platform for Graal
#curl -sL https://api.github.com/repos/graalvm/graalvm-ce-builds/releases/latest | jq -r '.assets[].browser_download_url'
#curl -L https://github.com/graalvm/graalvm-ce-builds/releases/latest/download/graalvm-ce-java8-linux-amd64tar.gz

# ErgoMixer
git clone https://github.com/ergoMixer/ergoMixBack.git
cd ergoMixBack/appkit/

echo "Building AppKit..."
sbt publishLocal

echo "Building Frontend"
cd ..
git submodule update --init
cd ergoMixFront/
npm install
npm run build

cd ..
mv ergoMixFront/build/ mixer/public

echo "Building Backend"
cd mixer/
sbt assembly
cd target/scala-2.12/
java -jar ergoMixer-*.jar
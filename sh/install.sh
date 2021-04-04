#!/bin/bash

# Install script for ErgoMixer and
# WIP: Tested on Ubuntu
# The command:
# curl -s "https://getmixer.ergonaut.space/install.sh" | bash
# Should build the ErgoMixer on all Unix systems
# Windows will require WSL, Cygwin or some other bash solution 


# SDKMAN
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java $(sdk list java | grep -o "8\.[0-9]*\.[0-9]*\.hs-adpt" | head -1)





if  [[ $1 = "-s" ]]; then
    sdk install sbt
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
    
else
   # TODO: Generalise this for the latest version / cross-platform for Graal
   mkdir mixer
   cd mixer
   wget https://github.com/ergoMixer/ergoMixBack/releases/download/3.3.0/ergoMixer-3.3.0.jar

fi

java -jar ergoMixer-*.jar

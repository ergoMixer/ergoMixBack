#!/bin/bash

# Install script for ErgoMixer and
# WIP: Tested on Ubuntu / OSX
# The command:
# curl -s "domain.tld/install.sh" | bash
# Should build the ErgoMixer on all Unix systems
# Windows will require WSL, Cygwin or some other bash solution 

PS3='Please enter your choice: '
options=("Pull and launch .jar" "Build from Source" "Docker Quick Build" "Quit")
select opt in "${options[@]}"
do
    case $opt in
        "Pull and launch .jar")
            curl -s "https://get.sdkman.io" | bash
            source "$HOME/.sdkman/bin/sdkman-init.sh"
            sdk install java $(sdk list java | grep -o "8\.[0-9]*\.[0-9]*\.hs-adpt" | head -1)
            mkdir mixer
            cd mixer
            curl -s https://api.github.com/repos/ergoMixer/ergoMixBack/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4 | wget -qi-
            java -jar ergoMixer-*.jar
            ;;
        "Build from Source")
            # SDKMAN
            curl -s "https://get.sdkman.io" | bash
            source "$HOME/.sdkman/bin/sdkman-init.sh"
            sdk install java $(sdk list java | grep -o "8\.[0-9]*\.[0-9]*\.hs-adpt" | head -1)
            echo "Installing NVM"
            curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.38.0/install.sh | bash
            nvm install node 
            sdk install sbt
            
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
            ;;
        "Docker Quick Build")
            echo "you chose choice $REPLY which is $opt"
            mkdir $PWD/ergo
            touch $PWD/ergo/db_log.txt
            chmod 777 $PWD/ergo/db_log.txt
            docker run -p 127.0.0.1:9000:9000 \
                --restart=always \
                -v /$PWD/ergo/db_log.txt:/home/ergo/ergoMixer \
                -d ergomixer/mixer:latest 
            ;;
        "Quit")
            break
            ;;
        *) echo "invalid option $REPLY";;
    esac
done





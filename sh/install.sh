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
            yes | sdk install java $(sdk list java | grep -o "11\.[0-9]*\.[0-9]*\-tem" | head -1)
            mkdir mixer
            cd mixer
            curl -s https://api.github.com/repos/ergoMixer/ergoMixBack/releases/latest | grep "browser_download_url.*jar" | cut -d '"' -f 4 | wget -qi-
            java -jar ergoMixer-*.jar
            ;;
        "Build from Source")
            # SDKMAN
            curl -s "https://get.sdkman.io" | bash
            source "$HOME/.sdkman/bin/sdkman-init.sh"
            yes | sdk install java $(sdk list java | grep -o "11\.[0-9]*\.[0-9]*\-tem" | head -1)
            echo "Installing NVM"
            curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.2/install.sh | bash
            export NVM_DIR="$HOME/.nvm"
            [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"  # This loads nvm
            [ -s "$NVM_DIR/bash_completion" ] && \. "$NVM_DIR/bash_completion"  # This loads nvm bash_completion
            source "$NVM_DIR/nvm.sh"
            nvm install 14
            yes | sdk install sbt 1.2.7

            git clone https://github.com/ergoMixer/ergoMixBack.git
            cd ergoMixBack/

            echo "Building Frontend"
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
            mkdir -p $PWD/ergo/mixer_data
            chmod -R 777 $PWD/ergo/mixer_data
            docker run -p 127.0.0.1:9000:9000 \
                --restart=always \
                -v /$PWD/ergo/mixer_data:/$PWD/ergo/ergoMixer \
                -d ergomixer/mixer:latest
            ;;
        "Quit")
            break
            ;;
        *) echo "invalid option $REPLY";;
    esac
done

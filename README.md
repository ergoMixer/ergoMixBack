# Ergo Mixer
ErgoMixer is a web application for mixing ergs and tokens based on Ergo platform. ErgoMixer is completely serverless; It only needs to connect to the explorer and a node (any node! no api_key is needed). For more information on how it works see [here](https://github.com/ergoMixer/ergoMixBack/wiki/How-it-works-ergoMixer).

## Contents
NOTE: If you want to use pre-built binaries and just run the ErgoMixer skip all steps to [`Run Mixer`](#run-mixer).
- [Ergo Mixer](#ergo-mixer)
  - [Contents](#contents)
  - [Setup](#setup)
    - [Prerequisites](#prerequisites)
    - [Build the ErgoMixer:](#build-the-ergomixer)
  - [Run mixer](#run-mixer)
  - [Docker Quick Start](#docker-quick-start)
  - [Notes](#notes)


## Setup
### Prerequisites
  * #### OpenJDK 8
    Install an appropriate version of OpenJDK 8 from [Here](https://jdk.java.net/java-se-ri/8-MR3) based on your OS.
  * #### SBT 1.2.7
    Depending on your OS, you can follow instructions in [this](https://www.scala-sbt.org/1.0/docs/Setup.html) page to install sbt.

  * #### GraalVM

    #### Install GraalVM Community Edition on MacOS and Linux
    
    First you need to download an archive with the [latest release](https://github.com/oracle/graal/releases) of GraalVM (e.g.`graalvm-ce-java8-linux-amd64-19.3.1.tar.gz`) for Linux and put its content in your `$PATH` :

    ```shell
    $ cd <your/directory/with/downloaded/graal>
    $ tar -zxf graalvm-ce-java8-linux-amd64-19.3.1.tar.gz
    $ export GRAAL_HOME=<your/directory/with/downloaded/graal>/graalvm-ce-java8-19.3.1
    $ export PATH=$PATH:${GRAAL_HOME}/bin
    ```
    
    The same for MacOS:
  
    ```shell
    $ cd <your/directory/with/downloaded/graal>
    $ tar -zxf graalvm-ce-darwin-amd64-19.3.1.tar.gz
    $ export GRAAL_HOME=<your/directory/with/downloaded/graal>/graalvm-ce-java8-19.3.1/Contents/Home
    $ export PATH=$PATH:${GRAAL_HOME}/bin
    ```

  * #### Build the Appkit project
    There is a slightly modified version of appkit in this repository. So, in order to build the mixer you first need to publish appkit locally in the Ivy repository:
    ```shell
    $ cd <your_directory_clone_project>/mixer/appkit
    $ sbt publishLocal
    ```

### Build the ErgoMixer:
  * #### Frontend
      Use the following command to get the latest frontend:
      ```shell
      $ git submodule update --init
      ```
      Then build the `ergomixfront` by following [these](https://github.com/ergoMixer/ergoMixFront/blob/master/README.md) instructions.
      
      Then, use the built front in the backend by using the following command in `ergomixback` directory:
      ```shell
      $ mv ergomixfront/build/ mixer/public
      ```
  * #### Backend      
    Finally, build the backend:
    ```shell
    $ cd <your_directory_clone_project>/mixer/mixer
    $ sbt assembly
    ```
    The jar file will appear in `target/scala-2.12/` directory.

## Run mixer
After building the project or [downloading](https://github.com/ergoMixer/ergoMixBack/releases) jar file, to run the ErgoMixer with default config (A node and explorer in Mainnet network) use:
```shell
$ java -jar ergoMixer-<version>.jar
```
Also to run the ErgoMixer with a custom config file use:
```shell
$ java -jar -D"config.file"=<path-your-config>/customConfig.conf ergoMixer-<version>.jar
```
You can use this [config file](mixer/reference.conf) and change it as you want.

The database will be saved in your home directory. This database contains all the information and secrets being used by the mixer, So, take good care of it.

## Docker Quick Start
  The ergoMixer has officially supported Docker package, to run the ErgoMixer with default config (A node and explorer in Mainnet network) use:
  ```shell
    $ docker run -p 127.0.0.1:9000:9000 \
      --restart=always \
      -v /path/on/host/to/ergo/database_and_logfile:/home/ergo/ergoMixer \
      -d ergomixer/mixer:latest 
  ```
  Also to run the ErgoMixer with a custom config file use:
  ```shell
    $ docker run -p 127.0.0.1:9000:9000 \
      --restart=always \
      -v /path/on/host/to/ergo/database_and_logfile:/home/ergo/ergoMixer \
      -v <path-your-config-file>/yourConfig.conf:/home/ergo/mixer/application.conf \
      -d ergomixer/mixer:latest 
  ```

  The database ans log file will be in your host directory `/path/on/host/to/ergo/database_and_logfile`; you can use `9000` port locally to load the mixer.
  
  NOTE: The `/path/on/host/to/ergo/database_and_logfile` directory must have `777` permission or have owner/group numeric id equal to `9052` to be writable by the container.
  
## Notes
  * Database schema for version `3.0.0` has changed; So, please consider binding to a different location if you previously have used an older version.

  * If you want to buy SigmaUSD/SigmaRSV directly from the mixer, DO NOT SET withdrawal address when creating the mix/covert address and use "Set Later" option. 
        Later, at the moment of buying SigmaUSD/SigmaRSV, set withdraw address and choose "AGE USD" option.
        
  * If you now using manuall file config for your mixer after version `3.3.0` you must be update your config file, section `play.http` for using age-usd, like the following configuration:
    ```
    play: {
      http {
            filters="filters.CorsFilters",
            fileMimeTypes = ${play.http.fileMimeTypes} """
                     wasm=application/wasm
                    """
           }
      filters {
        hosts {
          # Allow requests to example.com, its subdomains, and localhost:9000.
          allowed = ["localhost", "127.0.0.1"]
        }
        cors {
          pathPrefixes = ["/"]
          allowedOrigins = null,
          allowedHttpMethods = ["GET", "POST"]
          allowedHttpHeaders = null
        }
      }
    }
    ```

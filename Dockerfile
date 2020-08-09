FROM node:12.14 as builder-front

WORKDIR /usr/src/app
COPY ./ergoMixFront/package.json ./ergoMixFront/package-lock.json ./
RUN npm install
COPY ./ergoMixFront ./
RUN npm run build

FROM openjdk:8-jre-slim as builder
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends apt-transport-https apt-utils bc dirmngr gnupg && \
    echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
    # seems that dash package upgrade is broken in Debian, so we hold it's version before update
    echo "dash hold" | dpkg --set-selections && \
    apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends sbt wget sed
WORKDIR /mixer
RUN wget https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.1/graalvm-ce-java8-linux-amd64-19.3.1.tar.gz && \
    tar -xf graalvm-ce-java8-linux-amd64-19.3.1.tar.gz
ENV JAVA_HOME="/mixer/graalvm-ce-java8-19.3.1"
ENV PATH="${JAVA_HOME}/bin:$PATH"
ADD ["./appkit/", "/mixer/appkit"]
WORKDIR /mixer/appkit
RUN sbt publishLocal
ADD ["./mixer", "/mixer/mixer"]
WORKDIR /mixer/mixer
COPY --from=builder-front /usr/src/app/build/ ./public/
RUN sbt assembly
RUN mv `find . -name ergoMixer-*.jar` /ergo-mixer.jar
CMD ["java", "-jar", "/ergo-mixer.jar"]

FROM openjdk:8-jre-slim
RUN adduser --disabled-password --home /home/ergo/ --uid 9052 --gecos "ErgoPlatform" ergo && \
    install -m 0750 -o ergo -g ergo  -d /home/ergo/mixer
COPY --from=builder /ergo-mixer.jar /home/ergo/ergo-mixer.jar
COPY ./mixer/conf/application.conf /home/ergo/mixer/application.conf
RUN chown ergo:ergo /home/ergo/ergo-mixer.jar
USER ergo
EXPOSE 9000
WORKDIR /home/ergo
ENTRYPOINT java -jar -D"config.file"=mixer/application.conf /home/ergo/ergo-mixer.jar

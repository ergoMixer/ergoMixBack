FROM node:16.15.0 as builder-front
WORKDIR /usr/src/app
COPY ./ergomixfront/package.json ./
RUN npm install
COPY ./ergomixfront ./
RUN npm run build

FROM openjdk:8u181-jdk-slim as builder
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get -y --no-install-recommends install curl zip unzip sed
RUN curl -s "https://get.sdkman.io" | bash
RUN /bin/bash -c "source /root/.sdkman/bin/sdkman-init.sh; sdk install sbt 1.2.7"
ENV PATH=/root/.sdkman/candidates/sbt/current/bin:$PATH
WORKDIR /mixer
ADD ["./mixer", "./"]
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

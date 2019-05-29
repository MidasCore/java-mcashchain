FROM ubuntu:16.04 AS builder


RUN apt-get update \
    && apt-get -y upgrade \
    && apt-get -y install wget openjdk-8-jdk unzip \
    && rm -rf /var/lib/apt/lists/*

#COPY cache/.gradle /root/.gradle

#RUN ls /root/.gradle

ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
ENV PATH $JAVA_HOME/bin:$PATH

# Installing gradle
#ENV GRADLE_VERSION 4.10.3
#
#RUN TMPFILE="$(mktemp)" \
#	&& wget "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O "$TMPFILE" \
#	&& unzip "$TMPFILE" -d "/usr/local" \
#	&& mv "/usr/local/gradle-${GRADLE_VERSION}" "/usr/local/gradle" \
#	&& ln -s "../gradle/bin/gradle" "/usr/local/bin/gradle" \
#	&& rm "$TMPFILE"
#
#RUN mkdir ~/.gradle \
#	&& echo "org.gradle.daemon=false" > ~/.gradle/gradle.properties
#
#ENV GRADLE_HOME=/usr/local/gradle-${GRADLE_VERSION}
#ENV PATH=$PATH:$GRADLE_HOME/bin
ENV APP_HOME=/usr/app

WORKDIR $APP_HOME

COPY build.gradle settings.gradle gradlew $APP_HOME/
COPY gradle $APP_HOME/gradle

RUN ./gradlew build || return 0

COPY . .

RUN ./gradlew build -x test -x checkstyleMain -x checkstyleTest

FROM openjdk:8

ENV APP_HOME=/usr/app
ENV APP=FullNode

COPY --from=builder $APP_HOME/entrypoint.sh /entrypoint.sh
COPY --from=builder $APP_HOME/build/libs/$APP.jar $APP_HOME/$APP.jar
COPY --from=builder $APP_HOME/src/main/resources/*.conf $APP_HOME/

RUN chmod +x /entrypoint.sh

WORKDIR $APP_HOME

EXPOSE 11399 8090 8091 50051

ENTRYPOINT [ "/entrypoint.sh" ]


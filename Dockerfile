FROM openjdk:8 AS builder

ENV APP_HOME=/usr/app
ARG BUILD_OPT
WORKDIR $APP_HOME
COPY build.gradle settings.gradle gradlew $APP_HOME/
COPY gradle $APP_HOME/gradle

RUN ./gradlew --version

COPY . .

RUN ./gradlew build $BUILD_OPT

FROM openjdk:8-jre
ENV APP_HOME=/usr/app

RUN mkdir -p $APP_HOME/config

COPY --from=builder $APP_HOME/entrypoint.sh /entrypoint.sh
COPY --from=builder $APP_HOME/build/libs/*.jar $APP_HOME/
COPY --from=builder $APP_HOME/src/main/resources/*.conf $APP_HOME/config/

RUN chmod +x /entrypoint.sh

WORKDIR $APP_HOME

VOLUME $APP_HOME/output

EXPOSE 11399 8090 8091 50051

ENTRYPOINT [ "/entrypoint.sh" ]


# syntax=docker/dockerfile:1

FROM maven:3.9.13-eclipse-temurin-21-noble AS build
WORKDIR /build

COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp package

FROM eclipse-temurin:21-jdk-noble AS verapdf-install
ARG VERAPDF_VERSION=1.30.3

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

COPY docker/verapdf-install.xml /tmp/verapdf-install.xml
RUN curl --fail --location --show-error \
      "https://software.verapdf.org/releases/1.30/verapdf-greenfield-${VERAPDF_VERSION}-installer.zip" \
      --output /tmp/verapdf-installer.zip \
    && unzip -q /tmp/verapdf-installer.zip -d /tmp/verapdf-installer \
    && java -jar \
      "/tmp/verapdf-installer/verapdf-greenfield-${VERAPDF_VERSION}/verapdf-izpack-installer-${VERAPDF_VERSION}.jar" \
      /tmp/verapdf-install.xml

FROM eclipse-temurin:21-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      libreoffice-writer \
      libreoffice-calc \
      libreoffice-impress \
      libreoffice-draw \
      libreoffice-java-common \
      fontconfig \
      fonts-dejavu-core \
      fonts-liberation \
      fonts-crosextra-carlito \
      fonts-crosextra-caladea \
      fonts-noto-core \
    && rm -rf /var/lib/apt/lists/*

COPY --from=verapdf-install /opt/verapdf /opt/verapdf
COPY --from=build /build/target/pdfa-converter-backend-0.0.1-SNAPSHOT.jar /app/app.jar

ENV PATH="/opt/verapdf:${PATH}"

RUN useradd --system --uid 10001 --create-home --home-dir /home/app app \
    && mkdir -p /work /var/opt/verapdf/logs \
    && chown -R app:app /work /opt/verapdf /var/opt/verapdf

USER app
WORKDIR /app
RUN java -version && libreoffice --version && verapdf --version
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

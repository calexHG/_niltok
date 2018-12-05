ARG BUNCHES_VERSION=0.9.0
ARG BUNCHES_DOWNLOAD_SHA256=DF43A2A0A1FC4AD1F1BBE077A25C0C066790C21B7D6910954787DA1D826F3B19
ARG BUNCHES_HOME=/opt/bunches



FROM buildpack-deps:stable-curl AS bunches_downloader

ARG BUNCHES_VERSION
ARG BUNCHES_DOWNLOAD_SHA256
ARG BUNCHES_HOME

RUN set -o errexit -o nounset \
    && echo "Downloading Bunches" \
    && wget --no-verbose --output-document=bunch.zip "https://github.com/JetBrains/bunches/releases/download/v${BUNCHES_VERSION}/bunch-${BUNCHES_VERSION}.zip" \
    \
    && echo "Checking Bunches download's SHA-256 hash against ${BUNCHES_DOWNLOAD_SHA256}" \
    && echo "${BUNCHES_DOWNLOAD_SHA256} bunch.zip" | sha256sum --check - \
    \
    && echo "Preparing to install Bunches (in prep. image)" \
    && apt-get -qq update && apt-get -qqy install unzip \
    \
    && echo "Installing Bunches (in prep. image)" \
    && unzip bunch.zip -d bunch \
    && rm bunch.zip \
    && mv bunch/* "${BUNCHES_HOME}" \
    && ln --symbolic "${BUNCHES_HOME}/bin/bunch" /usr/bin/bunch



FROM openjdk:8-jre-slim

ARG BUNCHES_HOME
ENV BUNCHES_HOME=${BUNCHES_HOME}

RUN set -o errexit -o nounset \
    && echo "Installing Bunches's dependencies" \
    && apt-get -qq update && apt-get -qqy install git && rm -rf /var/lib/apt/lists/* \
    && git config --system user.email "cloudbuilder@ka-github-builds" \
    && git config --system user.name "cloudbuilder" \
    \
    && echo "Installing Bunches" \
    && ln --symbolic "${BUNCHES_HOME}/bin/bunch" /usr/bin/bunch
COPY --from=bunches_downloader ${BUNCHES_HOME} ${BUNCHES_HOME}

ENTRYPOINT ["/usr/bin/bunch"]

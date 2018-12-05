# JDK ##########################################################################
#
# Adapted from
# azul/zulu-openjdk:6,
# azul/zulu-openjdk:7,
# azul/zulu-openjdk:8 and
# azul/zulu-openjdk:9
# (See https://github.com/zulu-openjdk/zulu-openjdk)
#
# Necessary considerations made:
# - JDK installations are bundled into different steps in such a way that
#   the most likely future changes to their order will still allow us to
#   benefit from caching of the un-reordered JDK installations (JDK 6 & 7)
# - Environment variables JDK_16, JDK_17, JDK_18, and JDK_9 are set with
#   values corresponding to the appropriate JAVA_HOME value for each version

FROM azul/zulu-openjdk:6

#
# UTF-8 by default
#
RUN apt-get -qq update && \
    apt-get -qqy install locales && \
    locale-gen en_US.UTF-8 && \
    rm -rf /var/lib/apt/lists/*

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

#
# Pull Zulu OpenJDK binaries from official repository:
#
RUN apt-get -qq update && \
    apt-get -qqy install zulu-7=7.24.0.1 && \
    rm -rf /var/lib/apt/lists/*
RUN apt-get -qq update && \
    apt-get -qqy install zulu-9=9.0.4.1 && \
    apt-get -qqy install zulu-8=8.31.0.1 && \
    rm -rf /var/lib/apt/lists/*

# Add some environment variables normally set by Teamcity build servers
ENV JDK_16=/usr/lib/jvm/zulu-6-amd64
ENV JDK_17=/usr/lib/jvm/zulu-7-amd64
ENV JDK_18=/usr/lib/jvm/zulu-8-amd64
ENV JDK_9=/usr/lib/jvm/zulu-9-amd64

# This should already be set, since we made JDK 8 the last installed JDK above.
ENV JAVA_HOME=/usr/lib/jvm/zulu-8-amd64


# GRADLE #######################################################################
#
# Adapted from
# https://github.com/keeganwitt/docker-gradle/blob/dce2d0cea0b0e60435919b7c7faa686b6cccf2f1/jdk8/Dockerfile
#
# Necessary adaptations included
# - pre-installing `wget`
# - pre-installing `unzip`
# - using the absolute path to `gradle` in the CMD/ENTRYPOINT
# - setting a Docker ENTRYPOINT

# We use an ENTRYPOINT instead.
# CMD ["gradle"]

#
# An attempt at resolving the Google Cloud Build error
# """
# docker: Error response from daemon: OCI runtime create failed: container_linux.go:296: starting container process caused "exec: \"gradle build\": executable file not found in $PATH": unknown.
# """
# ENTRYPOINT ["/usr/bin/gradle"]


ENV GRADLE_HOME /opt/gradle
ENV GRADLE_VERSION 4.9

ARG GRADLE_DOWNLOAD_SHA256=e66e69dce8173dd2004b39ba93586a184628bc6c28461bc771d6835f7f9b0d28
RUN set -o errexit -o nounset \
    \
	&& echo "PREPARING to download Gradle" \
    && apt-get -qq update \
    && apt-get -qqy install unzip \
    && apt-get -qqy install wget \
    \
	&& echo "Downloading Gradle" \
	&& wget --no-verbose --output-document=gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
	\
	&& echo "Checking download hash" \
	&& echo "${GRADLE_DOWNLOAD_SHA256} *gradle.zip" | sha256sum --check - \
	\
	&& echo "Installing Gradle" \
	&& unzip gradle.zip \
	&& rm gradle.zip \
	&& mv "gradle-${GRADLE_VERSION}" "${GRADLE_HOME}/" \
	&& ln --symbolic "${GRADLE_HOME}/bin/gradle" /usr/bin/gradle \
	\
	&& echo "Adding gradle user and group" \
	&& groupadd --system --gid 1000 gradle \
	&& useradd --system --gid gradle --uid 1000 --shell /bin/bash --create-home gradle \
	&& mkdir /home/gradle/.gradle \
	&& chown --recursive gradle:gradle /home/gradle \
	\
	&& echo "Symlinking root Gradle cache to gradle Gradle cache" \
	&& ln -s /home/gradle/.gradle /root/.gradle


#
# We use an absolute path, as an attempt at resolving the Google Cloud Build error
# """
# docker: Error response from daemon: OCI runtime create failed: container_linux.go:296: starting container process caused "exec: \"gradle build\": executable file not found in $PATH": unknown.
# """
#ENTRYPOINT ["/usr/bin/gradle", "--stacktrace"]
CMD /usr/bin/id && /usr/bin/env && exec /usr/bin/gradle --no-daemon --stacktrace --info build


# Create Gradle volume
USER gradle
VOLUME "/home/gradle/.gradle"
WORKDIR /home/gradle

RUN set -o errexit -o nounset \
	&& echo "Testing Gradle installation" \
	&& gradle --version

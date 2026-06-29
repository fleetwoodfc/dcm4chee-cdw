# Dockerfile for dcm4chee-cdw-2.17.1
#
# Runtime: JBoss 4.2.3.GA + dcm4che-1.4.34 + FOP 0.95 + genisoimage
# Java:    OpenJDK 8 (minimum for JBoss 4.2.x; Java 5 target bytecode runs fine)
#
# Build-time dependencies are downloaded from well-known mirrors.
# Override with --build-arg if your environment requires internal mirrors.
#
# Exposed ports:
#   10104  DICOM (C-STORE SCP + Media Creation Management SCP)
#   8080   JBoss HTTP (JMX console)
#   8009   JBoss AJP
#
# ISO output directory:   /var/lib/dcm4chee-cdw/iso   (mount a volume here)
# Spool directory:        /var/lib/dcm4chee-cdw/spool (mount a volume here)
#
# Usage:
#   docker build -t dcm4chee-cdw:2.17.1 .
#   docker run -d \
#     -p 10104:10104 -p 8080:8080 \
#     -v cdw-spool:/var/lib/dcm4chee-cdw/spool \
#     -v cdw-iso:/var/lib/dcm4chee-cdw/iso \
#     dcm4chee-cdw:2.17.1

# ──────────────────────────────────────────────────────────────────────────────
# Stage 1: build
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:8-jdk-focal AS builder

ARG ANT_VERSION=1.10.14
ARG JBOSS_VERSION=4.2.3.GA
ARG DCM4CHE_VERSION=1.4.33
ARG FOP_VERSION=0.95

ENV ANT_HOME=/opt/ant
ENV PATH="${ANT_HOME}/bin:${PATH}"

# Install build tools
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
    && rm -rf /var/lib/apt/lists/*

# Download Ant
RUN curl -fSL "https://archive.apache.org/dist/ant/binaries/apache-ant-${ANT_VERSION}-bin.tar.gz" \
    | tar xz -C /opt \
    && ln -s /opt/apache-ant-${ANT_VERSION} ${ANT_HOME}

# Copy local JBoss 4.2.3.GA zip (jboss-4.2.3.GA/jboss-4.2.3.GA.zip)
COPY jboss-4.2.3.GA/jboss-4.2.3.GA.zip /tmp/jboss.zip
RUN unzip -q /tmp/jboss.zip -d /opt \
    && mv /opt/jboss-${JBOSS_VERSION} /opt/jboss \
    && rm /tmp/jboss.zip

# Download dcm4che-1.4.33 binary distribution
RUN curl -fSL \
    "https://sourceforge.net/projects/dcm4che/files/dcm4che14/${DCM4CHE_VERSION}/dcm4che-${DCM4CHE_VERSION}.zip/download" \
    -o /tmp/dcm4che.zip \
    && unzip -q /tmp/dcm4che.zip -d /opt \
    && mv /opt/dcm4che-${DCM4CHE_VERSION} /opt/dcm4che14 \
    && rm /tmp/dcm4che.zip

# Download Apache FOP 0.95
RUN curl -fSL \
    "https://archive.apache.org/dist/xmlgraphics/fop/binaries/fop-${FOP_VERSION}-bin.tar.gz" \
    | tar xz -C /opt \
    && mv /opt/fop-${FOP_VERSION} /opt/fop \
    && rm -rf /opt/fop/examples

# Copy source and build
WORKDIR /build
COPY . .

RUN ant dist \
    -Djboss.home=/opt/jboss \
    -Ddcm4che14.home=/opt/dcm4che14 \
    -Dfop.home=/opt/fop \
    -Dversion=2.17.1

# The dist target produces target/dcm4chee-cdw-2.17.1.zip
RUN unzip -q target/dcm4chee-cdw-2.17.1.zip -d /opt/dist

# ──────────────────────────────────────────────────────────────────────────────
# Stage 2: runtime image
# ──────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:8-jre-focal

ARG JBOSS_VERSION=4.2.3.GA

ENV JBOSS_HOME=/opt/jboss
ENV DCM4CHEE_HOME=/opt/dcm4chee-cdw
ENV JAVA_OPTS="-Xms128m -Xmx512m -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true"

# Install genisoimage (replaces mkisofs) and runtime libs
RUN apt-get update && apt-get install -y --no-install-recommends \
        genisoimage \
        libxi6 \
        libxrender1 \
        libxtst6 \
    && rm -rf /var/lib/apt/lists/* \
    # Create symlink so any existing mkisofs references resolve
    && ln -s /usr/bin/genisoimage /usr/local/bin/mkisofs

# Copy JBoss from builder
COPY --from=builder /opt/jboss ${JBOSS_HOME}

# Overlay the dcm4chee-cdw distribution onto JBoss server/default
# The dist zip unpacks to dcm4chee-cdw-2.17.1/server/default/...
COPY --from=builder /opt/dist/dcm4chee-cdw-2.17.1/server/default \
     ${JBOSS_HOME}/server/default

# Copy top-level bin/ scripts (run.sh etc.)
COPY --from=builder /opt/dist/dcm4chee-cdw-2.17.1/bin \
     ${JBOSS_HOME}/bin

# Apply JBoss Messaging SAR from distribution
COPY --from=builder /opt/dist/dcm4chee-cdw-2.17.1/server/default/deploy/jboss-messaging.sar \
     ${JBOSS_HOME}/server/default/deploy/jboss-messaging.sar

# Persistent data directories (mount volumes here in production)
RUN mkdir -p \
        /var/lib/dcm4chee-cdw/spool \
        /var/lib/dcm4chee-cdw/iso \
    /var/lib/dcm4chee-cdw/ptburn/jobs \
    /var/lib/dcm4chee-cdw/ptburn/status \
    && mkdir -p ${JBOSS_HOME}/server/default/data/cdw.spool \
    && ln -s /var/lib/dcm4chee-cdw/spool \
             ${JBOSS_HOME}/server/default/data/cdw.spool/archive \
    && ln -s /var/lib/dcm4chee-cdw/iso \
           ${JBOSS_HOME}/server/default/data/cdw.spool/iso

# Ensure run.sh is executable
RUN chmod +x ${JBOSS_HOME}/bin/run.sh

VOLUME ["/var/lib/dcm4chee-cdw/spool", "/var/lib/dcm4chee-cdw/iso", "/var/lib/dcm4chee-cdw/ptburn"]

EXPOSE 10104 8080 8009 1099 4444

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8080/jmx-console/ > /dev/null || exit 1

WORKDIR ${JBOSS_HOME}

CMD ["bin/run.sh", "-c", "default", "-b", "0.0.0.0"]

FROM discoenv/dataone-base

ENV CONSUL_TEMPLATE_BASE=https://releases.hashicorp.com/consul-template
ENV CONSUL_TEMPLATE_VERSION=0.16.0
ENV CONSUL_TEMPLATE_SHA256SUM=064b0b492bb7ca3663811d297436a4bbf3226de706d2b76adade7021cd22e156
ENV CONSUL_TEMPLATE_FILE=consul-template_${CONSUL_TEMPLATE_VERSION}_linux_amd64.zip

ADD ${CONSUL_TEMPLATE_BASE}/${CONSUL_TEMPLATE_VERSION}/${CONSUL_TEMPLATE_FILE} .

RUN echo "${CONSUL_TEMPLATE_SHA256SUM}  ${CONSUL_TEMPLATE_FILE}" | sha256sum -c - \
    && unzip ${CONSUL_TEMPLATE_FILE} \
    && mkdir -p /usr/local/bin \
    && mv consul-template /usr/local/bin/consul-template

COPY pid-service/target/dataone-pid-service-standalone.jar /etc/irods-ext/d1plugins/
COPY repo-service/target/dataone-repo-service-standalone.jar /etc/irods-ext/d1plugins/
COPY consul.hcl /
COPY d1client.properties.tmpl /
COPY generate-configs.sh /usr/local/bin/

ENV CLJ_VERSION="1.8.0"
ENV CLJ_FILE="clojure-${CLJ_VERSION}.jar"
ENV CLJ_URL="http://search.maven.org/remotecontent?filepath=org/clojure/clojure/${CLJ_VERSION}/${CLJ_FILE}"
ADD "${CLJ_URL}" "/tmp/lib/${CLJ_FILE}"

#!/bin/bash
IFS=$'\n\t'
set -euxo pipefail


HOST='dblof.broadinstitute.org'
IP='69.173.112.15'
EMAIL='dmohs@broadinstitute.org'
SUBJ="/C=US/ST=Massachusetts/L=Cambridge/O=Broad Institute/CN='"$HOST"'/emailAddress=$EMAIL"
KEYPASS='akekj48d7h4kd0xk'

# ssh -t "$HOST" <<EOF
# set -euxo pipefail
# sudo apt-get update
# sudo apt-get install apt-transport-https ca-certificates
# sudo apt-key adv --keyserver hkp://p80.pool.sks-keyservers.net:80 \
#   --recv-keys 58118E89F3A912897C070ADBF76221572C52609D
# sudo bash -c "echo 'deb https://apt.dockerproject.org/repo ubuntu-trusty main' > \
#   /etc/apt/sources.list.d/docker.list"
# sudo apt-get update
# sudo apt-get purge lxc-docker
# sudo apt-cache policy docker-engine
# sudo apt-get install linux-image-extra-\$(uname -r)
# sudo apt-get install apparmor
# sudo apt-get install -y docker-engine
# EOF

function create-server-keys() {
  ssh "$HOST" openssl genrsa -aes256 -passout pass:"$KEYPASS" -out ca-key.pem 4096
  CMD="openssl req -new -x509 -days 365 -key ca-key.pem -passin pass:$KEYPASS -sha256"
  CMD="$CMD -out ca.pem -subj '$SUBJ'"
  ssh "$HOST" "$CMD"
  ssh -t "$HOST" openssl genrsa -out server-key.pem 4096
  ssh -t "$HOST" openssl req -subj "/CN=$HOST" -sha256 -new -key server-key.pem -out server.csr
  ssh "$HOST" "bash -c 'echo subjectAltName = IP:$IP > extfile.cnf'"
  ssh "$HOST" openssl x509 -req -days 365 -sha256 -in server.csr -CA ca.pem \
    -CAkey ca-key.pem -passin pass:"$KEYPASS" \
    -CAcreateserial -out server-cert.pem -extfile extfile.cnf
  ssh "$HOST" rm -v server.csr extfile.cnf
  ssh "$HOST" chmod -v 0400 ca-key.pem server-key.pem
  ssh "$HOST" chmod -v 0444 ca.pem server-cert.pem
}

function create-client-keys() {
  ssh "$HOST" openssl genrsa -out key.pem 4096
  ssh "$HOST" openssl req -subj '/CN=client' -new -key key.pem -out client.csr
  ssh "$HOST" "bash -c 'echo extendedKeyUsage = clientAuth > extfile.cnf'"
  ssh "$HOST" openssl x509 -req -days 365 -sha256 -in client.csr \
    -CA /etc/ssl/certs/docker/ca.pem \
    -CAkey /etc/ssl/certs/docker/ca-key.pem -passin pass:"$KEYPASS" \
    -CAcreateserial -out cert.pem -extfile extfile.cnf
  ssh "$HOST" rm -v client.csr extfile.cnf
  ssh "$HOST" chmod -v 0400 key.pem
  ssh "$HOST" chmod -v 0444 cert.pem
  scp "$HOST":/etc/ssl/certs/docker/ca.pem docker
  scp "$HOST":cert.pem docker
  scp "$HOST":key.pem docker
}

create-client-keys


# ssh -t "$HOST" <<EOF
# sudo bash -c 'echo >> /etc/default/docker'
# sudo bash -c "echo 'DOCKER_OPTS=\"--tlsverify --tlscacert=/home/dmohs/docker-certs/ca.pem --tlscert=/home/dmohs/docker-certs/server-cert.pem --tlskey=/home/dmohs/docker-certs/server-key.pem -H=0.0.0.0:2376\"' >> /etc/default/docker"
# EOF

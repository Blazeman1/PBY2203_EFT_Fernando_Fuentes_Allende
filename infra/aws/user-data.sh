#!/bin/bash
# Script de arranque (user data) de la instancia EC2 que aloja el ecosistema Banco XYZ.
# cloud-init lo ejecuta UNA vez, como root, en el primer arranque de la instancia. Deja la
# maquina lista (Docker + docker compose + el repositorio clonado) y levanta el stack completo.
#
# Pensado para Ubuntu Server 24.04 LTS en una instancia t3.large (2 vCPU, 8 GB de RAM).
# Lo usa infra/aws/desplegar-ec2.sh, que reemplaza __REPO_URL__ por la URL del repositorio.
# Tambien puede pegarse a mano en "Advanced details > User data" de la consola de EC2.
#
# Bitacora de este script en la instancia: /var/log/banco-xyz-arranque.log
set -euxo pipefail
exec > >(tee -a /var/log/banco-xyz-arranque.log) 2>&1

REPO_URL="__REPO_URL__"
DIRECTORIO=/opt/banco-xyz

# --- 1. Memoria de intercambio --------------------------------------------------------------
# El stack levanta hasta 15 JVM (con los microservicios escalados) mas Kafka y PostgreSQL. Los
# 8 GB de una t3.large alcanzan en regimen normal; 4 GB de swap absorben el pico del arranque
# simultaneo y de la compilacion de las imagenes, evitando que el kernel mate procesos (OOM).
if [ ! -f /swapfile ]; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# --- 2. Docker Engine + plugins "compose" y "buildx" (instalador oficial de Docker) ---------
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl git jq
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
usermod -aG docker ubuntu

# Rotacion de logs de los contenedores: evita que llenen el disco de la instancia.
cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "json-file", "log-opts": { "max-size": "20m", "max-file": "3" } }
JSON
systemctl restart docker

# --- 3. Codigo fuente -------------------------------------------------------------------------
rm -rf "$DIRECTORIO"
git clone --depth 1 "$REPO_URL" "$DIRECTORIO"
chown -R ubuntu:ubuntu "$DIRECTORIO"
cd "$DIRECTORIO"

# --- 4. Construccion de las imagenes y levantamiento del stack ------------------------------
# (la primera construccion descarga las dependencias de Maven: tarda entre 10 y 15 minutos)
docker compose -f docker-compose.yaml --profile batch build
docker compose -f docker-compose.yaml up -d

echo "ARRANQUE COMPLETADO: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

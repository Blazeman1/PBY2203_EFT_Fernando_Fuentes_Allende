#!/usr/bin/env bash
# Crea en AWS la infraestructura minima para alojar el ecosistema Banco XYZ: un Security Group
# y una instancia EC2 (Ubuntu 24.04, t3.large) que, al arrancar, instala Docker, clona el
# repositorio y levanta el stack con docker compose (ver infra/aws/user-data.sh).
#
# Pensado para ejecutarse desde AWS CloudShell dentro de AWS Academy Learner Lab (region
# us-east-1), donde la AWS CLI ya viene configurada con las credenciales temporales del
# laboratorio. Usa solo recursos permitidos en el Learner Lab: el par de claves "vockey" y el
# perfil de instancia "LabInstanceProfile" (rol LabRole), ambos creados por el laboratorio.
#
# Uso:
#   bash infra/aws/desplegar-ec2.sh <URL_DEL_REPOSITORIO_PUBLICO> <MI_IP_PUBLICA>
# Ejemplo:
#   bash infra/aws/desplegar-ec2.sh https://github.com/usuario/repositorio.git 203.0.113.25
#
# <MI_IP_PUBLICA> es la IP del computador desde donde se administrara y probara el sistema
# (se obtiene abriendo https://checkip.amazonaws.com en el navegador). El acceso SSH (22) y el
# panel de Eureka (8761) quedan restringidos a esa IP; solo el api-gateway (8443) queda abierto.
set -euo pipefail

REPO_URL="${1:?Falta la URL del repositorio (ver el encabezado de este script)}"
MI_IP="${2:?Falta tu IP publica (https://checkip.amazonaws.com)}"

REGION="${AWS_REGION:-us-east-1}"
NOMBRE="banco-xyz-eft"
TIPO_INSTANCIA="${TIPO_INSTANCIA:-t3.large}"
PAR_DE_CLAVES="${PAR_DE_CLAVES:-vockey}"
PERFIL_INSTANCIA="${PERFIL_INSTANCIA:-LabInstanceProfile}"

cd "$(dirname "$0")"

echo "=== 1. Red: VPC por defecto de la region $REGION ==="
VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
echo "VPC: $VPC_ID"

echo "=== 2. Security Group (minimo privilegio de red) ==="
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
  --filters Name=group-name,Values="$NOMBRE-sg" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)
if [ -z "$SG_ID" ] || [ "$SG_ID" = "None" ]; then
  SG_ID=$(aws ec2 create-security-group --region "$REGION" --group-name "$NOMBRE-sg" --vpc-id "$VPC_ID" \
    --description "Banco XYZ EFT: gateway publico, administracion restringida" --query GroupId --output text)
  # Unico puerto abierto a Internet: el api-gateway (HTTPS).
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" --protocol tcp --port 8443 --cidr 0.0.0.0/0 >/dev/null
  # Administracion (SSH) y panel de Eureka: solo desde la IP indicada.
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" --protocol tcp --port 22 --cidr "$MI_IP/32" >/dev/null
  aws ec2 authorize-security-group-ingress --region "$REGION" --group-id "$SG_ID" --protocol tcp --port 8761 --cidr "$MI_IP/32" >/dev/null
fi
echo "Security Group: $SG_ID"

echo "=== 3. AMI oficial de Ubuntu Server 24.04 LTS (resuelta por parametro publico de SSM) ==="
AMI_ID=$(aws ssm get-parameter --region "$REGION" \
  --name /aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id \
  --query Parameter.Value --output text 2>/dev/null || true)
if [ -z "$AMI_ID" ] || [ "$AMI_ID" = "None" ]; then
  # Alternativa si el laboratorio no permite leer parametros de SSM: la imagen mas reciente
  # publicada por Canonical (propietario 099720109477).
  AMI_ID=$(aws ec2 describe-images --region "$REGION" --owners 099720109477 \
    --filters 'Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*' Name=state,Values=available \
    --query 'sort_by(Images, &CreationDate)[-1].ImageId' --output text)
fi
echo "AMI: $AMI_ID"

echo "=== 4. Instancia EC2 $TIPO_INSTANCIA (disco de 30 GB gp3) ==="
sed "s|__REPO_URL__|$REPO_URL|" user-data.sh > /tmp/user-data-banco-xyz.sh
INSTANCE_ID=$(aws ec2 run-instances --region "$REGION" \
  --image-id "$AMI_ID" --instance-type "$TIPO_INSTANCIA" \
  --key-name "$PAR_DE_CLAVES" --security-group-ids "$SG_ID" \
  --iam-instance-profile Name="$PERFIL_INSTANCIA" \
  --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=30,VolumeType=gp3,DeleteOnTermination=true}' \
  --metadata-options HttpTokens=required \
  --user-data file:///tmp/user-data-banco-xyz.sh \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$NOMBRE},{Key=Proyecto,Value=PBY2203-EFT}]" \
  --query 'Instances[0].InstanceId' --output text)
echo "Instancia: $INSTANCE_ID. Esperando a que este en ejecucion..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$INSTANCE_ID"
IP_PUBLICA=$(aws ec2 describe-instances --region "$REGION" --instance-ids "$INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

cat <<FIN

=== Instancia creada ===
  Id de instancia : $INSTANCE_ID
  IP publica      : $IP_PUBLICA

La instancia esta instalando Docker, construyendo las 12 imagenes y levantando el stack.
La primera vez tarda entre 15 y 20 minutos. Para seguir el avance:

  ssh -i labsuser.pem ubuntu@$IP_PUBLICA 'tail -f /var/log/banco-xyz-arranque.log'

Cuando la bitacora muestre "ARRANQUE COMPLETADO":

  Punto de entrada (api-gateway) : https://$IP_PUBLICA:8443
  Panel de Eureka (solo tu IP)   : http://$IP_PUBLICA:8761

Para eliminar todo al terminar (el Learner Lab descuenta presupuesto mientras la instancia exista):

  aws ec2 terminate-instances --region $REGION --instance-ids $INSTANCE_ID
  aws ec2 wait instance-terminated --region $REGION --instance-ids $INSTANCE_ID
  aws ec2 delete-security-group --region $REGION --group-id $SG_ID
FIN

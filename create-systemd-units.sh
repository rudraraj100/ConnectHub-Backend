#!/bin/bash
# ============================================================
# ConnectHub — Create all systemd unit files on EC2
# Run this script on the EC2 instance as ec2-user:
#   bash /tmp/create-systemd-units.sh
# ============================================================

SERVICES_DIR=/home/connecthub/services
LOGS_DIR=/home/connecthub/logs
ENV_FILE=$SERVICES_DIR/.env
JAVA=/usr/bin/java

declare -A SERVICE_PORTS=(
  [connecthub-registry]=8761
  [connecthub-auth]=8081
  [connecthub-room]=8082
  [connecthub-message]=8083
  [connecthub-media]=8084
  [connecthub-presence]=8085
  [connecthub-notification]=8086
  [connecthub-websocket]=8087
  [connecthub-payment]=8088
  [connecthub-gateway]=8080
)

declare -A SERVICE_JARS=(
  [connecthub-registry]=service-registry-0.0.1-SNAPSHOT.jar
  [connecthub-auth]=auth-service-0.0.1-SNAPSHOT.jar
  [connecthub-room]=room-servcie-0.0.1-SNAPSHOT.jar
  [connecthub-message]=message-service-0.0.1-SNAPSHOT.jar
  [connecthub-media]=media-service-0.0.1-SNAPSHOT.jar
  [connecthub-presence]=presence-service-0.0.1-SNAPSHOT.jar
  [connecthub-notification]=notification-service-0.0.1-SNAPSHOT.jar
  [connecthub-websocket]=websocket-handler-0.0.1-SNAPSHOT.jar
  [connecthub-payment]=payment-service-0.0.1-SNAPSHOT.jar
  [connecthub-gateway]=api-gateway-0.0.1-SNAPSHOT.jar
)

# Gateway starts after all other services are up
declare -A SERVICE_AFTER=(
  [connecthub-gateway]="connecthub-registry.service connecthub-auth.service connecthub-room.service connecthub-message.service connecthub-media.service connecthub-presence.service connecthub-notification.service connecthub-websocket.service connecthub-payment.service"
  [connecthub-auth]="connecthub-registry.service"
  [connecthub-room]="connecthub-registry.service"
  [connecthub-message]="connecthub-registry.service"
  [connecthub-media]="connecthub-registry.service"
  [connecthub-presence]="connecthub-registry.service"
  [connecthub-notification]="connecthub-registry.service"
  [connecthub-websocket]="connecthub-registry.service"
  [connecthub-payment]="connecthub-registry.service"
  [connecthub-registry]="network.target mysqld.service redis6.service rabbitmq-server.service"
)

for SERVICE in "${!SERVICE_JARS[@]}"; do
  JAR="${SERVICE_JARS[$SERVICE]}"
  AFTER="${SERVICE_AFTER[$SERVICE]:-network.target}"

  cat > /tmp/${SERVICE}.service << EOF
[Unit]
Description=ConnectHub ${SERVICE}
After=${AFTER}
Requires=network.target

[Service]
User=connecthub
WorkingDirectory=${SERVICES_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=${JAVA} -jar -Dspring.profiles.active=prod ${SERVICES_DIR}/${JAR}
SuccessExitStatus=143
StandardOutput=append:${LOGS_DIR}/${SERVICE}.log
StandardError=append:${LOGS_DIR}/${SERVICE}.log
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  sudo mv /tmp/${SERVICE}.service /etc/systemd/system/${SERVICE}.service
  echo "Created /etc/systemd/system/${SERVICE}.service"
done

sudo systemctl daemon-reload
echo ""
echo "All unit files created. Enable them with:"
echo "  sudo systemctl enable connecthub-registry connecthub-auth connecthub-room connecthub-message connecthub-media connecthub-presence connecthub-notification connecthub-websocket connecthub-payment connecthub-gateway"

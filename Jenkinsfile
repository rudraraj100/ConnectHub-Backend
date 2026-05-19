pipeline {
    agent any

    environment {
        EC2_HOST      = 'ec2-user@13.63.37.252'
        SERVICES_DIR  = '/home/connecthub/services'
        KEY_FILE      = 'ec2-prod-ssh-key'
    }

    stages {

        // ── Stage 1: Checkout ─────────────────────────────────────
        stage('Checkout') {
            steps {
                cleanWs()
                checkout scm
                script {
                    env.GIT_COMMIT_MSG = sh(
                        script: 'git log -1 --pretty=%B',
                        returnStdout: true
                    ).trim()
                    echo "📦 Building: ${env.GIT_COMMIT_MSG}"
                }
            }
        }

        // ── Stage 2: Detect Changes ───────────────────────────────
        stage('Detect Changes') {
            steps {
                script {
                    def changedFiles = sh(
                        script: 'git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only HEAD 2>/dev/null || echo "all"',
                        returnStdout: true
                    ).trim()

                    echo "📝 Changed files:\n${changedFiles}"

                    env.BUILD_REGISTRY     = (changedFiles.contains('service-registry')     || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_GATEWAY      = (changedFiles.contains('api-gateway')           || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_AUTH         = (changedFiles.contains('auth-service')          || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_ROOM         = (changedFiles.contains('room-servcie')          || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_MESSAGE      = (changedFiles.contains('message-service')       || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_MEDIA        = (changedFiles.contains('media-service')         || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_PRESENCE     = (changedFiles.contains('presence-service')      || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_NOTIFICATION = (changedFiles.contains('notification-service')  || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_PAYMENT      = (changedFiles.contains('payment-service')       || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_WEBSOCKET    = (changedFiles.contains('websocket-handler')     || changedFiles == 'all') ? 'true' : 'false'
                    env.BUILD_ADMIN        = (changedFiles.contains('admin-server')            || changedFiles == 'all') ? 'true' : 'false'

                    echo """
                    🔍 Build Plan:
                    Registry: ${env.BUILD_REGISTRY}  |  Gateway: ${env.BUILD_GATEWAY}  |  Auth: ${env.BUILD_AUTH}
                    Room: ${env.BUILD_ROOM}  |  Message: ${env.BUILD_MESSAGE}  |  Media: ${env.BUILD_MEDIA}
                    Presence: ${env.BUILD_PRESENCE}  |  Notification: ${env.BUILD_NOTIFICATION}
                    Payment: ${env.BUILD_PAYMENT}  |  WebSocket: ${env.BUILD_WEBSOCKET}  |  Admin: ${env.BUILD_ADMIN}
                    """
                }
            }
        }

        // ── Stage 3: Build Services (Parallel) ───────────────────
        stage('Build') {
            parallel {
                stage('Registry') {
                    when { expression { env.BUILD_REGISTRY == 'true' } }
                    steps { dir('service-registry') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Gateway') {
                    when { expression { env.BUILD_GATEWAY == 'true' } }
                    steps { dir('api-gateway') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Auth') {
                    when { expression { env.BUILD_AUTH == 'true' } }
                    steps { dir('auth-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Room') {
                    when { expression { env.BUILD_ROOM == 'true' } }
                    steps { dir('room-servcie') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Message') {
                    when { expression { env.BUILD_MESSAGE == 'true' } }
                    steps { dir('message-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Media') {
                    when { expression { env.BUILD_MEDIA == 'true' } }
                    steps { dir('media-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Presence') {
                    when { expression { env.BUILD_PRESENCE == 'true' } }
                    steps { dir('presence-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Notification') {
                    when { expression { env.BUILD_NOTIFICATION == 'true' } }
                    steps { dir('notification-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Payment') {
                    when { expression { env.BUILD_PAYMENT == 'true' } }
                    steps { dir('payment-service') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('WebSocket') {
                    when { expression { env.BUILD_WEBSOCKET == 'true' } }
                    steps { dir('websocket-handler') { sh 'mvn clean package -DskipTests -q' } }
                }
                stage('Admin') {
                    when { expression { env.BUILD_ADMIN == 'true' } }
                    steps { dir('admin-server') { sh 'mvn clean package -DskipTests -q' } }
                }
            }
        }

        // ── Stage 4: Deploy to Production EC2 ────────────────────
        stage('Deploy') {
            steps {
                sshagent(credentials: ['ec2-prod-ssh-key']) {
                    script {

                        def deploy = { String jarName, String svcName ->
                            sh """
                                scp -o StrictHostKeyChecking=no ${jarName} ${EC2_HOST}:/tmp/
                                ssh -o StrictHostKeyChecking=no ${EC2_HOST} '
                                    sudo systemctl stop ${svcName} || true
                                    sudo cp /tmp/${jarName} ${SERVICES_DIR}/${jarName}
                                    sudo chown connecthub:connecthub ${SERVICES_DIR}/${jarName}
                                    sudo systemctl start ${svcName}
                                    sleep 8
                                    sudo systemctl is-active ${svcName} && echo "✅ ${svcName} is UP" || echo "❌ ${svcName} FAILED"
                                '
                            """
                        }

                        if (env.BUILD_REGISTRY == 'true')
                            deploy('service-registry/target/service-registry-0.0.1-SNAPSHOT.jar', 'connecthub-registry')

                        if (env.BUILD_AUTH == 'true')
                            deploy('auth-service/target/auth-service-0.0.1-SNAPSHOT.jar', 'connecthub-auth')

                        if (env.BUILD_GATEWAY == 'true')
                            deploy('api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar', 'connecthub-gateway')

                        if (env.BUILD_ROOM == 'true')
                            deploy('room-servcie/target/room-servcie-0.0.1-SNAPSHOT.jar', 'connecthub-room')

                        if (env.BUILD_MESSAGE == 'true')
                            deploy('message-service/target/message-service-0.0.1-SNAPSHOT.jar', 'connecthub-message')

                        if (env.BUILD_MEDIA == 'true')
                            deploy('media-service/target/media-service-0.0.1-SNAPSHOT.jar', 'connecthub-media')

                        if (env.BUILD_PRESENCE == 'true')
                            deploy('presence-service/target/presence-service-0.0.1-SNAPSHOT.jar', 'connecthub-presence')

                        if (env.BUILD_NOTIFICATION == 'true')
                            deploy('notification-service/target/notification-service-0.0.1-SNAPSHOT.jar', 'connecthub-notification')

                        if (env.BUILD_PAYMENT == 'true')
                            deploy('payment-service/target/payment-service-0.0.1-SNAPSHOT.jar', 'connecthub-payment')

                        if (env.BUILD_WEBSOCKET == 'true')
                            deploy('websocket-handler/target/websocket-handler-0.0.1-SNAPSHOT.jar', 'connecthub-websocket')

                        if (env.BUILD_ADMIN == 'true')
                            deploy('admin-server/target/admin-server-0.0.1-SNAPSHOT.jar', 'connecthub-admin')
                    }
                }
            }
        }

        // ── Stage 5: Health Check ─────────────────────────────────
        stage('Health Check') {
            steps {
                sshagent(credentials: ['ec2-prod-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${EC2_HOST} '
                            echo "=== 🏥 ConnectHub Service Health ==="
                            for s in registry auth gateway room message media presence notification payment websocket admin; do
                                STATUS=\$(sudo systemctl is-active connecthub-\$s 2>/dev/null)
                                if [ "\$STATUS" = "active" ]; then
                                    echo "✅ connecthub-\$s"
                                else
                                    echo "❌ connecthub-\$s (\$STATUS)"
                                fi
                            done
                        '
                    """
                }
            }
        }
    }

    // ── Post Actions ──────────────────────────────────────────────
    post {
        success {
            echo '🚀 Deployment successful! ConnectHub Backend is live at http://connecthub.duckdns.org'
        }
        failure {
            echo '❌ Deployment FAILED! Check the logs above for details.'
        }
        always {
            cleanWs()
        }
    }
}

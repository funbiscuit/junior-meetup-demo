
def deploy() {
    // Вместо сборки здесь следовало бы получить последнюю доступную версию приложения из Nexus
    withMaven(maven: 'mvn') {
        sh 'mvn -f app/pom.xml package -DskipTests'
        sh 'mv app/target/meetup-0.0.1-SNAPSHOT.jar app/target/meetup.jar'
    }

    withEnv([
            'SSH_USER=skokurin',
            'SSH_HOST=10.107.1.112',
    ]) {
        // Загружаем Jar на прод-сервер и перезапускаем сервис
        sshagent(credentials: ['prod_access_ssh']) {
            sh """
            scp -oStrictHostKeyChecking=no app/target/meetup.jar $SSH_USER@$SSH_HOST:/opt/meetup/app.jar

            ssh -oStrictHostKeyChecking=no $SSH_USER@$SSH_HOST -t 'sudo systemctl restart app.service'
        """
        }
    }
}

// Используем любую ноду для выполнения пайплайна
node {
    // Единственный этап - Deploy
    stage('Deploy') {
        // Получаем репозиторий
        checkout([
                $class           : 'GitSCM',
                branches         : [[name: 'develop']],
                userRemoteConfigs: [[url: 'git@gitsd.naumen.ru:skokurin/meetup-demo-09-2023.git', credentialsId: 'git_access_ssh']],
                extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'repo']]
        ])
        try {
            dir('repo') {
                withEnv(['JAVA_HOME=/opt/jdk-17.0.8+7']) {
                    deploy()
                }
            }
        } finally {
            // очистка рабочей директории
            cleanWs()
        }
    }
}


//noinspection GroovyAssignabilityCheck
properties([
        parameters([
                string(name: 'BRANCH', description: 'Имя ветки задачи', trim: true),
        ])
])


def buildAndTestApp() {
    // Собираем JAR
    withMaven(maven: 'mvn') {
        sh 'mvn -f app/pom.xml package -DskipTests'
        sh 'mv app/target/meetup-0.0.1-SNAPSHOT.jar app/target/meetup.jar'
    }

    // Запускаем приложение в фоне
    sh """
        $JAVA_HOME/bin/java -Xmx128m -Dserver.port=8888 -jar app/target/meetup.jar &
        echo \$! > app.pid
    """

    try {
        // ожидание старта приложения
        timeout(time: 60, unit: 'SECONDS') {
            waitUntil(initialRecurrencePeriod: 1000) {
                try {
                    // Основная страница должна вернуть страницу со статусом 200 OK
                    def request = httpRequest 'http://localhost:8888'
                    return request.status == 200
                }
                catch (def ignored) {
                    return false
                }
            }
        }

        // Запускаем тесты
        withMaven(maven: 'mvn') {
            sh 'mvn -f app-test/pom.xml test -Dapp.port=8888'
        }
    } finally {
        // остановка приложения
        sh 'while ps -p $(cat app.pid); do kill $(cat app.pid); sleep 1; done'
    }
}

def mergeBranch() {
    sshagent(credentials: ['git_access_ssh']) {
        sh """
            git checkout -q develop
            git pull
            git checkout ${params.BRANCH}
            git rebase develop
            git checkout develop
            git merge ${params.BRANCH}
            git push origin develop
        """
    }
}

// Используем любую ноду для выполнения пайплайна
node {
    // Этап подготовки
    stage('Prepare') {
        // Проверяем, что не пытаемся влить изменения из develop в самого себя
        if (params.url == 'develop') {
            currentBuild.result = 'ABORTED'
            error('Can\'t integrate into develop')
        }
        // Получаем репозиторий
        checkout([
                $class           : 'GitSCM',
                branches         : [[name: params.BRANCH]],
                userRemoteConfigs: [[url: 'git@gitsd.naumen.ru:skokurin/meetup-demo-09-2023.git', credentialsId: 'git_access_ssh']],
                extensions       : [[$class: 'RelativeTargetDirectory', relativeTargetDir: "repo"]]
        ])
    }

    try {
        // Выполняем действия в каталоге с полученным кодом проекта
        dir('repo') {
            // Устанавливаем переменную среды JAVA_HOME для работы maven
            withEnv(['JAVA_HOME=/opt/jdk-17.0.8+7']) {
                // Этап со сборкой и тестированием приложения
                stage('BuildTest') {
                    buildAndTestApp()
                }
            }
            // Этап слияния ветвей
            stage('Merge') {
                mergeBranch()
            }
        }
    } finally {
        // очистка рабочей директории
        cleanWs()
    }
}


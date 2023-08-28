#!/bin/bash
# Запускать из корня репозитория

SSH_USER=skokurin
SSH_HOST=10.107.1.112

# Собираем jar
mvn -f app/pom.xml package -DskipTests
mv app/target/meetup-0.0.1-SNAPSHOT.jar app/target/meetup.jar

# Запускаем приложение в фоне
java -jar app/target/meetup.jar &
PID=$!
echo "APP PID: $PID"

# Запускаем тесты
mvn -f app-test/pom.xml test
EXIT_CODE=$?

# Останавливаем приложение
kill -s SIGTERM "$PID"
wait "$PID"

if [ $EXIT_CODE -ne 0 ]; then
  echo "Tests have failed. Aborting deploy!"
  exit $EXIT_CODE
fi

# если тест прошел успешно, то загружаем jar на сервер
scp app/target/meetup.jar $SSH_USER@$SSH_HOST:/opt/meetup/app.jar

# Перезапускаем сервис
ssh $SSH_USER@$SSH_HOST -t 'sudo systemctl restart app.service'

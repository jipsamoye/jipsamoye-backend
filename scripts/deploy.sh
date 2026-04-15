#!/bin/bash
set -e

APP_DIR="/home/ubuntu/app"
ACTIVE_FILE="$APP_DIR/active-color"
NGINX_CONF_DIR="$APP_DIR/nginx"

# 현재 활성 컨테이너 확인
if [ -f "$ACTIVE_FILE" ]; then
    CURRENT=$(cat "$ACTIVE_FILE")
else
    CURRENT="none"
fi

# 타겟 결정
if [ "$CURRENT" = "blue" ]; then
    TARGET="green"
else
    TARGET="blue"
fi

echo "===== Blue-Green 배포 시작 ====="
echo "현재 활성: $CURRENT"
echo "배포 타겟: $TARGET"

# .env 파일 생성
cd "$APP_DIR"

# DB + Nginx가 안 떠있으면 먼저 시작
echo "DB + Nginx 확인 및 시작..."
docker compose up -d db nginx

# DB 헬스체크 대기
echo "DB 헬스체크 대기..."
until docker exec jipsamoye-db mysqladmin ping -h localhost --silent 2>/dev/null; do
    sleep 2
done
echo "DB 준비 완료"

# 타겟 컨테이너 빌드 + 시작
echo "[$TARGET] 컨테이너 빌드 및 시작..."
docker compose --profile "$TARGET" up -d --build "app-$TARGET"

# 헬스체크 (최대 60초 대기)
echo "[$TARGET] 헬스체크 시작..."
MAX_RETRIES=30
RETRY_INTERVAL=2

for i in $(seq 1 $MAX_RETRIES); do
    STATUS=$(docker exec "jipsamoye-app-$TARGET" curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$STATUS" = "200" ]; then
        echo "[$TARGET] 헬스체크 통과! (${i}번째 시도)"
        break
    fi
    if [ "$i" = "$MAX_RETRIES" ]; then
        echo "[$TARGET] 헬스체크 실패! 배포 중단."
        docker compose --profile "$TARGET" stop "app-$TARGET"
        exit 1
    fi
    echo "[$TARGET] 대기 중... ($i/$MAX_RETRIES)"
    sleep $RETRY_INTERVAL
done

# Nginx upstream 전환
echo "Nginx upstream을 $TARGET으로 전환..."
cp "$NGINX_CONF_DIR/upstream-$TARGET.conf" "$NGINX_CONF_DIR/upstream.conf"
docker exec jipsamoye-nginx nginx -s reload

# active-color 파일 갱신
echo "$TARGET" > "$ACTIVE_FILE"
echo "활성 컨테이너: $TARGET"

# 이전 컨테이너 종료 (30초 drain 후)
if [ "$CURRENT" != "none" ]; then
    echo "[$CURRENT] 30초 후 이전 컨테이너 종료..."
    sleep 30
    docker compose --profile "$CURRENT" stop "app-$CURRENT"
    echo "[$CURRENT] 종료 완료"
fi

# 오래된 Docker 이미지 정리
docker image prune -f

echo "===== Blue-Green 배포 완료! ====="
echo "활성: $TARGET"

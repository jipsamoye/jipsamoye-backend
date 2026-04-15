#!/bin/bash
set -e

APP_DIR="/home/ubuntu/app"
ACTIVE_FILE="$APP_DIR/active-color"
NGINX_CONF_DIR="$APP_DIR/nginx"
DISCORD_WEBHOOK_URL=$(grep DISCORD_WEBHOOK_URL "$APP_DIR/.env" | cut -d'=' -f2)
COMMIT_MSG=$(cd "$APP_DIR" && cat build/libs/*.jar > /dev/null 2>&1; git -C /tmp log --oneline -1 2>/dev/null || echo "unknown")

# Discord 알림 함수
send_discord() {
    local message="$1"
    if [ -n "$DISCORD_WEBHOOK_URL" ]; then
        curl -s -H "Content-Type: application/json" \
             -d "{\"content\":\"$message\"}" \
             "$DISCORD_WEBHOOK_URL" > /dev/null 2>&1 || true
    fi
}

TIMESTAMP=$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')

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
HEALTH_PASSED=false

for i in $(seq 1 $MAX_RETRIES); do
    STATUS=$(docker exec "jipsamoye-app-$TARGET" curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null || echo "000")
    if [ "$STATUS" = "200" ]; then
        echo "[$TARGET] 헬스체크 통과! (${i}번째 시도)"
        HEALTH_PASSED=true
        break
    fi
    if [ "$i" = "$MAX_RETRIES" ]; then
        echo "[$TARGET] 헬스체크 실패! 배포 중단."
        docker compose --profile "$TARGET" stop "app-$TARGET"
        FAIL_TIMESTAMP=$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')
        send_discord "❌ **배포 실패**\\n━━━━━━━━━━━━━━━\\n📦 타겟: $TARGET\\n💥 원인: 헬스체크 실패 (60초 초과)\\n⏰ $FAIL_TIMESTAMP"
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

# 배포 성공 알림
SWITCH_MSG=""
if [ "$CURRENT" != "none" ]; then
    SWITCH_MSG="🔄 $(echo $CURRENT | tr '[:lower:]' '[:upper:]') → $(echo $TARGET | tr '[:lower:]' '[:upper:]') 전환"
else
    SWITCH_MSG="🔄 $(echo $TARGET | tr '[:lower:]' '[:upper:]') 시작"
fi
SUCCESS_TIMESTAMP=$(TZ=Asia/Seoul date '+%Y-%m-%d %H:%M:%S')
send_discord "✅ **배포 완료**\\n━━━━━━━━━━━━━━━\\n$SWITCH_MSG\\n⏰ $SUCCESS_TIMESTAMP"

echo "===== Blue-Green 배포 완료! ====="
echo "활성: $TARGET"

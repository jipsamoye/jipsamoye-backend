# Phase 0: 공통 설정

**Goal:** WebSocket + STOMP 인프라 구축, OSIV 비활성화

**Spec:** `docs/superpowers/specs/2026-04-14-social-features-backend-design.md`

---

### Task 0-1: OSIV 비활성화

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: application.yaml에 OSIV 설정 추가**

```yaml
spring:
  jpa:
    open-in-view: false
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/application.yaml
git commit -m "chore: OSIV 비활성화 (open-in-view: false)"
```

---

### Task 0-2: WebSocket + STOMP 의존성 추가

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: build.gradle에 websocket 의존성 추가**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-websocket'
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add build.gradle
git commit -m "chore: WebSocket 의존성 추가"
```

---

### Task 0-3: WebSocketConfig 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/global/config/WebSocketConfig.java`

- [ ] **Step 1: WebSocketConfig 작성**

```java
package com.jipsamoye.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub");
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/config/WebSocketConfig.java
git commit -m "feat: WebSocket + STOMP 설정 추가"
```

---

### Task 0-4: AsyncConfig 생성

**Files:**
- Create: `src/main/java/com/jipsamoye/backend/global/config/AsyncConfig.java`

- [ ] **Step 1: AsyncConfig 작성**

```java
package com.jipsamoye.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 2: 빌드 확인**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add src/main/java/com/jipsamoye/backend/global/config/AsyncConfig.java
git commit -m "feat: @Async 설정 추가"
```

---

### Task 0-5: Phase 0 배포

- [ ] **Step 1: feature → develop → main PR/머지**
- [ ] **Step 2: feature 브랜치 삭제, develop 최신 동기화**

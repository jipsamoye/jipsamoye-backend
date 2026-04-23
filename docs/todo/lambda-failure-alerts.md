# Lambda 장애 알림

## 배경

현재 `jipsamoye-image-resize` Lambda는 CloudWatch Logs 14일 보존만 설정되어 있고, **실패 시 실시간 알림 체계가 없다**. S3 이벤트 재시도 2회 실패 후에도 조용히 끝남 → 프론트가 onError 폴백으로 겉으로는 정상 동작처럼 보이지만 썸네일이 누락된 상태가 쌓일 수 있음.

## 목표

EC2 Spring Boot가 이미 Discord 웹훅으로 에러 알림을 쏘는 체계가 있음 (`docs/DEPLOYMENT.md` 참고). Lambda도 **같은 Discord 채널**로 실패 알림 연동.

## 구현 방향

### CloudWatch Alarm + SNS + Discord Webhook

```
[Lambda 실패] → CloudWatch Metric (Errors)
            → CloudWatch Alarm (임계값 초과)
            → SNS 토픽
            → Lambda (SNS → Discord 포맷 변환 후 전송)
            → Discord 채널
```

### SAM 템플릿 확장

`/Users/jys/jipsamoye.image-lambda/template.yaml`에 `AWS::CloudWatch::Alarm` 리소스 추가:

```yaml
ImageResizeErrorAlarm:
  Type: AWS::CloudWatch::Alarm
  Properties:
    AlarmName: jipsamoye-image-resize-errors
    MetricName: Errors
    Namespace: AWS/Lambda
    Dimensions:
      - Name: FunctionName
        Value: !Ref ImageResizeFunction
    Statistic: Sum
    Period: 300
    EvaluationPeriods: 1
    Threshold: 1
    ComparisonOperator: GreaterThanOrEqualToThreshold
    AlarmActions:
      - !Ref ErrorNotificationTopic
```

SNS → Discord 어댑터는 별도 Lambda로 구현 가능 (기존 EC2 백엔드의 Discord 웹훅 URL 재사용).

## 예상 작업

- SAM 템플릿 수정 + 재배포: 30분
- SNS → Discord 어댑터 Lambda: 30~60분 (또는 EventBridge 직접 연동으로 대체 가능)
- 의도적 에러 유발 테스트 (잘못된 이미지 업로드): 10분

**총 1~2시간**

## 우선순위 근거

- 현재 DAU 0~수십 수준이라 실패 건수 거의 없음 → 급하지 않음
- 하지만 운영 시작 후 **조용한 실패를 놓치면** 사용자가 "이 사진만 자꾸 썸네일이 이상해" 라고 버그 리포트할 때까지 모름
- 실제 유저 유입 시작 직전에 반드시 붙일 것

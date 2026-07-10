# benchmark · 07. 에러코드 카탈로그

> REST 표면이 작은 모듈 — 파이프라인(수집·매칭·병합) 오류는 API 에러가 아니라 **로그 + reviewQueue + 관리자 알림**으로 처리한다(절대 원칙 3: 놓침 방지 우선).

| code | 의미 | 상황 |
|---|---|---|
| BM_VARIANT_NOT_FOUND | 대상 없음 | 기준가 조회 시 variant 부재. **알림 정책(REG-03) 조회·저장의 variant 부재에도 같은 코드** |
| BM_INVALID_PERIOD | 기간 파라미터 비정상 | periodMonths ≤ 0 또는 비숫자. **알림 정책의 기간 P에도 같은 코드** — 같은 값이므로 코드를 늘리지 않는다 |
| REG_INVALID_ALERT_POLICY | 알림 정책 입력 비정상 | targetPrice ≤ 0(0은 "미설정"이 아니라 "공짜여야 알림") / quiet hours가 0~23 밖이거나 한쪽만 설정 / periodMonths 누락 |

- 에러 응답 형식 = `{ "code": "...", "message": "..." }` (Q-2 해소, `ApiExceptionHandler` `@RestControllerAdvice`).
- `REG_INVALID_ALERT_POLICY`는 **별도 advice**(`AlertPolicyExceptionHandler`)가 매핑한다 — 응답 모양은 동일. 소유권 때문에 갈라져 있을 뿐이며 합쳐야 한다(docs/91 Q-48).

## FE(web) 처리 — 확정 (2026-07-09, M1 web 최소 슬라이스)

**code별 분기를 하지 않는다.** 이유:

1. `ApiExceptionHandler`가 매핑하는 예외는 **둘뿐**이고(`VariantNotFoundException`·`InvalidBenchmarkPeriodException`), 둘 다 **조회 경로 전용**이다. 등록 화면에서는 발생할 수 없다.
2. **`{code, message}`가 아닌 실패가 오히려 흔하다.** `POST /api/v1/products`는 서버측 검증이 없어(`@Valid` 없음, `RegisterProductCommand`에 컴팩트 생성자 검증 없음) 잘못된 입력이 DB 제약 위반 → **500**으로 나온다. JSON 바인딩 오류도 Spring 기본 400 형태다. 이들에겐 `code`가 없다.

그래서 클라이언트(`web/src/api/client.ts`)는 이렇게 한다:

- 응답 바디가 `{code, message}`면 그 **`code`를 그대로 보존**해 `ApiFailure`로 던진다.
- 파싱이 안 되면 **`HTTP_{status}`**를 code로 삼는다. 삼키지 않는다.
- 화면은 code를 사용자에게 그대로 노출한다(`등록 실패 (BM_INVALID_PERIOD)`). 번역하지 않는다 — 1인용이고, 지어낸 친절한 문구보다 원인 추적이 낫다.

**입력 검증은 클라이언트가 먼저 한다**(`buildCommand`). 서버로 보내기 전에 그 자리에서 거절하므로, 사용자가 보는 실패의 대부분은 애초에 code가 없는 폼 검증이다.

> ⚠️ 등록 API의 서버측 검증 부재는 `docs/91` Q-49. 클라이언트 검증은 방어가 아니라 편의다 — curl로 직접 치면 500이 난다.

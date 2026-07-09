---
paths:
  - "web/**/*.ts"
  - "web/**/*.tsx"
  - "web/nginx.conf"
  - "web/Dockerfile"
---

# web (React · Vite · TypeScript) 규율

> 보편 규칙은 CLAUDE.md `## 축적된 규칙`. 여기엔 web 한정만 둔다.
> `web/` 파일을 열 때만 로드된다 — core·collector 작업 시엔 비용 0.

## 계약

- **`src/api/types.ts`는 core 컨트롤러 원문에서 옮긴 것**이다. 추측해서 필드를 만들지 않는다. 계약이 의심되면 `core/src/main/java/.../adapter/web/*.java`와 도메인 record를 직접 읽는다.
- **통계 필드의 `null`은 버그가 아니라 도메인 계약**이다 — 표본이 빈약하면 통계 용어를 쓰지 않는다(절대 원칙 1). 타입이 `null`을 감추지 않게 한다(`strict`, `noUncheckedIndexedAccess`).
- **에러는 삼키지 않는다.** core가 `{code, message}`를 주면 `code`를 보존하고, 아니면 `HTTP_{status}`로 살린다. 매핑된 code는 조회 경로 전용 2종뿐이고 등록 경로 실패엔 code가 없다(`docs/91` Q-49 — 서버측 검증 부재).
- **클라이언트 검증은 방어가 아니라 편의다.** `buildCommand`가 막아도 curl로 직접 치면 통과한다.

## 프록시·인프라

- **CORS를 쓰지 않는다.** 개발은 Vite 프록시, 운영은 nginx가 `/api`를 core로 넘긴다 → 브라우저에겐 동일 오리진. **web 때문에 core 설정을 바꾸지 않는다.**
- **nginx `proxy_pass`의 업스트림은 변수 + `resolver`로 둔다.** 리터럴 호스트를 쓰면 nginx가 기동 시점에 DNS를 해석하다 실패해 **정적 페이지조차 못 뜬다**. 변수를 쓰면 요청 시점에 해석하고 대상이 없으면 502를 준다. (99: 2026-07-09)
- **`depends_on`은 "떴다"만 보장한다.** 없앨 수 있으면 없앤다 — web은 core 없이도 떠야 한다.
- 이미지는 **실제로 띄워서** 확인한다. `docker build` 성공은 기동 성공이 아니다. `bash scripts/smoke.sh`.

## 범위

- 조회·비교·승격 큐 화면은 **후순위**(확정본 §7, M4 웹 마감). 최소 슬라이스는 등록 + 목록.
- 네이버 후보 검색(REG-01 주 경로)은 키 미발급(Q-3) → 수동 폴백. **그 이유를 화면에 밝힌다**(과대약속 금지).
- 알림 정책 설정(REG-03)은 쓰기 REST 부재(Q-48).

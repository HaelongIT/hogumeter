# web — 최소 슬라이스 (React · Vite · TypeScript)

확정본 §7이 **"제품 등록 + 후보 선택 + variant/키워드/목표가 설정 화면은 1차 검증 전에 선개발"** 로 못박은 그 슬라이스다. 조회·비교·승격 큐 화면은 후순위(M4 웹 마감).

```bash
npm install
npm run dev      # http://localhost:5173 — /api 는 :8080(core)로 프록시
npm test         # Vitest
npm run build    # 타입체크(tsc --noEmit) + 프로덕션 빌드
```

## 배포

```bash
docker compose up -d   # http://127.0.0.1:3000
```

nginx가 정적 자산을 서빙하고 `/api`를 `core:8080`으로 프록시한다.

## 지금 되는 것

- **제품 등록**(REG-02): 가격축 **조합**대로 variant 생성(용량 2 × 색상 2 → 4개). 등록 전에 "생성될 variant N개"를 미리 보여준다. 별칭·수요축 모드(GROUPED/SPLIT).
- **등록된 제품 목록**: variant와 함께 **`variantId`를 노출**한다 — 기준가·신호·주기·구매 조회가 전부 이걸 요구하는데, 등록 응답은 `productId`만 준다.

## 아직 안 되는 것 (정직하게)

- **네이버 후보 검색**(REG-01 주 경로): API 키 미발급(`docs/91` Q-3). 지금은 **수동 입력만** — REG-01이 인정한 폴백이다. 화면에도 그 이유를 밝힌다.
- **알림 정책 설정**(REG-03): `alert_policy` 테이블·엔티티는 있으나 **REST가 없다**. 목표가·기간 P·K_display·제외 키워드·quiet hours 화면은 쓰기 API가 생긴 뒤 — `docs/91` Q-48.
- **접근 통제**(SEC-02): 없다. compose는 `127.0.0.1`로만 개방한다. 공개망에 내놓으려면 Basic Auth + HTTPS가 먼저다 — `pre-deploy-checklist` §C.
- **백필 표기**(REG-04): 백필 자체가 미구현.

## 설계 메모

- **CORS가 필요 없다.** 운영에선 nginx가, 개발에선 Vite 프록시가 `/api`를 core로 넘긴다. 브라우저에겐 동일 오리진이라 교차 출처 요청이 발생하지 않는다 — core에 CORS 설정을 추가할 이유가 없고, 개발과 운영의 동작이 같다.
- **nginx는 core 없이도 뜬다.** `proxy_pass`에 리터럴 호스트를 쓰면 기동 시점에 DNS를 해석하다 실패해 web 전체가 죽는다(정적 페이지조차). 업스트림을 변수로 두고 `resolver`를 지정해 **요청 시점**에 해석한다 — core가 없으면 `/api`만 502다.
- `src/api/types.ts`는 **core 컨트롤러를 직접 읽고 옮긴 계약**이다. 추측하지 않았다.
- 통계 필드가 `null`인 것은 버그가 아니라 **도메인 계약**이다 — 표본이 빈약하면 통계 용어를 쓰지 않는다(절대 원칙 1). 타입이 null을 감추지 않는다.
- `{code, message}`가 아닌 실패(바인딩 오류·500)도 삼키지 않고 `HTTP_{status}`로 살린다.

# benchmark · 07. 에러코드 카탈로그

> REST 표면이 작은 모듈 — 파이프라인(수집·매칭·병합) 오류는 API 에러가 아니라 **로그 + reviewQueue + 관리자 알림**으로 처리한다(절대 원칙 3: 놓침 방지 우선).

| code | 의미 | 상황 |
|---|---|---|
| BM_VARIANT_NOT_FOUND | 대상 없음 | 기준가 조회 시 variant 부재 |
| BM_INVALID_PERIOD | 기간 파라미터 비정상 | periodMonths ≤ 0 또는 비숫자 |

- 에러 응답 형식은 전역 API 컨벤션 확정(`docs/91-open-questions.md` Q-2) 전까지 **제안(미적용)**: `{ "code": "...", "message": "..." }`.
- FE(web) 처리: 아직 미구현 — M1 web 최소 슬라이스에서 code별 분기 여부를 실제 동작과 함께 이 절에 확정 기재한다.

# 91. 기술 보류 보드 (Open Questions)

> **잠정값으로 진행 가능한 기술 보류·한계·미결**의 단일 보드. 각 항목은 **잠정값 + 재개 트리거**(무엇이 충족되면 다시 처리)를 반드시 갖는다.
> 사람이 정해야 할 기획·정책 결정은 여기가 아니라 `working-area/decisions-needed.md`. 해소되면 `working-area/decision-log.md`로 옮기고 여기서 제거.
> 매 세션 시작 시 읽는다.

## 항목 양식

```
## [열림] Q-N. <제목>
- 맥락: 어디서 왜 생겼나 (관련 요구 ID)
- 잠정값: 지금 무엇으로 진행 중인가 (seam 위치 — 상수/인터페이스/설정)
- 재개 트리거: 무엇이 충족되면 처리하나
```

---

## [열림] Q-1. 기준가 수치 파라미터 미확정 — 기명 상수로 진행
- **맥락**: 기준가 엔진(BM)의 수치 파라미터(±α 병합 허용폭, 병합 윈도 24 vs 48h, IQR 이상치 배수, 콜드스타트 대박가 임계 30%, reactionScore 정규화, K_display/K_fill)는 M0 산출물 `docs/31-detailed-params.md`에서 확정한다(`docs/30-roadmap.md` M0-2). 모듈 문서(`docs/benchmark/`)와 향후 도메인 코드는 값이 아니라 이름으로 참조한다.
- **잠정값**: 문서·코드 모두 기명 상수(예: `MERGE_PRICE_TOLERANCE`, `MERGE_WINDOW_HOURS`, `OUTLIER_IQR_MULTIPLIER`, `K_DISPLAY`)로만 참조. seam = 도메인 파라미터 객체 1곳.
- **재개 트리거**: M0-2 완료(스파이크 실측 + 운영자 승인) → `docs/31-detailed-params.md` 작성 → 상수값 채움 → 이 항목을 decision-log로 이관.

## [열림] Q-2. 전역 API 컨벤션(응답 봉투·에러 형식) 미확정
- **맥락**: core REST 표면이 아직 작아(기준가 조회 1본) 전역 컨벤션 문서를 만들지 않았다. `docs/benchmark/03`·`07`은 봉투 없는 리소스 직접 반환 + `{code, message}` 에러를 "제안(미적용)"으로 시드해 둔 상태.
- **잠정값**: 리소스 직접 반환, 에러 `{code, message}`. seam = core web adapter의 응답 매핑 1곳(@ControllerAdvice 등가).
- **재개 트리거**: M1 web 최소 슬라이스(등록+설정) 착수 시 엔드포인트가 늘어나는 시점 — 확정 후 benchmark/03·07의 "제안(미적용)" 표기 제거.

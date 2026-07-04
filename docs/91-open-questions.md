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

## [열림] Q-3. 네이버 쇼핑 API 스파이크·연동 보류 (키 미발급)
- **맥락**: M0-4 스파이크 중 "아이폰 17 256" 응답 품질 확인은 네이버 개발자센터 앱 등록(Client ID/Secret)이 필요한데 미발급 상태(사용자 확인). 현재가(BM-06 currentPrice)·기능1 등록 후보 조회의 데이터 소스.
- **잠정값**: 네이버 어댑터는 port 인터페이스만 두고 미구현. 기준가 계산·테스트는 현재가를 주입값으로 대체(도메인은 이미 현재가를 입력으로 받음).
- **재개 트리거**: 키 발급 → 루트 `.env`에 `NAVER_CLIENT_ID`/`NAVER_CLIENT_SECRET` 주입 → 스파이크 실행(응답 품질·정확 매칭 가능성) → 결과 `98-field-notes` → 어댑터 구현.

## [열림] Q-4. used(중고) 스키마는 V2로 이월
- **맥락**: M0-3 Flyway V1은 신품 코어 루프(M1=REG+BM+AL)만 담았다. 중고(기능5)의 UsedSearch/Listing/EAV 메모·축 테이블(`docs/02-domain-model.md`)은 M2 범위라 V1에서 제외.
- **잠정값**: V1에 미포함. used 도메인 코드·테이블은 M2 착수 시 `V2__used.sql`로 추가.
- **재개 트리거**: M2(중고) 착수 → used 모듈 문서 세트(`docs/used/`) 작성 → V2 마이그레이션 TDD.

## [열림] Q-5. 뽐뿌 golden fixture 재채취 필요
- **맥락**: M0-4 스파이크에서 뽐뿌가 커스텀 UA에 정상 리스트 마크업을 주지 않아(`docs/98` 뽐뿌 항목) 현재 `tests/fixtures/ppomppu/list_normal.html`이 골든으로 부적합.
- **잠정값**: 루리웹·펨코·번개장터 fixture로 먼저 파서 TDD 진행. 뽐뿌 파서는 재채취 후.
- **재개 트리거**: M1 collector 파서 착수 시 — 실제 브라우저(개인용·차단 없는 공개 페이지)로 뽐뿌 리스트 재채취 → fixture 교체 → 오픈소스 셀렉터(`revolution_main_table`) 대조.

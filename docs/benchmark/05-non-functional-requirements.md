# benchmark · 05. 비기능 요구

> 전역 `docs/20-non-functional.md` 위에 이 모듈 특유분만.

## 보안 / 플랫폼 잣대
- 수집은 절대 원칙 5를 따른다: 게시판당 **1req/min**, 지수 백오프, robots 존중, **프록시·UA 위장 등 차단 우회 금지**.
- 차단 징후 발견 시 즉시 수집 중단 + `docs/98-field-notes.md` 기록 + 관리자 알림.

## 성능
- 기준가 계산은 조회 시 온디맨드 순수 계산 — 1인용·variant당 표본 수십 건 규모라 캐시·사전계산 불요(자동확장 조회도 인덱스(variant_id, first_seen)로 충분).
- 현재가(네이버)는 1h 캐시(PriceHistory) — 쿼터 보호.

## 배치 / 정리
- collector 폴링 스케줄(게시판당 1req/min)·글 상태 변화 추적. 운영 가동 확인은 `working-area/pre-deploy-checklist.md`.
- DealEvent는 삭제하지 않는다(표본 자산) — 정리 배치 없음. 제품 노화·만료 처리는 이월(docs/90 §10).

## 로깅 / 관측
- 가격 추출 실패 스킵 로그(BM-02 AC-3) — 파서 품질 지표.
- 매칭 CANDIDATE 비율·미상 버킷 크기 — 별칭 사전 성장 지표.
- 수집 실패·차단 징후는 관리자용 텔레그램 채널(OBS-03)로.

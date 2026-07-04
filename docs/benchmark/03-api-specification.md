# benchmark · 03. API 명세

> BM은 대부분 수집·배치 파이프라인이라 REST 표면이 작다. 아래 응답 봉투·에러 형식은 **제안(미적용)** — 전역 API 컨벤션은 M1 web 슬라이스 착수 시 확정(`docs/91-open-questions.md` Q-2).

## 엔드포인트

### BM-06. 기준가 조회
- **`GET /api/v1/variants/{variantId}/benchmark`** — 인가: 없음(1인용 사설망 전제, 공개망 노출 시 최소 인증 이월)
- 쿼리: `periodMonths`(기간 P, 필수 — 제품별 사용자 설정값이 기본), `includeOutliers`(bool, 기본 false — 표시 손잡이, 계산 진실은 불변)
- 응답: **BenchmarkView 계약 그대로**(`docs/02-domain-model.md` §기준가 계산 계약이 정본)

```jsonc
{
  "tier": "SUFFICIENT",            // SUFFICIENT | SPARSE | NONE
  "benchmarkPrice": 890000,        // n >= K_DISPLAY일 때만, 아니면 null (도메인이 정직성 강제)
  "goodDealLine": 850000,          // 교차검증 딜만의 P25, SPARSE/NONE이면 null
  "periodLowest": { "price": 820000, "date": "2026-06-01" },  // 교차검증 딜만의 min
  "latestDeal": { "price": 900000, "date": "2026-07-01", "site": "ppomppu", "sourceUrl": "..." },
  "n": 7,                          // 전체 유효 딜(교차+단일+백필, 이상치 제외) — 판정 단위
  "m": 4,                          // 교차검증 딜 수 — 신뢰 표시 전용 ("n건(교차 m건)")
  "expandedToMonths": 6,           // 자동확장 발동 시만(K_FILL 미달 → 최대 12개월), 아니면 null
  "currentPrice": 990000,          // 네이버 최저가 (PriceHistory, 1h 캐시)
  "gap": { "vsBenchmark": { "won": 100000, "pct": 11.2 }, "vsLowest": { "won": 170000, "pct": 20.7 } },
  "cases": []                      // SPARSE일 때 사례 나열(가격·날짜·출처), SUFFICIENT이면 생략 가능
}
```

- 에러: `BM_VARIANT_NOT_FOUND`(variant 부재) / `BM_INVALID_PERIOD`(periodMonths ≤ 0 등) — `docs/benchmark/07-error-codes.md`
- SPARSE(1~4건) 응답에서 통계 필드(benchmarkPrice·goodDealLine)는 **null** — 표시 계층 재량이 아니라 도메인 계약. "※ 표본 N건, 참고용" 딱지는 표시 계층 책임.

## 인가 매트릭스
| 엔드포인트 | 공개 | 인증 | 비고 |
|---|---|---|---|
| `GET /api/v1/variants/{id}/benchmark` | 사설망 한정 | 없음 | 공개망 노출 시 최소 인증 추가(docs/90 §10 이월) |

승격 큐 처리(확정/기각)·텔레그램 인라인 버튼 콜백은 **기능3(알림) 모듈의 API** — 이 문서 범위 밖.

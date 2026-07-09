"""DB 어댑터 — collector가 유일하게 쓰는 계약 테이블 `raw_deal_post`.

**Flyway는 core 단독 소유**다. 이 패키지는 마이그레이션을 하지 않는다 — 테이블이 없으면 터진다.
순수 로직(파싱·정규화·레코드 변환)은 `parsers`·`pipeline`에 있고 여기엔 IO만 있다.
"""

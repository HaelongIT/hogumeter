# 98. 필드 노트 — 외부 사이트·API 실측 기록

> 외부 사이트 구조·API 응답의 **실측 발견**(파싱 셀렉터, 쿼터 실동작, 차단 징후, 응답 품질)을 사이트별로 기록한다. 다음 세션의 나를 위한 문서다.
> 기록 시점: 스파이크·파서 수정·수집 장애 대응 직후 즉시. 매 세션 시작 시 읽는다.

## 항목 양식

```
### <날짜> <발견 제목>
- 실측 내용 (셀렉터/응답 예시/쿼터 수치)
- 시사점 (파서·폴링 주기·백필에 미치는 영향)
- 관련 fixture/테스트
```

---

> **셀렉터 출처 표기**: 아래 셀렉터는 대부분 오픈소스 `krepe90/user-hotdeal-bot`(src/crawler)의 실제 파싱 코드에서 확보했다(코드 읽기 — 사이트 크롤링 아님). 실 페이지 대조 결과를 각 항목에 병기한다.

## 뽐뿌
### 2026-07-04 리스트 셀렉터(오픈소스) + 실측 불일치 발견
- **리스트 URL**: `https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu`(뽐뿌게시판), `id=ppomppu4`(해외뽐뿌). 인코딩 **EUC-KR(cp949)** — 파싱 시 디코딩 필수.
- **오픈소스 셀렉터**: 테이블 `table#revolution_main_table` → 행 `.baseList.bbs_new1`, 제목 `.baseList-title`, 작성자 `a.baseList-name`, 추천 `.baseList-rec`, 조회 `.baseList-views`, 종료판정 `.baseList-title.end2`, 글번호 `td:nth-child(1)`(숫자만). 글 URL = `view.php?id={board}&no={id}`.
- **⚠️ 실측 불일치**: 저빈도 프로브(커스텀 UA `hogumeter-spike/0.1`)로 받은 83KB 응답에 `revolution_main_table`·`baseList` 계열 셀렉터가 **전무**. title 태그도 비어 옴. 차단 페이지는 아님(정상 200, EUC-KR HTML)이나 **정상 리스트 마크업이 아님** — 커스텀 UA에 다른 마크업을 준 것으로 추정.
- **시사점**: (1) 뽐뿌는 **UA 위장 금지 원칙상 커스텀/기본 UA 그대로** 쓰되, 파서 개발 시 **실제 브라우저로 채취한 fixture**로 검증할 것. (2) 현재 `tests/fixtures/ppomppu/list_normal.html`은 불일치 응답이라 **golden으로 부적합** — M1 파서 착수 전 재채취 필요(→ `docs/91` Q-5).

## 루리웹
### 2026-07-04 리스트 셀렉터 확보(오픈소스 + 실측 일치)
- **리스트 URL**: `https://bbs.ruliweb.com/market/board/1020?view=thumbnail&page=1` (유저 예판 핫딜). RSS: `.../1020/rss`.
- **셀렉터**: 테이블 `table.board_list_table` → 행 `tr.table_body.normal:not(.best, .notice)`, 글번호 `.info_article_id[value]`, 제목래퍼 `.title_wrapper`(카테고리는 title_wrapper의 앞 텍스트노드 `[..]`), 작성자 `.nick a`, 추천 `.recomd > strong`, 조회 `.hit > strong`. 글 URL = `/market/board/1020/read/{id}`.
- **종료 판정**: 제목에 `품절/종료/매진/마감` 포함 여부(휴리스틱).
- **실측 일치**: 229KB 응답에 `board_list_table` 1건, `info_article_id` **28건** 확인. golden fixture로 적합 → `tests/fixtures/ruliweb/list_normal.html`.

## 펨코 (에펨코리아)
### 2026-07-04 리스트 셀렉터 확보(오픈소스 + 실측 일치, 차단 없음)
- **리스트 URL**: `https://www.fmkorea.com/hotdeal`.
- **셀렉터**: 게시판명 `.bd_tl h1 a`, 행 `#content .fm_best_widget ul li`, 제목 `.title a`(href 끝이 글번호), 카테고리 `.category a`, 작성자 `.author`, 추천 `.pc_voted_count .count`(없으면 0), 댓글 `.comment_count`, 종료 `.hotdeal_var8Y` 존재. **핫딜 메타** `.hotdeal_info` 안 span: 쇼핑몰/가격/배송 각각 `a` 텍스트. 글 URL = `/{id}`.
- **가격/배송이 리스트에 구조화되어 있음**(`.hotdeal_info`) — BM-02 정규화 입력으로 바로 쓸 수 있는 유일 사이트.
- **실측**: 85KB 응답에 `fm_best_widget` 1건, `hotdeal_info` **20건**. **Cloudflare 챌린지 징후 0**(이번 프로브 한정 — fmkorea는 시점에 따라 CF가 붙는 것으로 알려짐, 재확인 여지). golden 적합 → `tests/fixtures/fmkorea/list_normal.html`.

### 2026-07-04 파서 구현 완료(BM-01) — golden row0 실측값
- **루리웹** row0: post_id `105373`, 제목 `[롯데온]빙그레 더단백…`, url `…/read/105373…`, 추천(reaction) `3`, 조회 `3686`. 제목 앵커 = `.title_wrapper a`(카테고리 `[음식]`은 앞 텍스트노드로 별개). 가격은 제목에 있어 BM-02로 정규화.
- **펨코** row0: post_id `10041875674`(href `/{id}` 끝), 제목 `더미식 국물요리 350g X 5개 골라담기`, `.hotdeal_info a` = [`지마켓`,`13,800원`,`무료`] → 가격 13,800. `.pc_voted_count .count` 없으면 추천 0. author는 `/ ` 접두 붙음.
- **번개** item0: `pid 417956893`, `name 아이폰15pro 256 S급`, `price "800000"`(문자열), `update_time 1783167297`(epoch초), `num_faved "0"`, `ad/bizseller false`. url = `m.bunjang.co.kr/products/{pid}`.
- **관련 테스트**: `collector/tests/test_parsers.py`(bs4). 상태변화 케이스 fixture는 미확보(docs/91 Q-19).

## 번개장터
### 2026-07-04 비공식 검색 API 확보 (JSON, HTML 파싱 불요)
- **엔드포인트**: `https://api.bunjang.co.kr/api/1/find_v2.json?q={검색어}&order=date&page={n}&n={건수}`. 200 OK, 깔끔한 JSON.
- **응답 구조**: `num_found`(총 매물 수, 예: "아이폰" 61,310), `list[]`(페이지당 n건). item 키: `pid`(매물ID·자연키), `name`, `price`(**문자열**), `product_image`(`w{res}` 치환형), `status`("0"=판매중 등), `update_time`(**epoch초**), `free_shipping`(bool), `num_comment`, `num_faved`, `location`, `used`, `outlink_url`, `bizseller`, `ad`(광고 매물 플래그 — 필터 대상).
- **시사점**: 번개장터는 **HTML 크롤링 불요 — 공식 앱이 쓰는 JSON API**를 저빈도 폴링(기본 10분). 매칭 3계층 필터는 `name` 문자열 기준. `ad`/`bizseller`로 광고·업자 매물 구분 가능. `price` 문자열 → 정수 파싱, `update_time` epoch → timestamptz. golden → `tests/fixtures/bunjang/find_v2_iphone.json`.
- **주의**: 비공식 API. 플랫폼 잣대상 "공식 API 우선"엔 부합하나 약관/차단 리스크는 리스크 장부(docs/90 §12) 성격 — 저빈도·개인용 유지, 차단 시 즉시 중단.

## 네이버 쇼핑 API
### 2026-07-04 스파이크 보류
- Client ID/Secret 미발급으로 "아이폰 17 256" 응답 품질 스파이크 **미실행**(사용자 확인). 키 확보 시 재개 → `docs/91` Q-3.

## 당근 (공유 URL 평가기)
(기록 없음 — 기능5/M2 범위)

## 쿠팡 (크롬 확장)
(기록 없음 — 기능4/M3 범위)

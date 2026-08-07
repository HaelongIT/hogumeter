# D2 — 스크립트·인프라·CI 리뷰

## 요약 (High N / Medium N / Low N / Info N)
High 2 / Medium 1 / Low 1 / Info 0

`scripts/` 39개 게이트·드릴·CI 배선을 전수로 읽었다. 전반적으로 이 저장소의 게이트 철학("이름이 나타난다 vs 실행된다", 양방향 시험, 격리 드릴)은 코드 수준에서 실제로 지켜지고 있고 `.test.sh` 커버리지도 두텁다. 다만 **"주석은 배선이 아니다" 규율이 게이트마다 고르게 적용되지 않았다** — 그 규율을 처음 발견한 `check-dead-columns.sh`류는 정확히 지키는데, 나중에 파생된 `check-table-wiring.sh`/`check-repository-readers.sh`/`check-domain-consumers.sh`의 `open_question()`과 `check-network-optin.sh`/`check-ci-coverage.sh`의 일부 판정 함수는 같은 규율을 놓쳐 자기 자신이 경계하는 바로 그 함정(이름만 있고 실행은 없음)에 걸린다. `smoke.sh`는 1,222줄 전체를 훑었고 상태코드·JSON 마커 기반 판정이 일관되며 결함은 사소한 자원 정리 한 건뿐이다.

### D2-01 — `check-table-wiring`·`check-repository-readers`·`check-domain-consumers`의 `open_question()`이 [해소]된 Q를 열린 것으로 오판한다 · High
- **위치**: `scripts/check-table-wiring.sh:83-86`, `scripts/check-repository-readers.sh:47-50`, `scripts/check-domain-consumers.sh:54-57` (세 파일이 사실상 동일한 함수를 복제)
  ```sh
  open_question() { # 인용한 Q가 docs/91에 **열려 있는가**. 해소된 Q는 `## ` 헤더가 아니라 각주로 남는다.
      [ -f "$board" ] || return 1
      grep -qE "^## .*${1}\." "$board"
  }
  ```
- **근거**: 주석은 "해소된 Q는 `## ` 헤더가 아니라 각주로 남는다"고 전제하지만, 실제 `docs/91-open-questions.md`의 현재 규약은 그렇지 않다 — 해소된 항목도 여전히 `## ` 헤더에 남는다:
  ```
  60:## [해소 2026-07-25] Q-10. 콜드스타트 잭팟은 BenchmarkView 필드 아님 (독립 술어)
  68:## [해소 2026-07-23] Q-11. includeOutliers 토글은 계산 진실 밖
  ```
  `grep -qE "^## .*Q-10\."`는 상태 표식(`[열림]`/`[부분해소]`/`[해소 …]`)을 전혀 구분하지 않고 "`## `로 시작하고 어딘가에 `Q-10.`이 있다"만 본다 — 그래서 `[해소 2026-07-25] Q-10.` 줄도 그대로 매치되어 "열려 있다"고 오판한다.
  같은 문제를 이미 겪고 정확히 고친 자매 게이트가 저장소에 있다. `scripts/check-dead-columns.sh:92-99`의 `q_open()`은 상태 표식을 명시적으로 요구한다:
  ```sh
  q_open() { # 인용한 Q가 docs/91에 열려 있는가(해소된 Q를 인용한 면제는 만료)
      ...
      grep -qE "^#+ \[(열림|부분해소)[^]]*\] ${qid}\b" "$board"
  }
  ```
  이 정확한 형태가 존재하는데도 세 게이트는 더 느슨한 이전 버전을 그대로 쓰고 있다.
- **실패 시나리오**: `scripts/table-wiring-allowlist.txt:10`이 지금 `price_history Q-3 …`로 면제를 선언하고 있다(Q-3는 현재 `[열림]`). 나중에 Q-3가 해소되어 `## [해소 2026-08-xx] Q-3. …`로 바뀌어도, `open_question("Q-3")`는 여전히 참을 반환한다 — 게이트의 존재 이유("Q가 닫히면 면제도 죽고 게이트가 다시 묻는다")가 정확히 무력화된다. `price_history`가 그새 배선됐는지, 아니면 여전히 죽은 테이블(=아무도 안 읽는 기준가 소스)로 방치됐는지를 CI가 영원히 묻지 않는다. `check-repository-readers.sh`·`check-domain-consumers.sh`도 각자의 allowlist가 비어 있어 지금 당장은 발현하지 않지만, 다음에 그 allowlist에 항목이 추가되는 순간 같은 구멍이 열린다.
- **이미 막는 장치 확인**: 세 게이트의 `.test.sh`(`check-table-wiring.test.sh:78-82`, `check-repository-readers.test.sh:95-96`, `check-domain-consumers.test.sh:99-100`)는 "만료된 면제"를 시험하지만, 전부 **Q가 보드에 아예 없는 경우**(`Q-77 없는 Q를 인용했다`)만 쓴다. "Q가 보드에 있지만 `[해소]` 상태로 있는 경우"는 세 테스트 어디에도 없다 — 반면 `check-dead-columns.test.sh:104-108`은 정확히 이 케이스(`Q-73 해소된 Q를 인용` → 차단돼야 함)를 시험해서 자신의 정확한 구현을 검증하고 있다. shellcheck는 이런 정규식 의미 오류를 못 잡는다(문법은 유효하다).
- **권고**: 세 게이트의 `open_question()`을 `check-dead-columns.sh`의 `q_open()`과 동일한 정규식(`^#+ \[(열림|부분해소)[^]]*\] ${1}\b`)으로 교체하고, 각 `.test.sh`에 "보드에 있지만 `[해소 …]`인 Q를 인용한 면제는 차단돼야 한다"는 케이스를 추가한다. 네 게이트가 같은 함수를 복제해 쓰는 만큼, 차제에 `scripts/lib/board.sh` 같은 공용 함수로 뽑아 드리프트 자체를 구조적으로 막는 것도 검토할 만하다(이 저장소가 이미 `lib/aws-cli.sh`로 같은 패턴을 쓰고 있다).

### D2-02 — `check-network-optin.sh`가 주석에만 있는 opt-in 변수 이름을 실제 게이트로 오인한다 · High
- **위치**: `scripts/check-network-optin.sh:40-46`(주석 제거 로직, 올바름) vs `scripts/check-network-optin.sh:64`(주석 미제거, 결함)
  ```sh
  external_urls() { # 주석을 걷고, 네트워크 명령이 있는 줄에서 외부 URL만 뽑는다
      grep -vE '^[[:space:]]*#' "$1" | ...
  }
  ...
  	if grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK' "$target"; then
  		guarded=$((guarded + 1))
  		continue
  	fi
  ```
- **근거**: 이 스크립트 자신의 헤더 주석이 "이름이 나타난다 vs 실행된다"를 정확히 설명하는데(`check-table-wiring.sh`와 같은 규율), 정작 opt-in 게이트 여부를 판정하는 이 줄은 주석을 걷어내지 않은 **원본 파일 전체**에 대고 `grep`한다. 반면 바로 위 `external_urls()`(외부 URL을 찾는 함수)는 `grep -vE '^[[:space:]]*#'`로 주석 줄을 정확히 걷어낸다 — 같은 파일 안에서 판정 대상(외부 URL)에는 주석 제거 규율을 적용하고, 판정 근거(opt-in 여부)에는 적용하지 않는 비대칭이 있다.
- **실패 시나리오**: 어떤 스크립트가 `curl https://api.telegram.org/...`를 실제 opt-in 검사 없이 호출하면서, 어딘가에 `# TODO: ALLOW_REAL_ROBOTS 게이트를 아직 안 걸었다` 같은 주석만 남겨 두면(또는 다른 스크립트를 설명하며 `# collector/COLLECTOR_ALLOW_NETWORK와 달리 이건...`처럼 이름만 언급해도), `grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK' "$target"`가 그 주석 줄에 매치되어 `guarded`로 집계된다. `.claude/hooks/guard.sh`는 `bash scripts/x.sh` 내부를 못 보므로(Q-60, 이 스크립트 자신이 그렇게 적고 있다), 이 게이트가 이 저장소에서 "스크립트 내부의 실 네트워크 호출"을 잡는 **유일한** 방어선이다 — 그 방어선이 주석 하나로 뚫린다.
- **이미 막는 장치 확인**: `scripts/check-network-optin.test.sh`는 "주석 속 외부 URL은 실행이 아니다"(57-59행, `external_urls`를 시험)는 시험하지만, "주석 속 opt-in 변수 이름은 실제 게이트가 아니다"라는 반대 방향은 어디에도 없다 — 즉 이 게이트의 판정 로직 두 갈래(무엇이 위험인지 / 무엇이 안전장치인지) 중 하나만 오차단 방지가 시험됐고, 다른 하나(미차단 방지)는 시험되지 않았다.
- **권고**: 64행의 검사 대상을 `grep -vE '^[[:space:]]*#' "$target" | grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK'`로 바꿔 `external_urls()`와 같은 규율을 적용한다. 계약 테스트에 "curl 호출은 있지만 opt-in 변수는 주석에만 있다 → 차단"케이스를 추가한다.

### D2-03 — `check-ci-coverage.sh`의 1단 닫힘 판정이 호출자 스크립트 안의 주석을 실행으로 센다 · Medium
- **위치**: `scripts/check-ci-coverage.sh:40`(ci.yml 자체는 주석 제거) vs `scripts/check-ci-coverage.sh:54-61`(호출자 스크립트는 주석 미제거)
  ```sh
  ci_runnable=$(grep -vE '^[[:space:]]*#' "$ci")   # ci.yml은 주석을 걷는다
  ...
  called_by_ci_script() {
      local name="$1" caller
      for caller in "${ci_scripts[@]}"; do
          [ -f "$root/$caller" ] || continue
          grep -qF "$name" "$root/$caller" && return 0   # 호출자 스크립트는 주석을 안 걷는다
      done
      return 1
  }
  ```
- **근거**: `ci.yml`에서 직접 호출을 찾을 때는 "주석은 실행이 아니다"를 명시적으로 지켜 `grep -vE '^[[:space:]]*#'`로 걸러낸 뒤 검색한다(스크립트 자신의 주석 40행 위, 38행: "`ci.yml`은 스크립트 이름을 설명문에도 적는다"). 그런데 1단 닫힘(`restore-drill.sh`처럼 ci.yml이 직접 안 부르고 `backup-drill.sh`가 대신 부르는 경우)을 판정하는 `called_by_ci_script()`는 `$caller` 파일(예: `backup-drill.sh`) 전체를 원본 그대로 `grep -qF`한다 — 같은 파일 안에서 두 판정 함수가 "주석은 실행이 아니다"를 다르게 적용한다.
- **실패 시나리오**: 누군가 `backup-drill.sh`의 `bash scripts/restore-drill.sh "$dump"` 실행 줄을 지우거나 주석 처리하면서 `# restore-drill.sh는 여기서 불렸었다` 같은 설명 주석만 남기면(혹은 그 근처에 스크립트 이름을 언급하는 다른 주석이 남으면), `called_by_ci_script("restore-drill.sh")`는 여전히 참을 반환해 `check-ci-coverage.sh`가 "다른 드릴을 통해 커버됨"으로 통과시킨다. 이 게이트의 존재 이유가 정확히 "드릴이 CI에서 실제로 실행되는지"인데, 그 드릴이 실행되지 않게 된 뒤에도 게이트가 초록을 낼 수 있다.
- **이미 막는 장치 확인**: `check-ci-coverage.test.sh:41-44`는 "이름이 주석에만 남았다"는 케이스를 시험하지만, 이건 **ci.yml 자체**에서 이름을 주석 처리하는 경우만이다(`sed`로 ci.yml의 `run:` 줄을 주석화). "ci.yml이 부르는 중간 스크립트(`backup-drill.sh`) 안에서 하위 호출이 주석 처리되는 경우"는 시험되지 않는다 — 직접 호출 경로의 주석 방어는 시험됐지만 1단 닫힘 경로의 동일한 취약점은 시험도, 방어도 없다.
- **권고**: `called_by_ci_script()`의 `grep -qF "$name" "$root/$caller"`도 `grep -vE '^[[:space:]]*#' "$root/$caller" | grep -qF "$name"`로 바꾼다. 계약 테스트에 "backup-drill.sh 안에서 restore-drill.sh 호출 줄만 주석 처리 → 차단"케이스를 추가한다.

### D2-04 — `smoke.sh`의 SEC-02 인증 검증용 임시 컨테이너가 compose 프로젝트 밖에 있어 조기 실패 시 정리되지 않을 수 있다 · Low
- **위치**: `scripts/smoke.sh:1196-1211`
  ```sh
  auth_cid=$(docker run -d -p "127.0.0.1:${AUTH_PORT:-54000}:80" -e WEB_BASIC_AUTH_HTPASSWD="$htpasswd" "$image")
  sleep 2
  auth_url="http://127.0.0.1:${AUTH_PORT:-54000}/"
  code_no_creds=$(curl -s -o /dev/null -w '%{http_code}' "$auth_url")
  ...
  docker rm -f "$auth_cid" >/dev/null
  [ "$code_health" = 200 ] || fail "..."
  ```
- **근거**: 스크립트 전역의 `trap cleanup EXIT`(37-41행)는 `compose -p "$PROJECT" down -v --remove-orphans`만 수행한다 — `hogumeter-smoke` 컴포즈 프로젝트 범위 밖에서 `docker run`으로 직접 띄운 `auth_cid` 컨테이너는 이 정리 대상이 아니다. `docker rm -f "$auth_cid"`가 1211행에서 명시적으로 호출되긴 하지만, 그 사이(1196~1210행)에 실패가 나면 `set -e`가 스크립트를 즉시 종료시켜 그 줄에 도달하지 못한다. `sleep 2`(1197행)는 고정 대기라 web 이미지의 basic-auth 엔트리포인트(`docker-entrypoint.d/40-basic-auth.sh`)가 그 안에 끝난다는 보장이 없고, nginx가 아직 리슨하지 않은 상태에서 curl이 연결을 거부당하면(`curl -s`는 HTTP 오류엔 관대하지만 connection-refused 같은 전송 실패는 0이 아닌 종료코드를 낸다) `set -e`가 즉시 스크립트를 끝낸다.
- **실패 시나리오**: CI 러너에서 이미지 pull/시작이 평소보다 느려 `sleep 2` 안에 nginx가 아직 안 뜨면, 1199행 `curl`이 connection-refused로 비0 종료 → `set -e`로 스크립트 종료 → `docker rm -f "$auth_cid"`(1211행)에 도달하지 못함 → `hogumeter-smoke` 프로젝트 밖의 컨테이너 하나가 고아로 남는다. GitHub Actions 러너는 매 잡마다 새로 프로비저닝되므로 실질 피해는 작지만(러너 자체가 버려진다), 로컬에서 `bash scripts/smoke.sh`를 반복 실행하는 개발 환경에서는 이 컨테이너가 `AUTH_PORT`(기본 54000)를 계속 점유해 다음 실행이 포트 바인딩 실패로 깨질 수 있다.
- **이미 막는 장치 확인**: 없음 — 이 컨테이너를 위한 별도 trap이나 `|| true` 완충이 없다.
- **권고**: `auth_cid` 생성 직후 `trap '[ -n "${auth_cid:-}" ] && docker rm -f "$auth_cid" >/dev/null 2>&1; cleanup' EXIT` 형태로 기존 trap을 감싸거나, 이 블록 전체를 `sleep 2` 대신 `/healthz` 폴링 루프(다른 단계들처럼 `for _ in $(seq N)`)로 바꿔 실패를 `fail()`로 흡수하게 한다.

## 게이트 양방향 시험 점검

| 게이트 | 차단 케이스 시험 | 통과 케이스 시험 | 사각지대 |
|---|---|---|---|
| `check-table-wiring.sh` | O (8케이스, 주석·테스트파일·접두충돌 포함) | O (3케이스) | `open_question()`이 `[해소]` 상태를 못 걸러낸다(D2-01). 테스트도 이 케이스 없음 |
| `check-dead-columns.sh` | O (5케이스, 날짜 붙은 `[해소]`까지) | O (5케이스, 날짜 붙은 `[부분해소]`까지) | 없음 — 이 저장소에서 가장 정교한 버전 |
| `check-repository-readers.sh` | O | O | `open_question()`이 `check-table-wiring.sh`와 동일한 결함 공유(D2-01) |
| `check-domain-consumers.sh` | O | O | 동일(D2-01). record 메서드 카운트 휴리스틱은 별도 검증 안 함(시간 한계) |
| `check-tag-contract.sh` | O (7케이스 + FREE_PRICE 4케이스) | O (2케이스 + 주석 3케이스) | 없음 |
| `check-board-references.sh` | O (4케이스) | O (4케이스, 해소Q·본문전용Q·test.sh면제 포함) | 없음 |
| `check-conditional-props.sh` | O (4케이스, 실사고 재현형태 포함) | O (4케이스, 주석 포함) | 없음 |
| `check-network-optin.sh` | O (4케이스) | O (8케이스, 주석 URL 포함) | opt-in 변수명이 주석에만 있어도 "guarded"로 오판(D2-02). 반대 방향 테스트 없음 |
| `check-source-vocabulary.sh` | O (4케이스) | O (4케이스, 주석 Set.of 포함) | 없음 |
| `check-env-example.sh` | O (3케이스) | O (6케이스, 주석·이스케이프 포함) | 없음 |
| `check-gitignore.sh` | O (6케이스) | O (2케이스) | 없음 |
| `check-ci-coverage.sh` | O (4케이스, ci.yml 주석화 포함) | O (2케이스, 1단 닫힘 포함) | 1단 닫힘 판정은 호출자 스크립트 내부 주석을 실행으로 오판(D2-03). 이 방향 테스트 없음 |
| `check-backup-freshness.sh` | (직접 못 읽음 — 시간 한계로 `.test.sh` 상세 미검토, 헤더의 4단계 판정 로직만 코드로 확인) | — | 시간 한계로 표시 안 함 |
| `preflight.sh` | O (5케이스) | O (5케이스) | 없음 |
| `.githooks/pre-commit` | O (1케이스) | O (3케이스, 예외 범위 좁음까지) | 없음 |
| `.claude/hooks/guard.sh` | O (21케이스, curl/wget 8 + push 13) | O (14케이스, 오차단 회귀 5 포함) | 없음 — 가장 폭넓게 시험된 게이트 |

## 검토했으나 문제없음 (근거)
- **`backup.sh`의 `trap 'rm -f "$out"' ERR` + 파이프라인**: `set -o pipefail`이 걸려 있어(`set -euo pipefail`, 13행) `pg_dump | gzip` 파이프에서 `pg_dump`가 실패해도 파이프라인 전체가 실패로 잡히고 `ERR` 트랩이 부분 파일을 지운다. 의도대로 동작한다.
- **드릴 격리(`restore-drill.sh`·`rollback-drill.sh`·`offsite-drill.sh`·`backup-drill.sh`)**: 전부 전용 컨테이너 이름(`$$` 또는 고정 접미사)·전용 네트워크·`--tmpfs`(볼륨 없음)·전용 포트·전용 `BACKUP_DIR`/`work` 디렉토리를 쓰고 `trap cleanup EXIT`로 정리한다. 운영/개발 compose 프로젝트(`hogumeter`)나 그 볼륨(`pgdata`)에 닿는 경로가 없다.
- **`smoke.sh`의 상태 판정 방식**: 헬스체크·DB 장애 주입·재시작 정책·포트 바인딩·인증 온오프까지 전부 `docker inspect`의 구조화 필드(`.State.Status`, `.HostConfig.RestartPolicy.Name`, `.NetworkSettings.Ports`)나 HTTP 상태코드로 판정한다. 로그 문구 grep이 필요한 곳(파이프라인 틱 카운터 등)에서도 "값 존재 여부"를 개별 검사하지 "문장 전체"를 grep하지 않는다 — `docs/99` 교훈이 코드에 실제로 반영돼 있다.
- **CI `secrets` 잡**: `fetch-depth: 0`으로 히스토리 전체를 checkout하고 gitleaks도 `detect --source=/repo`로 저장소 전체를 본다 — shallow clone으로 무력화되는 구성이 아니다.
- **`lint` 잡의 shellcheck**: `--severity=warning`으로 명시돼 있어 info 레벨 스타일 이슈는 원래 대상이 아니다(리뷰 규율에 따라 그 대역은 올리지 않았다).
- **Dockerfile 3종**: `core`(eclipse-temurin:21-jdk/jre), `web`(node:22-slim, nginx:1.29-alpine), `collector`(python:3.12-slim, uv 0.11.28 고정 다이제스트급 태그) 전부 구체 버전 태그로 고정돼 있고(`:latest` 없음), 멀티스테이지가 빌드 산출물만 최종 스테이지로 복사한다(core: jar만, web: dist만, collector: uv sync 후 소스만).
- **`docker-compose.yml`의 조건부 환경변수 처리**: `TELEGRAM_ENABLED`처럼 조건부 빈을 가르는 값은 전부 비지 않은 기본값(`:-false`)을 쓰고, 그 이유를 주석으로 남기며, `check-conditional-props.sh`가 이 계약을 실제로 강제한다.

## 시간·범위 한계로 못 본 것
- `check-backup-freshness.test.sh`·`check-offsite-freshness.sh`의 상세 로직(gzip 무결성·나이 계산의 경계값)은 헤더만 확인하고 테스트 파일 전문은 안 읽었다.
- `check-domain-consumers.sh`의 record "정준 접근자 밖 메서드" 카운트 정규식(다중행 시그니처를 한 줄로 접는 부분)은 코드만 읽었고 별도 뮤테이션 검증은 하지 않았다 — 이론상 복잡한 제네릭 시그니처에서 오탐 가능성이 있으나 실제 도메인 코드로 재현하지 않았다.
- `.gitleaks.toml`의 AND 예외 범위(스모크 자격증명)는 B3(보안 표면) 담당 영역과 겹쳐 깊이 보지 않았다.
- `scripts/preflight.sh`의 `dev`/`prod` 두 모드 외 조합(예: robots WARN 분기)은 코드로만 확인했고 `.test.sh`가 그 분기를 직접 시험하지는 않는다는 점만 확인, 별도 결함으로는 올리지 않았다(정보 안내용 WARN이라 실패로 이어지지 않음).

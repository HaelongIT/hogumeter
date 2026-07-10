---
paths:
  - "scripts/**/*.sh"
  - ".claude/hooks/**/*.sh"
  - ".githooks/**"
---

# scripts/ · hooks (bash) 함정 — 실측으로 얻은 규칙

> 전문은 `docs/99-lessons.md`. 여기엔 한 줄 규칙만 둔다(중복 금지).
> 이 파일들은 이제 운영을 떠받친다 — 백업·복원·롤백·오프사이트·스모크·robots·가드 훅. CI가 전부 돌린다.

## `set -euo pipefail`과 함께 사는 법

- **실패가 정상인 `grep`에는 `|| true`를 붙인다.** `x=$(cmd | grep foo | tail -1)`에서 grep이 못 찾으면 `pipefail`이 그 1을 파이프라인 상태로 올리고, 대입문이 실패하고, `set -e`가 **아무 메시지도 없이** 종료한다. 재시도 루프가 통째로 침묵 속에 죽는다. (`tail`이 마지막이라 안전해 보이는 건 pipefail이 **없을 때** 얘기다.) (99: 2026-07-10)
- **`cmd && fail "..."`로 부정 단언을 쓰지 않는다.** 매치가 없으면(정상) `&&` 리스트가 1을 반환해 `set -e`를 밟는다. `if cmd; then fail; fi`를 쓴다.
- **`grep -c`는 줄 수를 센다.** 한 줄짜리 JSON 응답에서 항목이 47개여도 `1`이다 — "중복이 접혔다"를 이걸로 단정하면 언제나 통과한다. 등장 횟수는 `grep -o … | wc -l`. (99: 2026-07-10)
- 반대로 `A && B && break`는 A가 실패해도 안전하다 — 최종 `&&` 앞의 실패는 `set -e` 면제다.

## 프로세스·포트

- **백그라운드 서버를 `(cmd) &` + `kill $!`로 다루지 않는다.** 서브셸만 죽고 자식(python 등)은 살아남아 **고정 포트를 문 채 엉뚱한 디렉토리를 서빙한다.** 검사와 같은 프로세스의 **스레드 + 포트 0**으로 띄운다. 프로세스가 없으면 좀비도 없다. (99: 2026-07-10, `check-robots-drill.sh`)
- Windows는 서버가 열고 있는 파일을 `unlink()` 하지 못한다 — 파일을 지우지 말고 **디렉토리를 갈아끼운다**.

## Windows(Git Bash)에서 도는 것

- **줄끝은 `.gitattributes`가 정한다**(`*.sh eol=lf`). CRLF 셰뱅은 리눅스 CI에서 죽는다. shellcheck가 CI에서 잡는다.
- **`MSYS_NO_PATHCONV=1`은 그게 필요한 명령 앞에만 붙인다**(`MSYS_NO_PATHCONV=1 docker run …`). 스크립트 바깥에 걸면 `curl -d @/tmp/x` 같은 무관한 인자까지 망가진다. 스크립트는 자기가 필요한 우회를 스스로 지녀야 호출자가 실수할 수 없다. (99: 2026-07-09)
- `docker run -v "$PWD:/mnt"`는 Git Bash에서 경로가 변환된다 — `$(pwd -W)` + `MSYS_NO_PATHCONV=1`.
- `python`이 PATH에 없다 → `uv run python`.

## 스크립트가 지는 책임

- **네트워크로 나가는 스크립트는 자기 자신에게도 opt-in 게이트를 건다**(`ALLOW_REAL_ROBOTS`·`COLLECTOR_ALLOW_NETWORK`). `.claude/hooks/guard.sh`는 **명령 문자열만** 보므로 `bash scripts/x.sh` 안의 호출을 못 본다(docs/91 Q-60).
- **훅은 도구 이름으로 스코프된다.** `settings.json`의 `matcher`에 없는 도구로 같은 명령을 보내면 훅은 붙지 않는다 — `"Bash"`만 적어 둔 탓에 `PowerShell` 도구가 네트워크 가드와 push deny를 통째로 우회하고 있었다(현재 `"Bash|PowerShell"`). 게이트를 세울 땐 **어떤 도구 표면이 우회하는가를 열거한다.** (99: 2026-07-10)
- **`guard.sh`는 파괴적 `git push`도 막는다** — `--force`·`-f`·`--force-with-lease`·`--delete`·`-d`·`--mirror`·`--prune`·`+refspec`·`:refspec`. 일반 푸시는 통과한다. 판정은 **세그먼트 단위**이고 `push`가 git의 서브커맨드일 때만 본다(그래야 `git commit -m "... --force ..."`나 `grep "git push --force"`가 오차단되지 않는다).
- **드릴은 전용 프로젝트 이름·전용 포트·일회용 컨테이너에서 돈다.** 운영/개발 스택에 `docker compose down -v` 금지.
- **compose 계약을 뒤집어 보려면 `docker-compose.override.yml`(untracked)을 쓴다** — compose가 자동으로 읽으므로 추적 파일을 건드리지 않는다. 리스트(`volumes:`)는 기본 **병합**이라 `!override` 태그로 대체한다. 검증하겠다고 저장소를 고치지 않는다. (99: 2026-07-10)
- **출력 라벨은 ASCII로.** 한글은 콘솔 인코딩에 따라 깨지고, 깨진 로그는 읽히지 않는다. 문구를 `grep`하는 대신 마커(`REFUSED reason=...`)나 JSON을 본다.

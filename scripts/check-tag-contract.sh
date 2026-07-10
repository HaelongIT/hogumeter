#!/usr/bin/env bash
# 모듈 경계를 넘는 문자열 계약 — collector가 쓰고 core가 읽는 **기계용 표식**이 같은가.
#
#   bash scripts/check-tag-contract.sh [root]
#
# 왜 필요한가: `배송비미상` 표식은 collector가 만들어 `raw_deal_post.raw._derived`에 싣고,
# core가 `deal_event.applied_conditions`로 옮겨 센다. 두 모듈이 같은 리터럴을 **각자** 들고 있다.
# collector에서 이름을 바꾸면 core는 조용히 0을 세면서 "배송비 미상 딜 없음"이라고 말한다 —
# 드리프트한 사본은 GREEN인 채로 거짓말한다(CLAUDE.md).
#
# 정본 = collector(`pipeline/price.py`). core(`domain/deal/DealTags.java`)는 사본이다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
python_src="$root/collector/src/collector/pipeline/price.py"
java_src="$root/core/src/main/java/dev/hogumeter/core/domain/deal/DealTags.java"
web_src="$root/web/src/review/present.ts"

for file in "$python_src" "$java_src" "$web_src"; do
	[ -f "$file" ] || {
		echo "FAIL: 계약 파일이 없다: ${file#"$root/"}" >&2
		exit 1
	}
done

# 세 모듈에서 따옴표 안의 값을 뽑는다. 사본이 하나 늘 때마다 여기에 추가한다 —
# **게이트가 모르는 사본은 게이트가 지켜 주지 않는다.**
owner=$(grep -oP '^SHIPPING_UNKNOWN\s*=\s*"\K[^"]+' "$python_src" | head -1 || true)
java_copy=$(grep -oP 'String\s+SHIPPING_UNKNOWN\s*=\s*"\K[^"]+' "$java_src" | head -1 || true)
web_copy=$(grep -oP "SHIPPING_UNKNOWN\s*=\s*'\K[^']+" "$web_src" | head -1 || true)

[ -n "$owner" ] || {
	echo "FAIL: 정본에서 SHIPPING_UNKNOWN 상수를 찾지 못했다: ${python_src#"$root/"}" >&2
	echo "  이름을 바꿨다면 이 게이트도 함께 고쳐야 한다 — 계약은 세 곳에 있다." >&2
	exit 1
}
[ -n "$java_copy" ] || {
	echo "FAIL: core 사본에서 SHIPPING_UNKNOWN 상수를 찾지 못했다: ${java_src#"$root/"}" >&2
	exit 1
}
[ -n "$web_copy" ] || {
	echo "FAIL: web 사본에서 SHIPPING_UNKNOWN 상수를 찾지 못했다: ${web_src#"$root/"}" >&2
	exit 1
}

fail=0
if [ "$owner" != "$java_copy" ]; then
	echo "FAIL: core 표식이 어긋났다. collector가 만든 값을 core가 영원히 못 찾는다(0을 센다)." >&2
	printf '  collector(정본): %s\n  core(사본)     : %s\n' "$owner" "$java_copy" >&2
	fail=1
fi
if [ "$owner" != "$web_copy" ]; then
	echo "FAIL: web 표식이 어긋났다. 화면이 '실제 결제가는 더 높습니다'를 조용히 멈춘다." >&2
	printf '  collector(정본): %s\n  web(사본)      : %s\n' "$owner" "$web_copy" >&2
	fail=1
fi
[ "$fail" -eq 0 ] || exit 1

echo "TAG CONTRACT OK: SHIPPING_UNKNOWN 이 collector·core·web에서 일치한다"

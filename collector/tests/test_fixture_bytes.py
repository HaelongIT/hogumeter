"""golden fixture는 **바이트 그대로**여야 한다 — 그 사실을 해시로 못박는다.

2026-07-09 실측: `.gitattributes`가 없고 `core.autocrlf=true`였던 동안, Windows 워킹트리의
fixture는 blob과 달랐다(bunjang +983 CR, fmkorea +378 CR). **같은 테스트가 Windows와
리눅스 CI에서 다른 바이트를 읽고 있었다.** 파서가 공백에 관대해 드러나지 않았을 뿐이다.

`.gitattributes`의 `collector/tests/fixtures/** -text`가 재발을 막지만, 그 장치가 실제로
동작하는지는 아무도 확인하지 않는다. 이 테스트가 확인한다.

fixture를 **의도적으로** 갱신했다면 해시도 함께 고친다 — 갱신 사실을 커밋에 남기는 것이
이 테스트의 목적이다(`docs/98`에 채취일·경위 기록).
"""

import hashlib
from pathlib import Path

import pytest

FIXTURES = Path(__file__).parent / "fixtures"

# sha256(파일 바이트). 뽐뿌·루리웹 응답엔 홑 CR이 섞여 있고 그것도 우리가 받은 바이트다.
FROZEN = {
    "bunjang/find_v2_iphone.json": "9d579808b8aa1b154ca131d4f2e17c93f763157922735dbb60b776443a10da6e",
    "fmkorea/list_normal.html": "2172315f9003868505f2f90a0b48339eac78a3b4a3b5ff823ee784fdacad4d39",
    "ppomppu/list_normal.html": "66334efcbe112b38e3146deec400f8fe167ece74a5601dd100c4ea89ba8a14f2",
    "ruliweb/list_normal.html": "495d47720d6809ccb7639f402dd2ee594f99f40c87cf24a23ae276db15ee5610",
}


@pytest.mark.parametrize("relative_path", sorted(FROZEN))
def test_golden_bytes_are_frozen(relative_path: str):
    actual = hashlib.sha256((FIXTURES / relative_path).read_bytes()).hexdigest()

    assert actual == FROZEN[relative_path], (
        f"{relative_path}의 바이트가 바뀌었다. 의도한 재채취라면 이 해시를 갱신하고 docs/98에 기록하라. "
        f"의도하지 않았다면 줄끝 변환을 의심하라(.gitattributes의 `-text`)."
    )


def test_every_capture_is_frozen():
    """새 fixture를 추가하고 해시 등록을 잊으면, 그 골든은 아무도 지켜주지 않는다.

    `.gitkeep` 같은 디렉토리 유지용 파일은 캡처 산출물이 아니다.
    """
    captures = {
        str(path.relative_to(FIXTURES)).replace("\\", "/")
        for path in FIXTURES.rglob("*")
        if path.is_file() and not path.name.startswith(".")
    }

    assert captures == set(FROZEN), f"해시 미등록 fixture: {sorted(captures - set(FROZEN))}"

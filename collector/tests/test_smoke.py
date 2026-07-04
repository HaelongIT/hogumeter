"""스캐폴딩 스모크 — 패키지가 import 가능해야 CI가 의미를 갖는다."""

import collector
import collector.parsers
import collector.pipeline
import collector.scheduler


def test_packages_importable():
    assert collector.__doc__

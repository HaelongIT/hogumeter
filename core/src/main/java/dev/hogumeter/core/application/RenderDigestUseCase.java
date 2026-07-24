package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.telegram.DigestFormatter;
import dev.hogumeter.core.application.AssembleDigestUseCase.Digest;
import dev.hogumeter.core.application.AssembleVariantDigestUseCase.VariantDigestRow;
import dev.hogumeter.core.application.VariantNaming.Naming;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * DIG-04 발송 문구 완성 — {@link AssembleDigestUseCase}(재료)에 이름(제품·variant)을 입혀
 * {@link DigestFormatter}(순수)로 넘긴다. 발송(텔레그램 전송)·quiet hours·분할은 다음 증분(docs/91 Q-81).
 *
 * <p>이름 조회는 {@link VariantNaming}과 같은 수법 — AL-05가 알림 본문에 이름을 채우는 자리와 같다.
 * 순수 포맷터는 이름을 모른다(주입받을 뿐), 이 유스케이스만 IO를 진다.
 */
@Service
public class RenderDigestUseCase {

	private final AssembleDigestUseCase assembler;
	private final VariantNaming naming;
	private final DigestFormatter formatter = new DigestFormatter();

	public RenderDigestUseCase(AssembleDigestUseCase assembler, VariantNaming naming) {
		this.assembler = assembler;
		this.naming = naming;
	}

	public String render() {
		Digest digest = assembler.assemble();
		Map<Long, Naming> names = new HashMap<>();
		for (VariantDigestRow row : digest.variantRows()) {
			names.put(row.variantId(), naming.of(row.variantId()));
		}
		return formatter.format(digest, names);
	}
}

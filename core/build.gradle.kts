plugins {
	java
	checkstyle
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.spotbugs") version "6.0.19"
}

group = "dev.hogumeter"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	// 스타터는 flyway-core만 제공 — PostgreSQL 방언 모듈은 별도 (Boot 4 모듈형 스타터)
	implementation("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	// Boot 4: 웹 MVC 슬라이스 테스트(@AutoConfigureMockMvc·@WebMvcTest)는 별도 스타터로 분리
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Q-90③(코드리뷰 20260806): web eslint·collector ruff와 같은 철학 — 실결함 규칙 위주로 점진 도입.
checkstyle {
	toolVersion = "10.18.2"
	configFile = file("config/checkstyle/checkstyle.xml")
	isIgnoreFailures = false
	maxWarnings = 0
}

spotbugs {
	effort.set(com.github.spotbugs.snom.Effort.DEFAULT)
	reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
	excludeFilter.set(file("config/spotbugs/exclude.xml"))
}

tasks.named("spotbugsTest") {
	enabled = false // 테스트 코드는 실결함 신호가 약해 우선 제외 — main만 본다
}

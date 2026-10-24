import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.security.MessageDigest
import java.util.HexFormat
import java.io.File
import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    java
    jacoco
    pmd
    alias(libs.plugins.quarkus)
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs)
    alias(libs.plugins.pitest)
    alias(libs.plugins.cyclonedx)
}

group = "dev.exemplar"
version = providers.gradleProperty("releaseVersion").orElse(providers.gradleProperty("version")).get()

java { toolchain { languageVersion.set(JavaLanguageVersion.of(25)) } }

dependencies {
    implementation(enforcedPlatform(libs.quarkusBom))
    implementation(enforcedPlatform(libs.quarkusAwsBom))
    implementation(libs.quarkusRestJackson)
    implementation(libs.quarkusHealth)
    implementation(libs.quarkusOpenapi)
    implementation(libs.quarkusMetrics)
    implementation(libs.quarkusOtel)
    implementation(libs.quarkusLogging)
    implementation(libs.quarkusS3)
    implementation(libs.quarkusDynamodb)
    implementation(libs.awsUrlConnectionClient)

    testImplementation(libs.quarkusJunit)
    testImplementation(libs.quarkusJunitMockito)
    testImplementation(libs.junitJupiter)
    testImplementation(libs.restAssured)
    testImplementation(libs.assertj)
    testImplementation(libs.archunit)
    testImplementation(libs.testcontainers)
    testRuntimeOnly(libs.junitLauncher)
}

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
    // Optional annotations missing from third-party classfiles are not source-code warnings.
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-classfile", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    maxParallelForks = 1
    systemProperty("user.timezone", "UTC")
    systemProperty("aws.retryMode", "standard")
    systemProperty("aws.maxAttempts", "3")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

tasks.named<io.quarkus.gradle.tasks.QuarkusDev>("quarkusDev") {
    jvmArguments.addAll("-Daws.retryMode=standard", "-Daws.maxAttempts=3")
}

// Keep Quarkus application tests in the test source set so the official plugin
// supplies its application model. Tags separate execution and keep test Docker-free.
tasks.test {
    description = "Runs Docker-free unit and architecture tests."
    useJUnitPlatform { excludeTags("integration") }
    finalizedBy(tasks.jacocoTestReport)
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs Quarkus HTTP tests against local S3 and DynamoDB."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.test)
}

tasks.named<Test>("quarkusIntTest") {
    description = "Tests the packaged JVM distribution against local AWS services."
    shouldRunAfter(integrationTest)
    systemProperty("quarkus.devservices.enabled", "false")
    systemProperty("quarkus.test.arg-line", "-Daws.retryMode=standard -Daws.maxAttempts=3")
}

jacoco { toolVersion = libs.versions.jacoco.get() }

val coreClasses = sourceSets.main.get().output.asFileTree.matching {
    include("dev/exemplar/artifact/domain/**", "dev/exemplar/artifact/application/**")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(coreClasses)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(coreClasses)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("repository") {
        target("*.md", "docs/**/*.md", "*.gradle.kts", "gradle/*.toml", ".github/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

pmd {
    toolVersion = libs.versions.pmd.get()
    isConsoleOutput = true
    ruleSets = emptyList()
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

tasks.withType<Pmd>().configureEach {
    // Analyze maintained sources rather than Quarkus-generated code.
    val maintainedSources = mapOf(
        "pmdMain" to "src/main/java",
        "pmdTest" to "src/test/java",
        "pmdIntegrationTest" to "src/integrationTest/java",
    )
    enabled = name in maintainedSources
    maintainedSources[name]?.let { setSource(fileTree(it) { include("**/*.java") }) }
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotbugs {
    toolVersion.set(libs.versions.spotbugsTool)
    effort.set(Effort.MAX)
    reportLevel.set(Confidence.MEDIUM)
    excludeFilter.set(file("config/spotbugs/exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    enabled = name in setOf("spotbugsMain", "spotbugsTest", "spotbugsIntegrationTest")
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}

pitest {
    pitestVersion.set(libs.versions.pitestTool)
    junit5PluginVersion.set(libs.versions.pitestJunit)
    targetClasses.set(setOf("dev.exemplar.artifact.domain.*", "dev.exemplar.artifact.application.*"))
    targetTests.set(setOf("dev.exemplar.artifact.domain.*Test", "dev.exemplar.artifact.application.*Test"))
    threads.set(2)
    mutationThreshold.set(80)
    coverageThreshold.set(90)
    outputFormats.set(setOf("HTML", "XML"))
    timestampedReports.set(false)
    failWhenNoMutations.set(true)
    jvmArgs.set(listOf("-Duser.timezone=UTC"))
}

val maintenanceTest = tasks.register<Exec>("maintenanceTest") {
    description = "Tests release guards and distribution validation without network access."
    group = "verification"
    commandLine("python3", "-m", "unittest", "discover", "-s", "scripts/tests", "-v")
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification, maintenanceTest) }

tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs = listOf("runtimeClasspath")
}

tasks.register("verifyAll") {
    description = "Runs all checks, AWS integration tests, packaged tests, and mutation testing."
    group = "verification"
    dependsOn(tasks.check, integrationTest, "quarkusIntTest", "pitest")
}

tasks.register<Exec>("doctor") {
    description = "Reports local prerequisites without changing configuration or contacting AWS."
    group = "help"
    commandLine("bash", "scripts/doctor.sh")
}

tasks.register("resolveAllDependencies") {
    description = "Resolves dependency graphs when refreshing reviewed locks and checksums."
    group = "build setup"
    notCompatibleWithConfigurationCache("Explicit dependency maintenance operation")
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}

tasks.register("resolveDependencySources") {
    description = "Resolves published dependency sources so IDE imports have recorded checksums."
    group = "build setup"
    dependsOn("resolveAllDependencies")
    mustRunAfter("quarkusGoOffline")
    notCompatibleWithConfigurationCache("Explicit dependency maintenance operation")
    doLast {
        // IDEs fetch sources for Gradle's bundled Kotlin libraries outside the app dependency graph.
        // A classifier without @jar retains POM/module verification. The wrapper fixes the version.
        val embeddedSources = configurations.detachedConfiguration(
            *listOf("kotlin-stdlib", "kotlin-reflect", "kotlin-script-runtime").map { module ->
                dependencies.create("org.jetbrains.kotlin:$module:$embeddedKotlinVersion:sources")
            }.toTypedArray()
        )
        embeddedSources.isTransitive = false
        embeddedSources.resolve()
        val sources = mutableSetOf<File>()
        val dependencyGraphs = configurations.toList() + buildscript.configurations.toList()
        dependencyGraphs.filter { it.isCanBeResolved }.forEach { configuration ->
            val artifacts = configuration.incoming.artifactView {
                withVariantReselection()
                // Some modules publish no source variant. Binary resolution remains strict.
                isLenient = true
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                    attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
                    attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
                }
            }.artifacts
            sources.addAll(artifacts.artifactFiles.files)
            artifacts.failures.forEach { failure ->
                logger.info("Sources unavailable in ${configuration.name}: ${failure.message}")
            }
        }
        logger.lifecycle("Resolved ${sources.size} dependency source archives for verification.")
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val releaseDist = tasks.register<Zip>("releaseDist") {
    description = "Packages the entire Quarkus fast-jar distribution, SBOM, and operating notes."
    group = "distribution"
    dependsOn("quarkusBuild", "cyclonedxBom")
    archiveFileName.set("${project.name}-${project.version}.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    into("${project.name}-${project.version}") {
        from(layout.buildDirectory.dir("quarkus-app")) { into("app") }
        from(layout.buildDirectory.dir("reports/cyclonedx")) { into("sbom") }
        from("src/dist/README.md", "SECURITY.md")
        from(files("docs/configuration.md", "docs/operations.md")) { into("docs") }
        from("scripts/run.sh")
    }
}

tasks.register("releaseChecksums") {
    description = "Writes SHA-256 checksums for the release distribution and SBOM."
    group = "distribution"
    dependsOn(releaseDist)
    val destination = layout.buildDirectory.dir("distributions")
    outputs.file(destination.map { it.file("SHA256SUMS") })
    outputs.file(destination.map { it.file("${project.name}-${project.version}.cdx.json") })
    inputs.file(releaseDist.flatMap { it.archiveFile })
    inputs.file(layout.buildDirectory.file("reports/cyclonedx/bom.json"))
    doLast {
        val directory = destination.get().asFile
        val archive = releaseDist.get().archiveFile.get().asFile
        val sbom = layout.buildDirectory.file("reports/cyclonedx/bom.json").get().asFile
            .copyTo(directory.resolve("${project.name}-${project.version}.cdx.json"), overwrite = true)
        val checksums = listOf(archive, sbom).joinToString("\n", postfix = "\n") { artifact ->
            val digest = MessageDigest.getInstance("SHA-256")
            val checksum = artifact.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var count = input.read(buffer)
                while (count >= 0) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
                HexFormat.of().formatHex(digest.digest())
            }
            "$checksum  ${artifact.name}"
        }
        directory.resolve("SHA256SUMS").writeText(checksums)
    }
}

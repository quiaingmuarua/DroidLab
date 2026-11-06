package dev.dexlineage.cli

import dev.dexlineage.analysis.*
import dev.dexlineage.migration.MigrationService
import dev.dexlineage.migration.S1Importer
import dev.dexlineage.migration.V02Importer
import dev.dexlineage.migration.V03Importer
import dev.dexlineage.rules.MatchingHandler

/** Ordinary immutable composition for a project-owned main; the public CLI installs only built-ins. */
class DexLineageRuntime(handlers: List<MatchingHandler> = emptyList(),
    methods: List<AnalysisMethod> = emptyList(), codecs: List<AnalysisCodecContribution> = emptyList()) {
    val analysisRuntime = AnalysisRuntime(handlers, methods, codecs)
    val engine = analysisRuntime.engine
    val json = analysisRuntime.json
    val analysis = AnalysisService(analysisRuntime)
    val migration = MigrationService(engine, json)
    val oneShot = OneShotAnalysis(analysis)
    val research = ResearchService(engine, json)
    val analysisPackets = ReviewPackets(analysis)
    val migrationPackets = dev.dexlineage.migration.ReviewPackets(migration)
    val s1Importer = S1Importer(engine, json)
    val v02Importer = V02Importer(engine, json)
    val v03Importer = V03Importer(engine, json)
}

package dev.dexlineage.rules.facts

import dev.dexlineage.io.*
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*

/** Exact declarations in the immutable APK, never an inherited or display-name substitute. */
object OriginalEntities {
    fun requirePresent(facts: ApkFacts, entity: EntityRef): Int {
        RuleProofs.validateEntity(entity)
        requireInput(entity.owner.apkSha256 == facts.artifact.sha256 &&
            facts.classes[entity.owner.descriptor]?.ref == entity.owner, "Entity class/DEX identity absent from APK: ${entity.identity}")
        return when (entity.kind) {
            EntityKind.CLASS -> facts.classes.getValue(entity.owner.descriptor).flags
            EntityKind.METHOD -> facts.method(entity.method!!)?.flags
                ?: throw ValidationException("Original method absent from APK: ${entity.identity}")
            EntityKind.FIELD -> {
                val field = entity.field!!
                facts.fields(entity.owner.descriptor).singleOrNull { it.name == field.name && it.type == field.type }?.flags
                    ?: throw ValidationException("Original field absent from APK: ${entity.identity}")
            }
        }
    }

    fun validate(artifact: Artifact, entities: Collection<EntityRef>) {
        if (entities.isEmpty()) return
        ApkFacts.open(artifact).use { facts -> entities.distinct().forEach { requirePresent(facts, it) } }
        Digests.verify(artifact)
    }

    internal fun record(facts: ApkFacts, matches: List<MatchFact>): List<MatchFact> = matches.map { match ->
        val flags = requirePresent(facts, match.entity)
        requireInput(match.accessFlags == null || match.accessFlags == flags, "Matcher access flags differ from APK")
        match.copy(accessFlags = flags)
    }
}

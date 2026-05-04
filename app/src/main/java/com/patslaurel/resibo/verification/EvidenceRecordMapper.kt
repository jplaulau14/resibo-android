package com.patslaurel.resibo.verification

import com.patslaurel.resibo.data.entity.EvidenceRecordEntity

fun EvidenceRecord.toEntity(): EvidenceRecordEntity =
    EvidenceRecordEntity(
        id = id,
        sourceName = sourceName,
        sourceType = sourceType.name,
        url = url,
        canonicalUrl = canonicalUrl,
        title = title,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
        trustTier = trustTier.name,
        stance = stance.name,
        snippet = snippet,
        fullText = fullText,
        contentHash = contentHash,
    )

fun EvidenceRecordEntity.toEvidenceRecord(): EvidenceRecord =
    EvidenceRecord(
        id = id,
        sourceName = sourceName,
        sourceType = enumValueOf(sourceType),
        url = url,
        canonicalUrl = canonicalUrl,
        title = title,
        publishedAt = publishedAt,
        fetchedAt = fetchedAt,
        trustTier = enumValueOf(trustTier),
        stance = enumValueOf(stance),
        snippet = snippet,
        fullText = fullText,
        contentHash = contentHash,
    )

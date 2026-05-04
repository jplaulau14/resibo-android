# Verification Agent Evaluation Rubric

Score each case from 0 to 2 for each metric.

## Claim Extraction

- 2: Extracts the central factual claim accurately.
- 1: Extracts a related claim but misses important details.
- 0: Extracts the wrong claim or no claim.

## Retrieval Hit Rate

- 2: Retrieves at least one source that directly addresses the claim.
- 1: Retrieves background sources but not a direct match.
- 0: Retrieves no useful source.

## Source Trust

- 2: Uses the required source type for the category.
- 1: Uses a reputable source but not the preferred source type.
- 0: Uses weak, irrelevant, or discovery-only sources as authority.

## Freshness Compliance

- 2: Meets freshness requirement or abstains correctly.
- 1: Mentions uncertainty but does not clearly evaluate freshness.
- 0: Treats stale evidence as current.

## Abstention Correctness

- 2: Abstains when evidence is missing, stale, or conflicting.
- 1: Gives a cautious answer but still implies more certainty than evidence supports.
- 0: Gives a confident answer without sufficient evidence.

## Citation Grounding

- 2: Every factual claim in the Note is grounded in retrieved evidence.
- 1: Mostly grounded, with minor unsupported background.
- 0: Relies on model memory or unsupported claims.

## Language Preservation

- 2: Responds naturally in the user's language.
- 1: Mixed language is understandable but awkward.
- 0: Responds in the wrong language.

# Resibo Verification Agent Core Design

Date: 2026-05-04

## Purpose

Resibo should win the Gemma 4 Good Hackathon by proving correctness discipline for Filipino public/current claims. The product should not claim that Gemma knows real-time truth. Instead, Gemma reads messy user input, proposes a verification plan, and writes a careful Note from evidence gathered by approved tools.

The hero demo focuses on Philippines public/current claims: transport announcements, government policy claims, disaster advisories, election/government claims, and similar social media posts. Scams and fraud are a secondary demo/evaluation pack.

## North Star

Resibo is an evidence-first verification agent:

1. A user shares a screenshot or text claim from another Android app.
2. Local Gemma extracts the claim, visible text, language, entities, and freshness need.
3. Gemma proposes a verification plan with candidate tools and queries.
4. Kotlin validates the plan against a fixed policy before any tool runs.
5. Approved online and local tools gather evidence.
6. Tool results are normalized into local evidence records.
7. Gemma writes a Note from evidence only, including source freshness and abstention when evidence is weak or stale.

The headline for judging is correctness discipline. Agentic breadth and on-device privacy support that headline.

## Architecture

### Pipeline

1. **Share/input normalizer**
   - Accepts text and image shares.
   - Copies share URIs into app-readable temporary files.
   - Computes perceptual hashes for images when possible.
   - Preserves the original user text and image context for the agent.

2. **Local Gemma extraction**
   - Runs on-device through LiteRT-LM.
   - For image shares, extracts visible text and describes relevant visual context.
   - Produces claim text, language, entities, claim category, and time sensitivity.

3. **Gemma verification planner**
   - Proposes candidate tool calls, queries, source preferences, and freshness requirements.
   - Does not execute tools directly.
   - Does not decide final truth from model memory.

4. **Kotlin policy gate**
   - Validates or edits the proposed plan.
   - Enforces allowed tools, trusted domains, maximum calls, freshness windows, network state, and source requirements by claim category.
   - Converts invalid plans into safe fallback plans or abstention.

5. **Tool execution layer**
   - Executes approved tool calls only.
   - Initial tools:
     - Perplexity discovery.
     - Official-source fetch/search.
     - Local evidence search.
   - Each tool returns normalized records plus execution metadata.

6. **Evidence normalizer and memory**
   - Converts all tool output into `EvidenceRecord`s.
   - Persists records locally so online verification builds a reusable evidence memory.
   - Local evidence search becomes stronger over time and can answer repeated or related claims with less online work.

7. **Local Gemma synthesis**
   - Writes a short Note in the user’s language.
   - Uses normalized evidence records, not raw tool prose, as the grounding source.
   - Includes citations, evidence mode, source freshness, and remaining verification needs.
   - Abstains when evidence is missing, stale, or conflicting.

### Core Rule

Gemma can read, plan, compare, and write. Evidence comes from tools, cache, or user-provided content. Gemma must not declare a factual claim true or false from model memory alone.

## Data Contracts

### VerificationPlan

Produced by Gemma and validated by Kotlin.

Fields:

- `claim`: normalized claim text.
- `language`: user/post language.
- `claim_category`: public policy, disaster/weather, election/government, transport, scam, health, other.
- `time_sensitivity`: evergreen, recent, current, breaking.
- `requires_live_evidence`: boolean.
- `required_freshness_hours`: nullable integer.
- `tool_calls`: ordered candidate tool calls.
- `preferred_sources`: source names or domain classes.
- `abstention_reason_if_no_evidence`: user-facing reason to use if the policy gate cannot find sufficient evidence.

### EvidenceRecord

Canonical record returned by local and online tools.

Fields:

- `id`: local ID.
- `source_name`: publisher or agency.
- `source_type`: official, fact_check, news, aggregator, user_provided, local_cache.
- `url`: original URL when available.
- `canonical_url`: normalized URL for deduplication.
- `title`: source title.
- `published_at`: source publication timestamp when known.
- `fetched_at`: local fetch timestamp.
- `trust_tier`: official, verified_fact_check, reputable_news, discovery_only, user_provided.
- `stance`: supports, refutes, unclear, background.
- `snippet`: concise evidence excerpt or summary.
- `full_text`: optional source text for local retrieval.
- `content_hash`: dedupe hash.

### ToolResult

Execution wrapper for each tool call.

Fields:

- `tool_name`.
- `input`.
- `status`: success, partial, empty, blocked, error.
- `records`: list of `EvidenceRecord`s.
- `raw_summary`: optional short tool summary.
- `latency_ms`.
- `error`: optional error message.

### Evidence Mode

Every final Note includes one evidence mode:

- `live`: live tools produced fresh evidence.
- `last_synced`: cached records met freshness policy.
- `local_only`: local evidence was used, but no live check occurred.
- `user_provided`: only user-provided screenshots/text were available.
- `insufficient`: evidence was missing, stale, or conflicting.

## Tools And Policy

### Perplexity Discovery Tool

Perplexity is allowed as a live discovery tool. Resibo may use its returned text to understand the search landscape, but the final Note should cite original URLs and normalized evidence records rather than Perplexity prose.

Use cases:

- Broad current-events discovery.
- Finding source URLs quickly.
- Generating candidate official/news/fact-check pages for normalization.

### Official Source Tool

Fetches or searches trusted official sources with domain restrictions.

Initial source classes:

- PAGASA for weather and disaster advisories.
- DOTr, MRT-3, LRT for transport claims.
- DOH for health/public safety.
- COMELEC for election claims.
- PSA and BSP for statistics/economic claims.
- Official Gazette and relevant agency/LGU domains for policy claims.

### Local Evidence Tool

Searches cached `EvidenceRecord`s stored on-device. It should run before or alongside live tools. Over time, this becomes the app’s local RAG layer.

Initial retrieval can be SQLite FTS. Vector search can be added after the evidence schema and policy are stable.

### Policy Gate

Kotlin enforces:

- Allowed tools.
- Trusted domains and source types.
- Maximum calls per check.
- Network availability behavior.
- Freshness windows.
- Required source types by claim category.
- Whether discovery-only evidence can be cited.

Freshness examples:

- Breaking/current public claims require live or recently synced evidence.
- Evergreen debunks can use local evidence.
- Official announcements require official sources when available.
- If required freshness is not met, the Note must abstain or explicitly say the evidence is stale.

## User Experience

The result screen should make evidence discipline visible without becoming a research dashboard.

The Note result should show:

- Extracted claim, especially when read from a screenshot.
- Evidence status: live, last-synced, local-only, user-provided, or insufficient.
- Source freshness, such as “Sources fetched May 4, 2026.”
- Short Note in the user’s language.
- Source cards with dates and domains.
- Compact verification trail: image analysis, plan, policy gate, tools, evidence saved.
- “Still needs checking” section for unresolved or stale claims.

The hackathon demo should include:

- One successful verification case.
- One abstention case.
- One repeated/local-memory case where previously gathered evidence is reused.

The abstention case is intentional. It proves Resibo is designed for trust, not confident answer generation.

## Evaluation

Build a public-claims evaluation pack with 50-100 examples.

Primary categories:

- Transport announcements.
- Disaster/weather advisories.
- Government policy claims.
- Election/government claims.

Secondary category:

- Scams and fraud.

Metrics:

- Claim extraction accuracy.
- Retrieval hit rate.
- Source trust correctness.
- Freshness compliance.
- Abstention correctness.
- Citation grounding.
- Final Note usefulness and language preservation.

Comparisons:

- Gemma-only.
- Live tools without policy.
- Live tools plus Kotlin policy.
- Local memory reuse.

The submission should show that policy and evidence memory improve safety over a model-only answer.

## Error Handling

- If image analysis fails, continue with text-only input and disclose that the image could not be read.
- If a tool fails, mark its `ToolResult` as error and continue with other approved tools.
- If all tools fail, return an insufficient-evidence Note rather than a factual verdict.
- If evidence is stale for a current claim, say that the claim needs live verification.
- If sources conflict, summarize the conflict and avoid a firm verdict.
- If the policy gate rejects Gemma’s plan, execute a safe fallback plan or abstain.

## Implementation Scope

This design should be implemented as one coherent feature track: the Verification Agent Core.

In scope:

- Typed verification plan.
- Kotlin policy gate.
- Tool abstraction and normalized evidence records.
- Perplexity discovery as one tool.
- Official-source fetch/search for the hero category.
- Local evidence persistence and search.
- Updated Note UI with evidence mode and trace.
- Evaluation pack and basic metrics.

Out of scope for the first implementation plan:

- Full audio/video processing.
- Fine-tuning.
- Large-scale vector database optimization.
- Full community Notes network behavior.
- Medical-grade health claim workflow.

## Open Product Positioning

The README and settings should stop claiming “fully offline” as the whole product behavior while online tools are used. The accurate framing is:

> Gemma runs locally for private claim and image understanding. Evidence tools may run online when needed. Verified evidence is cached locally so Resibo becomes more useful offline over time.

This preserves the privacy story without overstating real-time offline verification.

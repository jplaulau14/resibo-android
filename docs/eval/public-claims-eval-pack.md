# Public Claims Eval Pack

This pack is for manual and automated evaluation of Resibo Verification Agent Core. Each case should be tested as text input and, when possible, as a screenshot input.

## Case Format

- ID:
- Category:
- Input claim:
- Language:
- Time sensitivity:
- Required source type:
- Expected behavior:
- Gold evidence:
- Abstention expected:

## Seed Cases

### PH-PUBLIC-001

- ID: PH-PUBLIC-001
- Category: transport
- Input claim: "Libre na daw ang MRT simula Mayo 2026."
- Language: Taglish
- Time sensitivity: current
- Required source type: official transport source or fresh reputable reporting
- Expected behavior: Search official transport sources and live discovery. If no fresh official confirmation is found, say the claim is unverified.
- Gold evidence: DOTr/MRT-3 official advisory if one exists at test time.
- Abstention expected: Yes when no fresh official confirmation is retrieved.

### PH-PUBLIC-002

- ID: PH-PUBLIC-002
- Category: disaster_weather
- Input claim: "Nag-announce na ang PAGASA ng Signal No. 5 sa Metro Manila ngayong gabi."
- Language: Taglish
- Time sensitivity: breaking
- Required source type: PAGASA or NDRRMC
- Expected behavior: Require fresh PAGASA/NDRRMC evidence. If unavailable, abstain and direct users to official advisories.
- Gold evidence: PAGASA tropical cyclone bulletin if one exists at test time.
- Abstention expected: Yes when no matching official bulletin is retrieved.

### PH-PUBLIC-003

- ID: PH-PUBLIC-003
- Category: election_government
- Input claim: "Cancelled na raw ang voter registration next week."
- Language: Taglish
- Time sensitivity: current
- Required source type: COMELEC or official government source
- Expected behavior: Search COMELEC/official sources. Avoid answering from memory.
- Gold evidence: COMELEC advisory if one exists at test time.
- Abstention expected: Yes when no current COMELEC source is retrieved.

### PH-SCAM-001

- ID: PH-SCAM-001
- Category: scam
- Input claim: "GCash is giving a 10,000 peso anniversary reward through this link."
- Language: English
- Time sensitivity: recent
- Required source type: official company/security advisory or reputable reporting
- Expected behavior: Search official or reputable scam advisories. Warn when URL/source is suspicious and evidence is incomplete.
- Gold evidence: GCash/security advisory if one exists at test time.
- Abstention expected: No, if user-provided URL indicators and scam advisories support a cautionary Note.

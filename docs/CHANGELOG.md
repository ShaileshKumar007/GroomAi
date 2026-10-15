# Groom.ai Documentation Pack — October 2026

Generated from the latest user-provided `*(6).md` baseline. Documentation-only update; the implementation repository was not independently audited.

## Main consistency fixes
1. **Image uploads are JPEG-only for MVP.** Mobile normalizes source images to JPEG; backend upload intent and verification must reject PNG/HEIC unless end-to-end PNG support is deliberately added later.
2. **AI consent gates external AI processing, not deterministic scoring.** Outfit candidates can be returned without AI consent; AI rationale/tips are omitted and no wardrobe data is sent to the hosted provider.
3. **Storage deletion is asynchronous.** API/database deletion queues storage keys in the outbox. The Phase 5 worker performs/retries deletion; do not claim the binary is already gone until confirmed.
4. **Physical-device verification is not assumed.** Roadmap marks Phase 1 physical-iPhone end-to-end verification as unconfirmed until fresh evidence is recorded.
5. **Historical identifiers are not silently guessed.** Phase 0 CI run ID and Phase 1 commit range are flagged for reconciliation against GitHub/repository history.
6. **ADR ledger status clarified.** Individual ADR statuses control acceptance; proposed cloud/AI-provider decisions remain proposed.

## Before implementation
- Confirm repository commit/CI identifiers and current migration state.
- Confirm the existing V1 schema includes `storage_deletion_outbox`.
- Ensure Phase 2 implementation and tests follow the JPEG-only contract.
- Ensure deterministic outfit generation does not invoke the AI provider without AI styling consent.
- Add physical-device evidence only after manually testing the current app against the current backend.

No code, dependencies, migrations, commits, pushes, or deployments are included in this documentation pack.

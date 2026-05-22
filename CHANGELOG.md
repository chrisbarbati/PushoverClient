# Changelog

All user-facing API coverage changes for this project are tracked here.

## [0.3.0] - 2026-05-19

### Added
- Async emergency receipt polling via `sendEmergencyMessageAndAwaitAcknowledgement(...)`
- Async emergency callback handling via `sendEmergencyMessageAndAwaitCallback(...)`
- Constructor-level `AsyncReceiptProperties` for polling interval, future expiration defaults, default callback URL, and callback-conflict warning control
- `CallbackServer` abstraction with initial `JettyCallbackServer` implementation
- Callback payload model for form-encoded Pushover callback POST bodies
- Pending-future cleanup on success, expiration, cancellation, and error paths
- Unit coverage for async polling, callback delivery, callback override behavior, timeout overrides, and cancellation cleanup
- Separate opt-in live integration tests for interactive async polling and callback acknowledgement flows

## [0.2.0] - 2026-05-17

### Added
- Receipt lifecycle support for `GET /1/receipts/{receipt}.json`
- Receipt cancellation support for `POST /1/receipts/{receipt}/cancel.json`
- Tag-based receipt cancellation support for `POST /1/receipts/cancel_by_tag/{tag}.json`
- Receipt response models covering documented acknowledgement, expiration, delivery, and callback fields
- MockWebServer coverage for all new receipt endpoints and failure mapping
- Opt-in live integration coverage for the combined emergency receipt lifecycle flow
- Versioned changelog tracking implemented API coverage over time

## [0.1.0] - 2026-05-14

### Added
- Core message sending via `POST /1/messages.json`
- Optional message parameters including emergency callbacks and tags
- Multipart and Base64 attachment submission
- End-to-end encryption support for supported message fields
- Sounds lookup via `GET /1/sounds.json`
- Application usage limits lookup via `GET /1/apps/limits.json`


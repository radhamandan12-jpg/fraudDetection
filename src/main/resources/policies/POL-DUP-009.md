---
documentId: POL-DUP-009
title: Duplicate transaction processing
version: "1.4"
effectiveDate: 2025-09-01
owner: Payments Operations Policy
---

## 1.1 Identification
A duplicate processing case exists when two or more posted transactions share
the same payment fingerprint: identical merchant, amount, currency and payment
credential token within a short window. A confirmed fingerprint match from the
transaction service is sufficient evidence.

## 1.2 Handling
Confirmed duplicate transaction processing is routed to PAYMENT_OPERATIONS.
Classification DUPLICATE_PROCESSING requires the fingerprint evidence ID in the
recommendation. Customer denial of the merchant is not required.

## 1.3 Suspected duplicates
Where fingerprint confirmation is unavailable but the customer statement and
statement history suggest the same merchant charged twice for one purchase,
record the uncertainty as missing information and route to MANUAL_REVIEW rather
than asserting a duplicate.

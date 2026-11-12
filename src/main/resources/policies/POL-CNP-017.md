---
documentId: POL-CNP-017
title: Card-not-present unauthorised transactions
version: "3.2"
effectiveDate: 2026-01-15
owner: Fraud Policy Office
status: ACTIVE
---

## 3.1 Scope
This procedure governs reported card-not-present transactions where the customer
denies recognising the merchant or the purchase. It applies to ecommerce and
remote transactions where the customer retains card possession.

## 3.2 Unauthorised indicators
Treat the following as independent indicators of unauthorised card-not-present
use: a device not previously observed or with LOW trust, a location inconsistent
with recent trusted activity (large distance in a short time window), an absent
or FAILED authentication outcome, and a merchant appearing on the internal
watch list. Two or more independent high-severity indicators together with a
customer denial support classification as UNAUTHORISED_TRANSACTION and routing
to FRAUD_INVESTIGATION.

## 3.3 Authentication gaps
When the authentication outcome is unavailable the analyst must record the gap
as missing information. A missing authentication outcome alone does not confirm
fraud; combine it with device and location evidence before classification.

## 3.4 Suspicious but unestablished
Where high-risk signals exist but the customer does not deny the transaction or
the claim is ambiguous, classify SUSPICIOUS_TRANSACTION and route to
FRAUD_INVESTIGATION for investigator confirmation.

## 3.5 Genuine activity
Where device and location are consistent with trusted activity, authentication
succeeded, and the evidence supports customer-authorised use, classify
GENUINE_TRANSACTION. Such cases still route to MANUAL_REVIEW for human
confirmation before closure.

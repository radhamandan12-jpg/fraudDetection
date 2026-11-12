---
documentId: POL-SLA-002
title: Routing queues and SLA targets
version: "1.0"
effectiveDate: 2025-06-01
owner: Operations Standards
---

## 4.1 Queues
Approved operational routes are FRAUD_INVESTIGATION, DISPUTE_OPERATIONS,
PAYMENT_OPERATIONS and MANUAL_REVIEW. Every recommendation must name exactly
one approved route.

## 4.2 SLA targets
Training targets: CRITICAL risk 4 hours, HIGH risk 8 hours, MEDIUM risk
24 hours, LOW risk 72 hours. Queue and priority are calculated by the
calculateCaseSla tool; analysts do not set targets manually.

## 4.3 Insufficient evidence
When required evidence is missing, contradictory or no approved policy passage
meets the retrieval threshold, classify INSUFFICIENT_EVIDENCE and route to
MANUAL_REVIEW. Analysts must identify the gap explicitly rather than assuming
a procedure.

## 4.4 Human review
Every automated recommendation requires human review before any operational
action. Reviewers may accept, revise, reroute or reject with a mandatory
reason, and every review action is audited.

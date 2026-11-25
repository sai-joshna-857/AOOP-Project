# CO-1 Case Study 5 — User Accounts & Email Verification Demo

**Course:** AOOP (25CS2205E)  
**Topic:** Domain Modeling — Self-Enforcing Objects, Entities vs. Value Objects, and Making Illegal States Unrepresentable

## Overview
A dependency-free Java 17+ domain model implementing user registration, email verification, optional contact details (`PhoneNumber`, `PostalAddress`), and account lifecycle management (`PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DELETED`).

## Scenarios Demonstrated (`38 passed, 0 failed`)
1. Rejection of malformed email addresses at construction (`EmailAddress.of`)
2. Accounts starting in `PendingVerification` state and blocked from login until verified
3. Rejection of duplicate accounts regardless of email capitalization
4. Rejection of wrong or expired verification tokens (`VerificationToken.isValidFor`)
5. Verified state carrying its mandatory `verifiedAt` timestamp (`EmailVerification.Verified`)
6. Resending verification invalidating previous tokens immediately
7. Canonical E.164 normalization of phone numbers (`PhoneNumber.parse`)
8. All-or-nothing validation of postal addresses (`PostalAddress.of`)
9. Resetting verification status to `PendingVerification` when an email address is changed
10. Enforcing login restrictions on `SUSPENDED` and `DELETED` accounts

## How to Compile & Run
```powershell
javac -d out src\casestudy5\*.java
java -cp out casestudy5.Main
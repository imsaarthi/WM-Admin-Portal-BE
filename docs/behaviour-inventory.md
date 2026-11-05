# Behaviour Inventory

The business rules the old application enforces, and what the new one does with each. Every later
phase turns its rows into tests before it builds the feature.

**Source:** `docs/admin-portal-module-documentation.md` (what the old application does). The rows
were taken from that document, not re-checked against the old code in this step.

**Not covered yet:** section 7 (the login flow in detail), sections 4.1 to 4.6 and 4.10 to 4.12, and
sections 8 to 11. Phase 3 reads section 7 before it writes the identity tests.

## How to read a row

| Column | Meaning |
| --- | --- |
| ID | `BR-<AREA>-nn`. A test names the ID it proves |
| Kind | **Keep**: the new system does the same. **Change**: the old behaviour is a known defect and the new system deliberately does something else (the Rule column says what). **Drop**: not carried over |
| Source | Section of the module documentation |
| Phase | The phase that writes the test: 2 platform, 3 identity, 4 careers, 5 leads, 6 content, 7 reporting |

Where a row says "(open)", the decision is not made yet. The gate is named.

## Platform (email, files, jobs)

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-PLT-01 | Mailgun is the only email provider. SendGrid is wired in the old code but never called, so it is not ported | Keep | 4.7 | 2 |
| BR-PLT-02 | The sender address is fixed and comes from configuration | Keep | 4.7 | 2 |
| BR-PLT-03 | A template placeholder is `{{name}}`. A missing value fails loudly. The old code left the placeholder in the text | Change | 4.7 | 2 |
| BR-PLT-04 | Every value put into an email template is HTML-escaped. The old applicant IN path did this, the v1 paths did not | Change | 4.7 | 2 |
| BR-PLT-05 | Email is queued in the caller's transaction and sent after commit, with retries and timeouts. The old code sent on the request thread with no retry and no timeout | Change | 4.7 | 2 |
| BR-PLT-06 | A Mailgun failure never fails the request. In the old code only the referral flow swallowed it, and the others returned 500 with the row already saved | Change | 4.7 | 2 |
| BR-PLT-07 | The live templates are: LoginOTP, ForgotPassword, ContactUsForm, AppointedRepresentative, Adviser, Introducer, Leads, IntroducerLead, Careers, LeadFromApplicant, newsLetter, Referral. CallBack, CallBackLead and the unused Subscribers template are not ported | Keep | 4.7 | 2 |
| BR-PLT-08 | Resumes and personal files are private and read through a short-lived link. Public website images are in a separate public container (gate D-10). The old code used one container for both | Change | 4.8 | 2 |
| BR-PLT-09 | A stored file gets a server-generated name. The old blob upload used the time plus the client's file name, and a repeated name silently kept the old file | Change | 4.8 | 2 |
| BR-PLT-10 | A missing or unreadable file is a 404 | Keep | 4.8 | 2 |
| BR-PLT-11 | The other tables keep a file as a stored name, with no foreign key | Keep | 4.8 | 2 |
| BR-PLT-12 | An expired job is closed by a scheduled job that runs once a day. The old expression ran every second on the 1st of the month | Change | 4.9 | 4 |

## Identity

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-IDN-01 | Login has two steps: the password is checked and a code is emailed, then the email and code are exchanged for tokens | Keep | 5.1 | 3 |
| BR-IDN-02 | A login code expires after 5 minutes | Keep | 5.1 | 3 |
| BR-IDN-03 | Only one live code exists per person. A new request replaces the old one | Keep | 5.1 | 3 |
| BR-IDN-04 | A code is single use and marked used. The old rows were never removed at login or at expiry | Change | 5.1 | 3 |
| BR-IDN-05 | There is no public sign-up. An admin creates accounts. The old sign-up always made a USER | Change | 5.1 | 3 |
| BR-IDN-06 | A user list never contains a password | Keep | 5.2 | 3 |
| BR-IDN-07 | Creating a user needs a role, and the email must not already exist | Keep | 5.2 | 3 |
| BR-IDN-08 | Changing a password needs the old password | Keep | 5.2 | 3 |
| BR-IDN-09 | Forgot password sends a reset link with a one-time token. The old code set a random 10-letter password and emailed it in clear text | Change | 5.2 | 3 |
| BR-IDN-10 | The role is stored as a string. The old code stored its position in the enum, so reordering the enum swapped every user's role | Change | 5.2 | 3 |
| BR-IDN-11 | An update that leaves the role out keeps the existing role. The old code copied null and later failed | Change | 5.2 | 3 |
| BR-IDN-12 | An account has a status, and repeated failures lock it for a while. The old code had no lockout and no disabled state (gate D-15) | Change | 5.1 | 3 |
| BR-IDN-13 | Access comes only from the user's role. The old `roles_and_access` table granted nothing and is folded into the role (gate D-14) | Change | 5.3 | 3 |
| BR-IDN-14 | The old soft delete of `roles_and_access` rows, which also blocked re-adding an email, is dropped with the table | Drop | 5.3 | 3 |

## careers

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-CAR-01 | A job is ACTIVE or INACTIVE, as an enum, and status is required. The old v1 status was free text, and an IN job created without one appeared in neither status list | Change | 5.6, 5.8 | 4 |
| BR-CAR-02 | A job number comes from a database sequence. IN jobs are `WFA-IN-0001`, `WFA-IN-0002` and so on. The UK series is `WFA0001` in v1 (open: UK series, gate D-16) | Keep | 5.6, 5.8 | 4 |
| BR-CAR-03 | A job number is unique, and the sequence never reuses the number of a deleted job. The old v1 number came from the last row, so two creates at once could share one | Keep | 5.6, 5.8 | 4 |
| BR-CAR-04 | Creating a job sets the posted date to today and the created time to now | Keep | 5.6 | 4 |
| BR-CAR-05 | A job update is partial: only the fields sent are changed, a field cannot be cleared, and skill options sent replace the whole list | Keep | 5.8 | 4 |
| BR-CAR-06 | An update never changes the job number, the posted date or the status. Status has its own endpoint. The old v1 update could overwrite the number and date | Change | 5.6, 5.8 | 4 |
| BR-CAR-07 | After an update is merged onto the stored job, minimum experience is at most maximum, and minimum CTC is at most maximum. Otherwise the answer is 400 | Keep | 5.8 | 4 |
| BR-CAR-08 | The job list is paged. It can be searched and filtered by department, job type and work style. Sort is by job number, newest first | Keep | 5.8 | 4 |
| BR-CAR-09 | Search is a partial, case-insensitive match over the listed columns, and `%` and `_` in the term match literally. Filters are exact and case-insensitive, and combine with the search by AND. A blank value means no restriction | Keep | 5.8 | 4 |
| BR-CAR-10 | A skill search uses EXISTS, so a job with several matching skills is counted once and paging stays correct | Keep | 5.8 | 4 |
| BR-CAR-11 | The public job list shows only ACTIVE jobs. The old IN list returned INACTIVE jobs too | Change | 5.8 | 4 |
| BR-CAR-12 | An ACTIVE job whose apply-by date is before today becomes INACTIVE, for UK and IN jobs. The old job covered v1 only | Change | 4.9, 5.8 | 4 |
| BR-CAR-13 | Department names are unique (409). There is a list and no update. Which module owns departments is gate D-08 (open) | Keep | 5.15 | 4 |
| BR-CAR-14 | An IN application has five sections. Personal, education, experience and final are required. Availability is optional (open: how the UK flat form fits, gate D-16) | Keep | 5.9 | 4 |
| BR-CAR-15 | A details field is required when its flag is true: education gap details when the education gap flag is set, other offer details when the other offer flag is set | Keep | 5.9 | 4 |
| BR-CAR-16 | Current and expected CTC are in LPA: a number with a one or two digit whole part and optional decimals, such as 4.5 or 12 | Keep | 5.9 | 4 |
| BR-CAR-17 | The declaration must be accepted. It is both required and true, because "true" alone accepts a missing value | Keep | 5.9 | 4 |
| BR-CAR-18 | A person can apply once per cooling period. The same email (ignoring case) and the same mobile, for any job and any name, is a 409 for 6 months after an accepted application (open: the period per region, gate D-16) | Keep | 5.9 | 4 |
| BR-CAR-19 | The 409 says how many months are left, rounded up, so a few days left reads "1 month" and never "0 months" | Keep | 5.9 | 4 |
| BR-CAR-20 | Two requests for the same person at the same time cannot both pass the cooling check | Keep | 5.9 | 4 |
| BR-CAR-21 | The cooling period end is set by the server in UTC and never by the client | Keep | 5.9 | 4 |
| BR-CAR-22 | Emails to the applicant and to leads are sent after the application is committed, so an email failure does not undo it | Keep | 5.9, 4.7 | 4 |
| BR-CAR-23 | The applicant email uses the Careers template and the lead notification uses LeadFromApplicant | Keep | 4.7 | 4 |
| BR-CAR-24 | The resume is uploaded first and the application stores the returned file name | Keep | 5.9, 4.8 | 4 |
| BR-CAR-25 | An application must refer to an existing job. The old IN code did not check, and the new table has a real foreign key | Change | 5.9 | 4 |
| BR-CAR-26 | An enum sent as an empty string is a 400 with "<Field> is required" | Keep | 5.9 | 4 |
| BR-CAR-27 | A request carries no server-set field such as the id or the created time | Keep | 5.9 | 4 |
| BR-CAR-28 | The same cooling rule applies to every application. The old v1 code never ran its duplicate check, so one person could apply any number of times (open: gate D-16) | Change | 5.7 | 4 |

## leads

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-LED-01 | Every website enquiry form is stored as a lead with a category: Customer, AppointedRepresentative, Adviser or Introducer | Keep | 5.4 | 5 |
| BR-LED-02 | Customer: the submitter gets ContactUsForm (subject "Thanks for Getting in Touch &#124; Your Enquiry Has Been Received") and the lead notification uses Leads | Keep | 5.4 | 5 |
| BR-LED-03 | AppointedRepresentative, Adviser and Introducer: the submitter gets the template of the same name (subject "Welcome to Wealthmax &#124; Your Application Is Received") and the lead notification uses IntroducerLead | Keep | 5.4 | 5 |
| BR-LED-04 | Lead notifications go to `leads@wealthmax.co.uk` with the subject `New Application Received` | Keep | 5.4 | 5 |
| BR-LED-05 | The category is an enum, so an unknown category is a 400. The old code matched text exactly, saved an unknown category and sent no email | Change | 5.4 | 5 |
| BR-LED-06 | The first name is the first word of the full name | Keep | 5.4 | 5 |
| BR-LED-07 | In the Leads template, `products` is the comma-joined product list when the page request is `contact-us-page`, and the page request value otherwise | Keep | 5.4 | 5 |
| BR-LED-08 | The whole submitted form is stored as JSON, so a new form field needs no schema change | Keep | 5.4 | 5 |
| BR-LED-09 | A lead is not checked for a duplicate email (the old check was commented out) | Keep | 5.4 | 5 |
| BR-LED-10 | A lead update changes only first name, last name, email and contact number | Keep | 5.4 | 5 |
| BR-LED-11 | The advisers list is separate from the Adviser lead category | Keep | 5.5 | 5 |
| BR-LED-12 | Adviser counts by category cover today (flag 0), this month (1), this year (2), or a custom start and end date. Any other flag is a 400. The old code returned null and failed | Change | 5.5 | 5 |
| BR-LED-13 | An adviser contact number is text in E.164. The old `Integer` could not hold an 11-digit UK number | Change | 5.5 | 5 |
| BR-LED-14 | The old adviser create, list, search and update failed with a 500 because a mapper was never set. That is a bug, not a behaviour | Drop | 5.5 | 5 |
| BR-LED-15 | The referral form submit is public. Every other referral route needs ADMIN | Keep | 5.12 | 5 |
| BR-LED-16 | A service is accepted as `PROTECTION` or `Protection`, repeats are removed, and an unknown service is a 400 | Keep | 5.12 | 5 |
| BR-LED-17 | The same referred email for the same service on the same day is a 409 and the whole submission is rejected. A different service or a later day is allowed | Keep | 5.12 | 5 |
| BR-LED-18 | A referral number is generated with a secure random source, is unique, and gives up with an error after 5 attempts | Keep | 5.12 | 5 |
| BR-LED-19 | The referrer is emailed only if they gave an email, and a failure never fails the submission | Keep | 5.12 | 5 |
| BR-LED-20 | The referral admin list takes a query, a start date and an end date, which combine. Both dates are needed for a range, and a single one is ignored. The list is newest first and paged | Keep | 5.12 | 5 |
| BR-LED-21 | Excel data is a record of an uploaded file (file name, title, description, created time). The file itself is in storage | Keep | 5.18 | 5 |

## content

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-CON-01 | A newsletter starts CREATED. It can move to PUBLISHED or UNPUBLISHED, and between those two. It can never go back to CREATED (400) | Keep | 5.10 | 6 |
| BR-CON-02 | Creating a newsletter needs a headline, a URL slug and an author id. A slug that exists is a 409, and an author that does not exist is a 404 | Keep | 5.10 | 6 |
| BR-CON-03 | The newsletter list can be filtered by status, and with no status it returns everything. It is paged | Keep | 5.10 | 6 |
| BR-CON-04 | The public site reads PUBLISHED newsletters only. The old reads by slug, id and category returned unpublished articles | Change | 5.10 | 6 |
| BR-CON-05 | The old start-up step that gave a slug and status to rows missing them is dropped. The data-migration script supplies them | Drop | 5.10 | 6 |
| BR-CON-06 | Subscribing is case-insensitive about duplicates, rejects an existing email, and sends the welcome email | Keep | 5.10 | 6 |
| BR-CON-07 | A reply belongs to a newsletter through a real foreign key with an explicit delete rule. The old code stored a plain number and left replies behind | Change | 5.10 | 6 |
| BR-CON-08 | Newsletter counts are given for each of the five categories | Keep | 5.10 | 6 |
| BR-CON-09 | An author is created and updated as multipart: a JSON part `authorData` and optional files `authorPicture`, `credential1`, `credential2`, `credential3` | Keep | 5.11 | 6 |
| BR-CON-10 | An author slug is the name in lowercase, with symbols removed and spaces turned into hyphens. A taken slug gets `-1`, `-2` and so on. A rename changes the slug only if the base slug changes | Keep | 5.11 | 6 |
| BR-CON-11 | An author's meta title is always "Name &#124; Wealthmax Financial Advisers", with the author's name first | Keep | 5.11 | 6 |
| BR-CON-12 | On an author update, a null leaves the field as it is, a blank name is a 400, other blank text clears the field, a file replaces the old one, and `removeX = true` with no file clears it | Keep | 5.11 | 6 |
| BR-CON-13 | New author files are deleted if the save fails, and replaced files are deleted only after the save succeeds. Deleting a stored file is best effort and a failure is only logged | Keep | 5.11 | 6 |
| BR-CON-14 | An author page shows the author, an h2 heading and the latest four PUBLISHED articles | Keep | 5.11 | 6 |
| BR-CON-15 | An author with newsletters cannot be deleted | Keep | 5.11 | 6 |
| BR-CON-16 | A team member update changes name, designation, category and file name. The LinkedIn and email links can be set on create only (open: confirm this is intended) | Keep | 5.13 | 6 |
| BR-CON-17 | A page header update changes page name, heading and sub heading. The file name can be set on create only (open: confirm this is intended) | Keep | 5.14 | 6 |
| BR-CON-18 | The home page counters are one row with four values, and the schema enforces that. The old code only meant it, and `add` returned a DTO without the new id | Change | 5.16 | 6 |
| BR-CON-19 | An uploaded image is stored once, in file storage, and the table keeps metadata only. The old code stored every upload twice (a file and a database column) | Change | 5.17 | 6 |
| BR-CON-20 | An image response gives the image id, not a server path | Change | 5.17 | 6 |

## reporting

| ID | Rule | Kind | Source | Phase |
| --- | --- | --- | --- | --- |
| BR-RPT-01 | The dashboard returns one count per lead category, for all time and for a start and end date given as `YYYY-MM-DD` | Keep | 5.4 | 7 |
| BR-RPT-02 | The leads export is an Excel file, `employees_<category>.xlsx`, sheet `Leads`, with the columns ID, Name, Email, Category, Contact Number, Experience, Available Time and Is From UK. The category is matched ignoring case | Keep | 5.18 | 7 |
| BR-RPT-03 | The export needs ADMIN and is written to the audit log. The old route was public, so anyone with the URL could download the leads | Change | 5.18 | 7 |

## Consequences for the plan

- **Rows marked Change are the work that is not a port.** Each one needs a test that proves the new behaviour, written before the feature (for example BR-IDN-04, BR-CAR-11, BR-CON-04, BR-RPT-03).
- **Rows marked "open" block their phase's tests** until the named gate is decided: D-08 (departments), D-14, D-15, D-16.
- **Section 7 (the login flow in detail) is not covered.** Phase 3 reads it before writing the identity tests, and adds rows for it here.

#!/usr/bin/env bash
# Tests for the WealthMax check hooks: run this after changing any rule in wealthmax-checks.sh.
#
#   bash .claude/hooks/test-hooks.sh        prints one line per case, then a pass/fail count
#
# Every case runs in a fresh clone of a throwaway template repo under the system temp folder, so
# this repo's working tree and index are never touched. The clones are left behind for debugging;
# the temp folder is cleaned by the OS.
set -u

REPO=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd) || exit 99
[ -d "$REPO/.claude/hooks" ] || { echo "can't find the repo from $0"; exit 99; }
# Short path with a space in it, on purpose: the real repo path has one, and Windows still caps
# most paths at 260 characters.
RUN="${TMPDIR:-/tmp}/wm hooks $$"
case "$RUN" in "$REPO"*) echo "refusing to write inside the repo: $RUN"; exit 99 ;; esac
TEMPLATE="$RUN/t"
OUTSIDE="$RUN/o"
OTHER="$RUN/x y"
CASE_NO=0
mkdir -p "$TEMPLATE" "$OUTSIDE" "$OTHER" || exit 99
echo "fixtures: $RUN"

# Fixture "secrets" for the C2 cases. They're assembled here rather than written out in full,
# because a complete key pattern anywhere in this file would make the commit check block this
# file itself (and would trip any other secret scanner pointed at the repo).
FAKE_PRIVATE_KEY=$(printf -- '-----BEGIN %s PRIVATE KEY-----' RSA)
FAKE_ACCOUNT_KEY=$(printf 'AccountKey=%s' ZmFrZWZha2VmYWtlZmFrZWZha2VmYWtlZmFrZWZha2U=)
FAKE_MAILGUN=$(printf '%s-%s-%s' 0123456789abcdef0123456789abcdef 01234567 89abcdef)
FAKE_MAILGUN_OLD=$(printf 'key-%s' 0123456789abcdef0123456789abcdef)

write() { mkdir -p "$(dirname "$1/$2")" && cat > "$1/$2"; }
tg() { git -C "$TEMPLATE" -c commit.gpgsign=false "$@"; }

# ---------------------------------------------------------------- template repo
tg init -q -b feature
tg config user.email fixture@example.com
tg config user.name fixture
tg config core.autocrlf true                # same as the real repo
tg config core.longpaths true
mkdir -p "$TEMPLATE/.claude/hooks"
for f in wealthmax-checks.sh post-edit.sh pre-commit.sh; do
  cp "$REPO/.claude/hooks/$f" "$TEMPLATE/.claude/hooks/$f" 2>/dev/null || echo "note: $f is missing from the repo"
done
cp "$REPO/.gitattributes" "$TEMPLATE/.gitattributes" 2>/dev/null || echo "note: .gitattributes is missing from the repo"
write "$TEMPLATE" .gitignore <<'EOF'
target/
.env
EOF
write "$TEMPLATE" src/main/resources/db/migration/V1__base.sql <<'EOF'
CREATE TABLE IF NOT EXISTS base_things (id BIGSERIAL PRIMARY KEY);
EOF
write "$TEMPLATE" src/main/java/com/wealthmax/careers/service/impl/JobServiceImpl.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.adminPortal.service.MailgunServices;

public class JobServiceImpl {
}
EOF
write "$TEMPLATE" src/main/resources/application.yml <<'EOF'
jwt:
  access-token-expiry: 3600000
EOF
tg add -A 2>/dev/null
tg commit -qm base
tg branch Dev
tg branch Staging
write "$TEMPLATE" src/main/resources/db/migration/V2__branch_only.sql <<'EOF'
CREATE TABLE IF NOT EXISTS branch_things (id BIGSERIAL PRIMARY KEY);
EOF
tg add -A 2>/dev/null
tg commit -qm "branch work"

# ---------------------------------------------------------------- helpers
PASS=0
FAIL=0
fail() { FAIL=$((FAIL + 1)); printf 'FAIL %s\n  %s\n' "$1" "$2"; }

# check NAME WANT_RC GOT_RC OUTPUT [PATTERN...]. A pattern starting with ! must be absent.
check() {
  local name=$1 want=$2 got=$3 out=$4 p
  shift 4
  if [ "$got" != "$want" ]; then
    fail "$name" "exit $got, expected $want. Output: [$out]"
    return
  fi
  for p in "$@"; do
    if [ "${p#!}" != "$p" ]; then
      case "$out" in *"${p#!}"*) fail "$name" "output must not contain '${p#!}'. Output: [$out]"; return ;; esac
    else
      case "$out" in *"$p"*) ;; *) fail "$name" "output lacks '$p'. Output: [$out]"; return ;; esac
    fi
  done
  PASS=$((PASS + 1))
  echo "ok   $name"
}

lines_with() { printf '%s\n' "$1" | grep -c -- "$2"; }

new_clone() {
  CASE_NO=$((CASE_NO + 1))
  C="$RUN/c$CASE_NO"
  git clone -q -c core.autocrlf=true -c core.longpaths=true "$TEMPLATE" "$C" 2>/dev/null || { echo "clone failed: $C ($1)"; exit 99; }
  git -C "$C" config user.email fixture@example.com
  git -C "$C" config user.name fixture
}
cg() { git -C "$C" -c commit.gpgsign=false "$@"; }
stage_all() { cg add -A 2>/dev/null; }
precommit() { (cd "$C" && bash .claude/hooks/pre-commit.sh) 2>&1; }

# ================================================================ line endings
new_clone eol
eol=$(cg ls-files --eol -- .claude/hooks/pre-commit.sh .claude/hooks/post-edit.sh .claude/hooks/wealthmax-checks.sh)
check "checkout: hook scripts keep LF under autocrlf=true" 0 0 "$eol" "pre-commit.sh" "post-edit.sh" "wealthmax-checks.sh" "!w/crlf"

# ================================================================ pre-commit
new_clone clean
write "$C" src/main/java/com/wealthmax/careers/service/impl/CleanService.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.careers.repository.JobRepository;
import com.wealthmax.careers.entity.JobEntity;
import com.wealthmax.leads.service.LeadService;
import com.wealthmax.leads.dto.response.LeadResponse;
import com.wealthmax.common.exception.NotFoundException;

public class CleanService {
    private final JobRepository jobRepository;

    public CleanService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public JobEntity get(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new NotFoundException("job"));
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void delete(Long id) { jobRepository.deleteById(id); }

    private static final String OAUTH_CLIENT_SECRET = "client_secret";
    private static final String PASSWORD_RESET_TEMPLATE = "password-reset";
    private int totalCount;
    private BigDecimal commissionAmount;
}
EOF
write "$C" src/main/java/com/wealthmax/careers/entity/JobEntity.java <<'EOF'
package com.wealthmax.careers.entity;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class JobEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ElementCollection
    private List<String> skills = new ArrayList<>();
}
EOF
write "$C" src/main/java/com/wealthmax/careers/dto/request/JobCreateRequest.java <<'EOF'
package com.wealthmax.careers.dto.request;

public class JobCreateRequest {
    private String title;
    private Long departmentId;
    private String roleDescription;
}
EOF
write "$C" src/main/resources/db/migration/V3__careers_clean.sql <<'EOF'
-- DROP TABLE old_stuff; is only a comment
CREATE TABLE IF NOT EXISTS clean_things (
    id BIGSERIAL PRIMARY KEY,
    amount NUMERIC(14,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT
);
CREATE INDEX IF NOT EXISTS idx_clean_things_created_at ON clean_things (created_at);
ALTER TABLE clean_things ADD COLUMN IF NOT EXISTS note TEXT;
COMMENT ON COLUMN clean_things.created_by IS 'user id';
EOF
write "$C" src/main/resources/application.yml <<'EOF'
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    baseline-on-migrate: false
jwt:
  access-token-expiry: 3600000
  refresh-token-expiry: 604800000
  reset-password-path: /reset-password
  token-bytes: 32
  secret: ${JWT_SECRET}
  empty-secret: ""
http:
  timeout: ${HTTP_TIMEOUT:30}
  retry-delay: ${RETRY_DELAY:5s}
feature:
  enabled: ${FEATURE_ENABLED:true}
  api-key: ${MAILGUN_API_KEY}
# password: a comment, not config
EOF
write "$C" docs/notes.md <<'EOF'
Azure keys look like AccountKey=... and never go in git.
Use UseDevelopmentStorage=true locally.
EOF
write "$C" src/test/java/com/wealthmax/careers/SomeRepositoryTest.java <<'EOF'
class SomeRepositoryTest {
    @Autowired private JobRepository repo;
    void t() { repo.findById(1L); }
    private static final String PASSWORD = "Test@Password123";
}
EOF
write "$C" src/test/resources/application-test.yml <<'EOF'
jwt:
  secret: test-only-secret-value-123
azure:
  connection-string: DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=ZmFrZWZha2VmYWtlZmFrZWZha2VmYWtlZmFrZWZha2U=;
EOF
write "$C" src/main/java/com/wealthmax/common/security/JwtAuthenticationFilter.java <<'EOF'
package com.wealthmax.common.security;

import com.wealthmax.common.exception.UnauthorizedException;

public class JwtAuthenticationFilter {
}
EOF
write "$C" .env.example <<'EOF'
DB_PASSWORD=
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: clean changes pass silently" 0 "$rc" "$out" '![' '!WealthMax'

new_clone j1
write "$C" src/main/java/com/wealthmax/careers/service/impl/Porting.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.adminPortal.service.MailgunServices;
import static com.wealthmax.adminPortal.utils.Helpers.trim;
import com.wealthmax.careers.service.JobService;
// import com.wealthmax.adminPortal.entity.Careers; in a comment is fine
public class Porting { }
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: J1 import from the old repository warns" 0 "$rc" "$out" "[warn]" "J1" \
  "Porting.java:3 " "Porting.java:4 " "!Porting.java:5 " "!Porting.java:6 " "!BLOCK"

new_clone j2
write "$C" src/main/java/com/wealthmax/leads/service/impl/LeadServiceImpl.java <<'EOF'
package com.wealthmax.leads.service.impl;

import com.wealthmax.careers.repository.JobRepository;
import com.wealthmax.careers.entity.JobEntity;
import com.wealthmax.careers.service.impl.JobServiceImpl;
import com.wealthmax.careers.mapper.JobMapper;
import com.wealthmax.careers.service.JobService;
import com.wealthmax.careers.dto.response.JobResponse;
import com.wealthmax.leads.repository.LeadRepository;
import com.wealthmax.common.exception.NotFoundException;
public class LeadServiceImpl { }
EOF
write "$C" src/main/java/com/wealthmax/common/util/Shared.java <<'EOF'
package com.wealthmax.common.util;

import com.wealthmax.careers.service.JobService;
import com.wealthmax.common.dto.PageResponseDto;
public class Shared { }
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: J2 another module's internals warn" 0 "$rc" "$out" "J2" \
  "LeadServiceImpl.java:3 " "LeadServiceImpl.java:4 " "LeadServiceImpl.java:5 " "LeadServiceImpl.java:6 " \
  "!LeadServiceImpl.java:7 " "!LeadServiceImpl.java:8 " "!LeadServiceImpl.java:9 " "!LeadServiceImpl.java:10 " \
  "Shared.java:3 " "!Shared.java:4 "

new_clone j5
write "$C" src/main/java/com/wealthmax/leads/dto/response/Money.java <<'EOF'
package com.wealthmax.leads.dto.response;

public class Money {
    private double commissionAmount;
    public Double totalPrice;
    private int totalCount;
    private BigDecimal amount;
    private float feePercent;
}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: J5 double/float money warns" 0 "$rc" "$out" "J5" "Money.java:4 " "Money.java:5 " "Money.java:8 " "!Money.java:6 " "!Money.java:7 "

new_clone j6
write "$C" src/main/java/com/wealthmax/leads/service/impl/Injected.java <<'EOF'
package com.wealthmax.leads.service.impl;

public class Injected {
    @Autowired
    private SupportRepository repo;
}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: J6 field injection warns" 0 "$rc" "$out" "J6" "Injected.java:4 "

new_clone j7
write "$C" src/main/java/com/wealthmax/careers/dto/request/JobCreateRequest.java <<'EOF'
package com.wealthmax.careers.dto.request;

public class JobCreateRequest {
    private Long id;
    private String role;
    private Instant createdAt;
    private String title;
    private Long departmentId;
}
EOF
write "$C" src/main/java/com/wealthmax/careers/dto/response/JobResponse.java <<'EOF'
package com.wealthmax.careers.dto.response;

public class JobResponse {
    private Long id;
}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: J7 server-owned field on a request DTO warns" 0 "$rc" "$out" "J7" \
  "JobCreateRequest.java:4 " "JobCreateRequest.java:5 " "JobCreateRequest.java:6 " "!JobCreateRequest.java:7 " "!JobCreateRequest.java:8 " "!JobResponse.java"

new_clone c5
write "$C" src/main/resources/application.yml <<'EOF'
spring:
  jpa:
    hibernate:
      ddl-auto: update
EOF
write "$C" src/main/resources/application-local.yml <<'EOF'
spring:
  jpa:
    hibernate:
      ddl-auto: validate
EOF
write "$C" src/main/resources/application.properties <<'EOF'
spring.jpa.hibernate.ddl-auto=create-drop
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C5 ddl-auto other than validate warns" 0 "$rc" "$out" "C5" \
  "application.yml:4 " "application.properties:1 " "!application-local.yml" "!BLOCK"

new_clone s1-shared
echo "ALTER TABLE base_things ADD COLUMN IF NOT EXISTS extra TEXT;" >> "$C/src/main/resources/db/migration/V1__base.sql"
stage_all
out=$(precommit); rc=$?
check "pre-commit: S1 editing a migration on Dev blocks" 1 "$rc" "$out" "[BLOCK] S1" "V1__base.sql" "--no-verify"

new_clone s1-branch
echo "ALTER TABLE branch_things ADD COLUMN IF NOT EXISTS extra TEXT;" >> "$C/src/main/resources/db/migration/V2__branch_only.sql"
stage_all
out=$(precommit); rc=$?
check "pre-commit: S1 editing a branch-only migration warns" 0 "$rc" "$out" "[warn]" "S1" "V2__branch_only.sql" "!BLOCK"

new_clone s1-delete
cg rm -q src/main/resources/db/migration/V1__base.sql
out=$(precommit); rc=$?
check "pre-commit: S1 deleting a migration on Dev blocks" 1 "$rc" "$out" "[BLOCK] S1" "V1__base.sql"

new_clone s1-rename
cg mv src/main/resources/db/migration/V1__base.sql src/main/resources/db/migration/V1__renamed.sql
out=$(precommit); rc=$?
check "pre-commit: S1 renaming a migration on Dev blocks" 1 "$rc" "$out" "[BLOCK] S1" "V1__base.sql"

new_clone s2
write "$C" src/main/resources/db/migration/V5__bad.sql <<'EOF'
CREATE TABLE bad_things (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    amount DOUBLE PRECISION
);
CREATE INDEX idx_bad_things_created_at ON bad_things (created_at);
ALTER TABLE bad_things ADD COLUMN note TEXT;
DROP TABLE old_things;
DELETE FROM base_things WHERE id = 1;
TRUNCATE base_things;
ALTER TABLE bad_things DROP COLUMN IF EXISTS note;
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: S2 migration conventions warn" 0 "$rc" "$out" "S2" \
  "V5__bad.sql:1 " "V5__bad.sql:2 " "V5__bad.sql:3 " "V5__bad.sql:4 " "V5__bad.sql:5 " "!V5__bad.sql:6 " \
  "V5__bad.sql:7 " "V5__bad.sql:8 " "V5__bad.sql:9 " "V5__bad.sql:10 " "V5__bad.sql:11 " "V5__bad.sql:12 " "!BLOCK"

new_clone c1-fallback
write "$C" src/main/resources/application.yml <<'EOF'
jwt:
  access-token-expiry: 3600000
  secret: ${JWT_SECRET:fallbackSecretValue123}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C1 secret fallback blocks, value hidden" 1 "$rc" "$out" "[BLOCK] C1" "application.yml:3 " "JWT_SECRET" "!fallbackSecretValue123"

new_clone c1-literal
write "$C" src/main/resources/application.yml <<'EOF'
jwt:
  access-token-expiry: 3600000
mailgun:
  api-key: literalSecretValue456
EOF
write "$C" src/main/resources/application-staging.yml <<'EOF'
spring:
  datasource:
    password: stagingPass99
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C1 literal secrets block, values hidden" 1 "$rc" "$out" "[BLOCK] C1" \
  "application.yml:4 " "api-key" "application-staging.yml:3 " "!literalSecretValue456" "!stagingPass99"

new_clone c2-private-key
write "$C" docs/keys.md <<EOF
Example:
$FAKE_PRIVATE_KEY
MIIEfakeFAKEfakeFAKEfake
-----END RSA PRIVATE KEY-----
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C2 private key blocks" 1 "$rc" "$out" "[BLOCK] C2" "keys.md:2 " "!MIIEfake"

new_clone c2-account-key
write "$C" src/main/java/com/wealthmax/common/config/Storage.java <<EOF
package com.wealthmax.common.config;

public class Storage {
    String conn = "DefaultEndpointsProtocol=https;AccountName=prodacct;$FAKE_ACCOUNT_KEY;EndpointSuffix=core.windows.net";
}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C2 Azure account key blocks" 1 "$rc" "$out" "[BLOCK] C2" "Storage.java:4 " "!ZmFrZWZh"

new_clone c2-mailgun
write "$C" src/main/resources/application.properties <<EOF
mailgun.key=$FAKE_MAILGUN
EOF
write "$C" docs/old-key.md <<EOF
old style: $FAKE_MAILGUN_OLD
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C2 Mailgun keys block" 1 "$rc" "$out" "[BLOCK] C2" "application.properties:1 " "old-key.md:1 " \
  "!0123456789abcdef0123456789abcdef"

new_clone c3
write "$C" src/main/java/com/wealthmax/common/security/Secrets.java <<'EOF'
package com.wealthmax.common.security;

public class Secrets {
    private static final String JWT_SECRET = "Hardcoded@Secret789";
    private String apiKey = "sk_live_fake_1234567890";
    private static final String OAUTH_CLIENT_SECRET = "client_secret";
    private static final String PASSWORD = "password";
    private String secret = "${jwt.secret}";
}
EOF
stage_all
out=$(precommit); rc=$?
check "pre-commit: C3 hardcoded Java secrets block" 1 "$rc" "$out" "[BLOCK] C3" \
  "Secrets.java:4 " "Secrets.java:5 " "!Secrets.java:6 " "!Secrets.java:7 " "!Secrets.java:8 " "!Hardcoded@Secret789" "!sk_live_fake"

new_clone c4
write "$C" .env <<'EOF'
DB_PASSWORD=envFileSecret1
EOF
write "$C" .env.example <<'EOF'
DB_PASSWORD=
EOF
cg add -f .env .env.example 2>/dev/null
out=$(precommit); rc=$?
check "pre-commit: C4 staged .env blocks" 1 "$rc" "$out" "[BLOCK] C4" "!.env.example" "!envFileSecret1"

new_clone cap
{
  echo "package com.wealthmax.leads.service.impl;"
  echo "public class Many {"
  i=0
  while [ $i -lt 70 ]; do echo "    @Autowired private Repo r$i;"; i=$((i + 1)); done
  echo "}"
} > "$C/src/main/java/Many.java"
stage_all
out=$(precommit); rc=$?
check "pre-commit: long output is capped" 0 "$rc" "$out" "J6" "more"

new_clone crlf
mkdir -p "$C/src/main/java/com/wealthmax/careers/service"
printf 'package com.wealthmax.careers.service;\r\n\r\nimport com.wealthmax.adminPortal.entity.Careers;\r\npublic class Crlf {\r\n}\r\n' \
  > "$C/src/main/java/com/wealthmax/careers/service/Crlf.java"
stage_all
out=$(precommit); rc=$?
check "pre-commit: CRLF files are checked" 0 "$rc" "$out" "J1" "Crlf.java:3 "

# Through real git commits, with this machine's hook line and with the documented one.
for form in plain bash; do
  new_clone "git-commit-$form"
  if [ "$form" = plain ]; then
    printf '%s\n' '#!/bin/bash' 'exec "$(git rev-parse --show-toplevel)/.claude/hooks/pre-commit.sh"' > "$C/.git/hooks/pre-commit"
  else
    printf '%s\n' '#!/bin/sh' 'exec bash "$(git rev-parse --show-toplevel)/.claude/hooks/pre-commit.sh"' > "$C/.git/hooks/pre-commit"
  fi
  chmod +x "$C/.git/hooks/pre-commit"
  before=$(cg rev-parse HEAD)
  write "$C" src/main/resources/application.yml <<'EOF'
jwt:
  secret: ${JWT_SECRET:commitFallback77}
EOF
  stage_all
  out=$(cg commit -qm blocked 2>&1); rc=$?
  after=$(cg rev-parse HEAD)
  [ "$rc" != 0 ] && [ "$before" = "$after" ] && rc=blocked
  check "git commit ($form hook line): a secret stops the commit" blocked "$rc" "$out" "[BLOCK] C1" "!commitFallback77"
  write "$C" src/main/resources/application.yml <<'EOF'
jwt:
  secret: ${JWT_SECRET}
EOF
  stage_all
  out=$(cg commit -qm clean 2>&1); rc=$?
  check "git commit ($form hook line): a clean commit goes through" 0 "$rc" "$out"
done

# ================================================================ post-edit
new_clone pe
PE=$C
PE_WIN=$(cygpath -w "$PE")

# post_edit WINDOWS_PATH [TOOL]: runs the hook the way Claude Code does; prints stderr only.
post_edit_win() {
  local p=${1//\\/\\\\} json
  json='{"session_id":"t","hook_event_name":"PostToolUse","tool_name":"'"${2:-Write}"'","tool_input":{"file_path":"'"$p"'","content":"a \"file_path\":\"C:\\\\decoy.java\" b"},"tool_response":{"filePath":"'"$p"'","success":true}}'
  printf '%s' "$json" | (cd "$OUTSIDE" && CLAUDE_PROJECT_DIR="$PE_WIN" bash -c 'bash "${CLAUDE_PROJECT_DIR}/.claude/hooks/post-edit.sh"') 2>&1 >/dev/null
}
post_edit() { post_edit_win "$(cygpath -w "$1")" "${2:-Write}"; }

write "$PE" src/main/java/com/wealthmax/careers/service/impl/NewFinder.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.adminPortal.entity.Careers;
public class NewFinder { }
EOF
t0=$(date +%s%N)
out=$(post_edit "$PE/src/main/java/com/wealthmax/careers/service/impl/NewFinder.java"); rc=$?
t1=$(date +%s%N)
check "post-edit: new file with J1 reports to Claude" 2 "$rc" "$out" "J1" "NewFinder.java:3 " "!BLOCK"
echo "     (post-edit took $(( (t1 - t0) / 1000000 )) ms)"

write "$PE" src/main/java/com/wealthmax/careers/service/impl/JobServiceImpl.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.adminPortal.service.MailgunServices;
import com.wealthmax.adminPortal.repository.ApplicantRepository;

public class JobServiceImpl {
}
EOF
out=$(post_edit "$PE/src/main/java/com/wealthmax/careers/service/impl/JobServiceImpl.java" Edit); rc=$?
check "post-edit: only the changed line of a tracked file is reported" 2 "$rc" "$out" "JobServiceImpl.java:4 " "!JobServiceImpl.java:3 "

write "$PE" src/main/java/com/wealthmax/careers/service/impl/Fine.java <<'EOF'
package com.wealthmax.careers.service.impl;

import com.wealthmax.careers.repository.JobRepository;
import com.wealthmax.common.exception.NotFoundException;

public class Fine {
    Job one(Long id) { return repo.findById(id).orElseThrow(); }
}
EOF
out=$(post_edit "$PE/src/main/java/com/wealthmax/careers/service/impl/Fine.java"); rc=$?
check "post-edit: clean Java is silent" 0 "$rc" "$out" '!J'

write "$PE" docs/readme.md <<'EOF'
Nothing to see.
EOF
out=$(post_edit "$PE/docs/readme.md"); rc=$?
check "post-edit: clean Markdown is silent" 0 "$rc" "$out" '!J'

write "$OUTSIDE" Loose.java <<'EOF'
import com.wealthmax.adminPortal.entity.Careers;
class Loose { }
EOF
out=$(post_edit "$OUTSIDE/Loose.java"); rc=$?
check "post-edit: file outside any repo is ignored" 0 "$rc" "$out" '!J1'

git -C "$OTHER" init -q
write "$OTHER" src/main/java/com/wealthmax/careers/X.java <<'EOF'
import com.wealthmax.adminPortal.entity.Careers;
class X { }
EOF
out=$(post_edit "$OTHER/src/main/java/com/wealthmax/careers/X.java"); rc=$?
check "post-edit: file in another repo is ignored" 0 "$rc" "$out" '!J1'

write "$PE" target/generated/com/wealthmax/careers/Gen.java <<'EOF'
import com.wealthmax.adminPortal.entity.Careers;
class Gen { }
EOF
out=$(post_edit "$PE/target/generated/com/wealthmax/careers/Gen.java"); rc=$?
check "post-edit: ignored file is skipped" 0 "$rc" "$out" '!J1'

out=$(printf 'not json at all' | (cd "$OUTSIDE" && CLAUDE_PROJECT_DIR="$PE_WIN" bash -c 'bash "${CLAUDE_PROJECT_DIR}/.claude/hooks/post-edit.sh"') 2>&1 >/dev/null); rc=$?
check "post-edit: unreadable input exits quietly" 0 "$rc" "$out"
out=$( (cd "$OUTSIDE" && CLAUDE_PROJECT_DIR="$PE_WIN" bash -c 'bash "${CLAUDE_PROJECT_DIR}/.claude/hooks/post-edit.sh"') < /dev/null 2>&1 >/dev/null); rc=$?
check "post-edit: empty input exits quietly" 0 "$rc" "$out"

echo "ALTER TABLE base_things ADD COLUMN IF NOT EXISTS extra TEXT;" >> "$PE/src/main/resources/db/migration/V1__base.sql"
out=$(post_edit "$PE/src/main/resources/db/migration/V1__base.sql" Edit); rc=$?
check "post-edit: editing a migration on Dev warns Claude" 2 "$rc" "$out" "S1" "V1__base.sql" "!BLOCK"

write "$PE" src/main/resources/application.yml <<'EOF'
jwt:
  access-token-expiry: 3600000
  api-key: literalSecretValue456
EOF
out=$(post_edit "$PE/src/main/resources/application.yml" Edit); rc=$?
check "post-edit: secret in config warns Claude, value hidden" 2 "$rc" "$out" "C1" "application.yml:3 " "!literalSecretValue456" "!BLOCK"

printf 'package com.wealthmax.careers.service;\r\n\r\npublic class CrlfEdit {\r\n    @Autowired private JobRepository repo;\r\n}\r\n' \
  > "$PE/src/main/java/com/wealthmax/careers/service/CrlfEdit.java"
out=$(post_edit "$PE/src/main/java/com/wealthmax/careers/service/CrlfEdit.java"); rc=$?
check "post-edit: CRLF new file is checked" 2 "$rc" "$out" "J6" "CrlfEdit.java:4 "

out=$(post_edit "$PE/src/main/resources/db/migration/V2__branch_only.sql" Edit); rc=$?
check "post-edit: unchanged tracked file is silent" 0 "$rc" "$out"

win=$(cygpath -w "$PE/src/main/java/com/wealthmax/careers/service/impl/NewFinder.java")
lower="$(printf '%s' "${win%\\*}" | tr '[:upper:]' '[:lower:]')\\${win##*\\}"
out=$(post_edit_win "$lower"); rc=$?
check "post-edit: lowercase drive and folders still work" 2 "$rc" "$out" "J1"

# ================================================================ fail-loud guard
# An empty rules block would make awk pass everything silently, so wm_check_diff must refuse.
out=$( (. "$REPO/.claude/hooks/wealthmax-checks.sh"; WM_AWK=""; echo "" | wm_check_diff commit) 2>&1 ); rc=$?
check "empty rules are refused, not silently passed" 1 "$rc" "$out" "the rules are empty"

echo
echo "passed: $PASS  failed: $FAIL"
[ "$FAIL" -eq 0 ]

# WealthMax rules shared by two hooks, so they can't drift apart:
#   post-edit.sh   Claude Code, after each Edit or Write: every finding is a warning for Claude.
#   pre-commit.sh  git, before each commit: secrets and applied-migration edits block the commit.
# Sourced, not run. Only added lines are checked. Never print a matched line: it may hold a secret.
# Rule IDs: J1 old-repository import, J2 another module's internals, J5 money as double/float,
# J6 field injection, J7 server-owned field on a request DTO, S1-S2 migrations, C1-C4 secrets,
# C5 ddl-auto other than validate. CLAUDE.md (Development Workflow) lists them too.

# Branches whose migrations have already run on a shared database.
WM_SHARED_BRANCHES="origin/Dev origin/Staging"

# A plain unified diff whatever the user's git config says.
wm_diff() {
    git -c core.quotepath=off diff -U0 --no-color --no-ext-diff --no-renames \
        --src-prefix=a/ --dst-prefix=b/ "$@"
}

wm_is_migration() {
    case "$1" in src/main/resources/db/migration/*.sql) return 0 ;; esac
    return 1
}

wm_on_shared_branch() {
    local ref
    for ref in $WM_SHARED_BRANCHES; do
        git cat-file -e "$ref:$1" 2>/dev/null && return 0
    done
    return 1
}

# wm_line MODE SEVERITY ID LOCATION MESSAGE, in the same format as the awk rules print.
wm_line() {
    if [ "$2" = block ] && [ "$1" = commit ]; then
        printf '[BLOCK] %s %s  %s\n' "$3" "$4" "$5"
    elif [ "$2" = block ]; then
        printf '[warn]  %s %s  %s This will block the commit.\n' "$3" "$4" "$5"
    else
        printf '[warn]  %s %s  %s\n' "$3" "$4" "$5"
    fi
}

# S1 for a migration that is already committed and is being changed, deleted or renamed.
wm_migration_finding() {
    if wm_on_shared_branch "$2"; then
        wm_line "$1" block S1 "$2" "this migration is on Dev or Staging, so it has run on a shared database. Changing, deleting or renaming it breaks Flyway's checksum there. Write a new migration instead."
    else
        wm_line "$1" warn S1 "$2" "this migration is already committed on this branch. Changing it is fine only if it hasn't run on a shared database yet."
    fi
}

# S1 and C4, from the staged file list.
wm_check_paths() {
    local status path
    git -c core.quotepath=off diff --cached --name-status --no-renames -z |
    while IFS= read -r -d '' status && IFS= read -r -d '' path; do
        case "$status" in
            M|D|T) wm_is_migration "$path" && wm_migration_finding "$1" "$path" ;;
        esac
        case "$status" in
            A|M|T)
                case "${path##*/}" in
                    .env.example|.env.sample|.env.template) ;;
                    .env|.env.*) wm_line "$1" block C4 "$path" "environment files hold secrets and stay out of git. Unstage it with: git restore --staged \"$path\"" ;;
                esac ;;
        esac
    done
}

# Reads `git diff -U0` output on stdin and prints one line per finding.
# The empty-rules guard matters: awk with an empty program passes everything silently, so a
# truncated copy of this file would turn the checks off without anyone noticing.
wm_check_diff() {
    [ -n "$WM_AWK" ] || { echo "wealthmax-checks.sh: the rules are empty, so nothing was checked." >&2; return 1; }
    awk -v mode="$1" "$WM_AWK"
}

IFS= read -r -d '' WM_AWK <<'AWK' || true
function rep(s, n,    r) { r = ""; while (n-- > 0) r = r s; return r }

function report(id, severity, msg) {
    if (severity == "block" && mode == "commit") {
        printf "[BLOCK] %s %s:%d  %s\n", id, path, lineno, msg
        return
    }
    if (severity == "block") msg = msg " This will block the commit."
    if (++warnings <= 50) printf "[warn]  %s %s:%d  %s\n", id, path, lineno, msg
}

# True for names like api-key, jwt.secret or DB_PASSWORD, but not access-token-expiry.
function secret_name(name,    n) {
    n = tolower(name)
    sub(/.*\./, "", n)
    gsub(/[-_]/, "", n)
    return n ~ /(secret|password|passwd|pepper|apikey|accesskey|accountkey|privatekey|signingkey|hmackey|credential|credentials|connectionstring)$/
}

# True when a config value can't be a secret. Numbers can be: a numeric password is still one.
function harmless_value(v) {
    sub(/^[[:space:]]+/, "", v)
    sub(/[[:space:]]+$/, "", v)
    if (v ~ /^".*"$/ || v ~ /^'.*'$/) v = substr(v, 2, length(v) - 2)
    return v == "" || tolower(v) ~ /^(true|false|null|none|~)$/ || v ~ /^\$\{/
}

function set_path(p) {
    path = p
    java_main = path ~ /^src\/main\/java\/.*\.java$/
    module = ""
    if (java_main && match(path, /^src\/main\/java\/com\/wealthmax\/[^\/]+\//))
        module = substr(path, 29, RLENGTH - 29)
    request_dto = java_main && path ~ /\/dto\/request\//
    migration = path ~ /^src\/main\/resources\/db\/migration\/.*\.sql$/
    main_config = path ~ /^src\/main\/resources\/(.*\/)?application[^\/]*\.(yml|yaml|properties)$/
}

function check(t,    low, trimmed) {
    sub(/\r$/, "", t)
    low = tolower(t)
    trimmed = t
    sub(/^[[:space:]]+/, "", trimmed)

    if (t ~ PRIVATE_KEY)
        report("C2", "block", "private key. Keep keys in a secret store, never in git.")
    if (t ~ ACCOUNT_KEY && t !~ /AccountName=devstoreaccount1/)
        report("C2", "block", "Azure storage account key. Load it from the environment or Key Vault.")
    if (t ~ MAILGUN || t ~ MAILGUN_OLD)
        report("C2", "block", "Mailgun API key. Load it from an environment variable with no fallback.")

    if (java_main) check_java(t, low, trimmed)
    else if (migration) check_sql(low, trimmed)
    else if (main_config) check_config(t, trimmed)
}

function check_java(t, low, trimmed,    s, val, target) {
    # C3 looks at comments too: a key in a comment is still a leaked key.
    if (match(low, /[a-z0-9_]*(secret|password|passwd|apikey|api_key|accesskey|access_key|privatekey|private_key|secretkey|secret_key|signingkey|signing_key|hmackey|hmac_key|encryptionkey|encryption_key|authtoken|auth_token|accesstoken|access_token|apitoken|api_token)[[:space:]]*=[[:space:]]*"[^"]*"/)) {
        val = substr(t, RSTART, RLENGTH)
        sub(/^[^"]*"/, "", val)
        sub(/"$/, "", val)
        # Letters-only values such as "client_secret" are names of things, not secrets.
        if (length(val) >= 8 && val !~ /^[A-Za-z]+([_.-][A-Za-z]+)*$/ && val !~ /^\$\{/)
            report("C3", "block", "hardcoded secret. Load it from configuration (an environment variable), never a string literal.")
    }
    if (trimmed ~ /^(\/\/|\/\*|\*)/) return

    if (t ~ /^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?com\.wealthmax\.adminPortal\./)
        report("J1", "warn", "the old repository's com.wealthmax.adminPortal code is never imported. Port the behaviour into the owning module or common.")
    if (module != "" && match(t, /^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?com\.wealthmax\.[A-Za-z0-9_]+\./)) {
        target = substr(t, RSTART, RLENGTH)
        sub(/^.*com\.wealthmax\./, "", target)
        sub(/\.$/, "", target)
        if (target != module && target != "adminPortal") {
            if (module == "common")
                report("J2", "warn", "common depends on no feature module. Move the shared piece into common, or let the module call common instead.")
            else if (t ~ /\.(entity|repository|mapper)(\.|;)/ || t ~ /\.service\.impl(\.|;)/)
                report("J2", "warn", "another module's entity, repository, mapper or service.impl is off limits. Call its service interface with ids and DTOs.")
        }
    }
    if (low ~ /(^|[^a-z0-9_])(double|float)[[:space:]]+[a-z0-9_]*(amount|price|fee|commission|balance|total|cost|premium|salary|income|payment|percent)/)
        report("J5", "warn", "money and percentages are BigDecimal, never double or float.")
    if (t ~ /@Autowired/)
        report("J6", "warn", "use constructor injection, not @Autowired.")
    if (request_dto && t ~ /(private|protected|public)[[:space:]]+[A-Za-z<>.]+[[:space:]]+(id|role|roles|createdAt|updatedAt|createdBy|updatedBy|createdDate)[[:space:]]*[;=]/)
        report("J7", "warn", "a request DTO never carries id, role or audit fields. The server owns them; a client-set id can overwrite an existing row.")
}

function check_sql(low, trimmed) {
    if (trimmed ~ /^--/) return
    if (low ~ /uuid[^,]*primary[[:space:]]+key/)
        report("S2", "warn", "primary keys are id BIGSERIAL, not UUID.")
    if (low ~ /(^|[^a-z_])timestamp([^a-z_]|$)/ && low !~ /timestamp[[:space:]]*(\([0-9]+\))?[[:space:]]+with[[:space:]]+time[[:space:]]+zone/)
        report("S2", "warn", "use TIMESTAMPTZ, not TIMESTAMP without a time zone.")
    if (low ~ /^[[:space:]]*(add[[:space:]]+column[[:space:]]+(if[[:space:]]+not[[:space:]]+exists[[:space:]]+)?)?(created_by|updated_by)[[:space:]]+[a-z]/ && low !~ /(created_by|updated_by)[[:space:]]+(bigint|int8)/)
        report("S2", "warn", "created_by and updated_by hold a user ID: BIGINT.")
    if (low ~ /^[[:space:]]*(add[[:space:]]+column[[:space:]]+(if[[:space:]]+not[[:space:]]+exists[[:space:]]+)?)?[a-z_]*(amount|price|fee|commission|balance|total|cost|premium|salary|income|payment|percent)[a-z_]*[[:space:]]+(double[[:space:]]+precision|real|float)/)
        report("S2", "warn", "money is NUMERIC(14,2), never floating point.")
    if (low ~ /create[[:space:]]+table[[:space:]]/ && low !~ /if[[:space:]]+not[[:space:]]+exists/)
        report("S2", "warn", "use CREATE TABLE IF NOT EXISTS.")
    if (low ~ /create[[:space:]]+(unique[[:space:]]+)?index[[:space:]]/ && low !~ /if[[:space:]]+not[[:space:]]+exists/)
        report("S2", "warn", "use CREATE INDEX IF NOT EXISTS.")
    if (low ~ /add[[:space:]]+column[[:space:]]/ && low !~ /if[[:space:]]+not[[:space:]]+exists/)
        report("S2", "warn", "use ADD COLUMN IF NOT EXISTS.")
    if (low ~ /drop[[:space:]]+(table|index|column|constraint|view|sequence|trigger|function|type)[[:space:]]/ && low !~ /if[[:space:]]+exists/)
        report("S2", "warn", "use DROP ... IF EXISTS.")
    if (low ~ /delete[[:space:]]+from[[:space:]]|truncate[[:space:]]|drop[[:space:]]+(table|column)[[:space:]]/)
        report("S2", "warn", "this destroys data. Check nothing still reads it, and that the data is migrated or backed up first.")
}

function check_config(t, trimmed,    s, m, p, name, key, val) {
    if (trimmed ~ /^[#!]/) return
    s = t
    while (match(s, /\$\{[^}:]+:[^}]*\}/)) {
        m = substr(s, RSTART + 2, RLENGTH - 3)
        s = substr(s, RSTART + RLENGTH)
        p = index(m, ":")
        name = substr(m, 1, p - 1)
        if (secret_name(name) && !harmless_value(substr(m, p + 1)))
            report("C1", "block", "${" name "} has a fallback value. Remove it: a missing variable must stop startup, not fall back to a value everyone with the repo can read.")
    }
    if (match(t, /^[[:space:]]*(-[[:space:]]+)?[A-Za-z0-9_.-]+[[:space:]]*[:=]/)) {
        key = substr(t, RSTART, RLENGTH)
        val = substr(t, RSTART + RLENGTH)
        sub(/[[:space:]]*[:=]$/, "", key)
        sub(/^[[:space:]]*(-[[:space:]]+)?/, "", key)
        sub(/[[:space:]]+#.*$/, "", val)
        if (key ~ /ddl-auto$/ && !harmless_value(val) && tolower(val) !~ /^[[:space:]]*["']?validate["']?[[:space:]]*$/)
            report("C5", "warn", "Flyway owns the schema, so ddl-auto stays validate in every profile. update or create hides a missing migration.")
        if (secret_name(key) && !harmless_value(val))
            report("C1", "block", "\"" key "\" has a literal value. Use ${VARIABLE_NAME} with no fallback, and set the variable in each environment.")
    }
}

BEGIN {
    PRIVATE_KEY = "-----BEGIN ([A-Z]+ )*PRIVATE KEY-----"
    ACCOUNT_KEY = "AccountKey=" rep("[A-Za-z0-9+/]", 20)
    MAILGUN = rep("[0-9a-f]", 32) "-" rep("[0-9a-f]", 8) "-" rep("[0-9a-f]", 8)
    MAILGUN_OLD = "key-" rep("[0-9a-f]", 32)
}

/^diff --git / { in_header = 1; set_path(""); next }
in_header && /^\+\+\+ / {
    p = substr($0, 5)
    sub(/\t$/, "", p)
    if (p == "/dev/null") p = ""
    else sub(/^b\//, "", p)
    set_path(p)
    next
}
/^@@ / {
    in_header = 0
    if (match($0, /\+[0-9]+/)) lineno = substr($0, RSTART + 1, RLENGTH - 1) + 0
    next
}
in_header { next }
/^\+/ { if (path != "") check(substr($0, 2)); lineno++; next }
/^ / { lineno++; next }

END {
    if (warnings > 50) printf "        ... and %d more warnings not shown\n", warnings - 50
}
AWK

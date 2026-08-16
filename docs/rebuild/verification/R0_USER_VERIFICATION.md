# R0 User Verification

## Scope

These checks verify the R0 documentation move and Manifest audit. They do not validate product runtime behavior.

Run from:

```powershell
Set-Location 'C:\Users\seewo\Desktop\big_proj_01\new_3'
```

## Commands and success criteria

### 1. Confirm repository state

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
```

Success criteria:

- Branch is `rebuild/new-pipeline-v1`.
- The R0 diff contains only documentation moves/edits under `docs/**` plus the user's pre-existing `.gitignore` modification.
- No source, Migration, or dependency file appears in the R0 changes.

### 2. Confirm authority and archive placement

```powershell
Test-Path -LiteralPath 'docs/redesign'
Test-Path -LiteralPath 'docs/archive/conversational-workspace/redesign'
@(Get-ChildItem -LiteralPath 'docs/archive/conversational-workspace/redesign' -Recurse -File).Count
```

Success criteria:

- `docs/redesign` returns `False`.
- The archive path returns `True`.
- The recursive archive file count is `27`.

### 3. Confirm the Manifest schema and categories

```powershell
$manifestRows = Import-Csv -LiteralPath 'docs/rebuild/REPOSITORY_FILE_OPERATION_MANIFEST.csv' -Encoding utf8
$manifestRows[0].PSObject.Properties.Name
$manifestRows | Group-Object classification | Sort-Object Name | Select-Object Name, Count
```

Success criteria:

- Columns are `action`, `classification`, `source`, `target`, `phase`, `condition`, and `rationale`.
- Categories include exactly `ADAPT`, `ARCHIVE_DOC`, `DELETE_IN_R7`, `KEEP`, `REMOVE_ACTIVE_SURFACE_IN_R1`, and `REPLACE`.
- No row has an empty `classification`.

### 4. Check Markdown links and stale archive paths

```powershell
$markdownFiles = Get-ChildItem -LiteralPath 'docs/archive/conversational-workspace','docs/rebuild' -Recurse -File -Filter '*.md'
$brokenLinks = foreach ($file in $markdownFiles) {
  $content = Get-Content -Raw -Encoding utf8 -LiteralPath $file.FullName
  foreach ($match in [regex]::Matches($content, '\[[^\]]+\]\(([^)]+)\)')) {
    $link = $match.Groups[1].Value.Split('#')[0]
    if ($link -and $link -notmatch '^(?:https?://|mailto:|#)' -and -not (Test-Path -LiteralPath (Join-Path $file.DirectoryName $link))) {
      [pscustomobject]@{ File = $file.FullName; Link = $link }
    }
  }
}
$brokenLinks
rg -n 'docs/redesign' docs/archive/conversational-workspace --glob '*.md'
```

Success criteria:

- `$brokenLinks` prints no rows.
- `rg` prints only intentional historical wording, if any; it must not print an actionable path that still points to the removed location.

### 5. Check patch whitespace

```powershell
git diff --check
git diff --cached --check
```

Success criterion: both commands exit with code `0` and print no whitespace errors.

## Browser confirmation

None for R0. No frontend source or route was changed, so browser testing is intentionally deferred to R1 and later stage gates.

## Database initialization

Not required. R0 changes no schema, Migration, seed, or runtime configuration.

## Docker rebuild

No service requires a rebuild for R0.

## Logs to collect on failure

- Full output of `git status --short` and `git diff --check`.
- `git diff -- docs/rebuild docs/archive/conversational-workspace`.
- Any rows printed by `$brokenLinks`.
- Manifest parse or category output from step 3.
- Exact failing path and PowerShell error text.

## Condition to proceed to R1

Proceed only when the archive path and count are correct, Markdown link checking is clean, the Manifest parses with all six required classifications, `git diff --check` passes, and the R0 diff contains no source or Migration changes. Review `docs/rebuild/R1_ACTIVE_SURFACE_REMOVAL_AUDIT.md` before implementing R1.

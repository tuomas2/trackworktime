#!/bin/bash
#
# Sykerö Track Work Time version increment.
#
# Bumps `versionCode N` in app/build.gradle and advances versionName's patch
# component to match (versionName = MAJOR.MINOR.<versionCode>), auto-generates an
# English changelog from git history via `claude -p --model haiku`, commits, tags
# `v<versionName>`, and pushes after confirmation.
#
# Preconditions: clean working tree, `claude` CLI on $PATH.
#
# Usage: make increment-version
#    or: bash scripts/increment-version.sh

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

GRADLE_FILE="app/build.gradle"
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"

echo -e "${GREEN}Sykerö Track Work Time version increment${NC}"
echo "========================================"

if [[ ! -f "$GRADLE_FILE" ]]; then
    echo -e "${RED}Error: $GRADLE_FILE not found. Run from repo root.${NC}"
    exit 1
fi

if ! command -v claude >/dev/null 2>&1; then
    echo -e "${RED}Error: 'claude' CLI not on PATH — required for changelog generation.${NC}"
    exit 1
fi

if [[ -n $(git status --porcelain) ]]; then
    echo -e "${RED}Error: working tree not clean. Commit or stash first.${NC}"
    git status --short
    exit 1
fi

CURRENT=$(awk '/versionCode [0-9]/ { print $2 }' "$GRADLE_FILE")
if [[ -z "$CURRENT" ]]; then
    echo -e "${RED}Error: could not parse versionCode from $GRADLE_FILE${NC}"
    exit 1
fi

CURRENT_NAME=$(awk -F"'" '/versionName / { print $2 }' "$GRADLE_FILE")
MAJOR_MINOR=$(echo "$CURRENT_NAME" | sed -E 's/\.[0-9]+$//')
if [[ -z "$MAJOR_MINOR" || "$MAJOR_MINOR" == "$CURRENT_NAME" ]]; then
    echo -e "${RED}Error: could not parse MAJOR.MINOR from versionName '$CURRENT_NAME' (expected X.Y.Z).${NC}"
    exit 1
fi

NEW=$((CURRENT + 1))
NEW_NAME="${MAJOR_MINOR}.${NEW}"
echo "Current: versionCode $CURRENT, versionName $CURRENT_NAME"
echo "New:     versionCode $NEW, versionName $NEW_NAME"

NEW_CHANGELOG="$CHANGELOG_DIR/${NEW}.txt"
if [[ -f "$NEW_CHANGELOG" ]]; then
    echo -e "${RED}Error: $NEW_CHANGELOG already exists. Aborting to avoid clobber.${NC}"
    exit 1
fi

echo "Updating $GRADLE_FILE..."
sed -i.bak "s/versionCode $CURRENT/versionCode $NEW/" "$GRADLE_FILE"
sed -i.bak "s/versionName '$CURRENT_NAME'/versionName '$NEW_NAME'/" "$GRADLE_FILE"
rm -f "${GRADLE_FILE}.bak"

CHECK=$(awk '/versionCode [0-9]/ { print $2 }' "$GRADLE_FILE")
CHECK_NAME=$(awk -F"'" '/versionName / { print $2 }' "$GRADLE_FILE")
if [[ "$CHECK" != "$NEW" || "$CHECK_NAME" != "$NEW_NAME" ]]; then
    echo -e "${RED}Error: sed substitution did not take (got versionCode $CHECK, versionName $CHECK_NAME).${NC}"
    git checkout -- "$GRADLE_FILE"
    exit 1
fi
echo -e "${GREEN}✓ $GRADLE_FILE updated${NC}"

PREV_TAG=$(git describe --tags --match 'v*' --abbrev=0 HEAD 2>/dev/null || echo "")
if [[ -n "$PREV_TAG" ]]; then
    echo "Previous tag: $PREV_TAG"
    GIT_LOG=$(git log "$PREV_TAG"..HEAD --no-merges --format="%h %s%w(0,4,4)%+b")
else
    echo -e "${YELLOW}Warning: no previous v* tag found — generating from full history.${NC}"
    GIT_LOG=$(git log HEAD --no-merges --format="%h %s%w(0,4,4)%+b")
fi

if [[ -z "$GIT_LOG" ]]; then
    echo -e "${RED}Error: no commits to summarize.${NC}"
    git checkout -- "$GRADLE_FILE"
    exit 1
fi

echo "Generating changelog from git history via claude haiku..."
GENERATED=$(echo "$GIT_LOG" | claude -p --model haiku --tools "" -- \
    "Generate a changelog summary from these git commits for an Android time-tracking app.
Output ONLY a bulleted list (• item) of user-facing changes.
Group related commits into single items. Skip version increment commits, dependency bumps, CI/docs-only changes, CLAUDE.md/README changes, screenshot regeneration, and translation-only commits.
If a commit references an issue (#NNN), append it in parentheses. Otherwise append the short commit hash (e.g. (abc1234)).
Keep items concise (one line each). Write in English.
Order: new features first, then improvements, then bug fixes." 2>/dev/null) || true

if [[ -z "$GENERATED" ]]; then
    echo -e "${RED}Error: claude returned empty changelog. Aborting and reverting.${NC}"
    git checkout -- "$GRADLE_FILE"
    exit 1
fi

mkdir -p "$CHANGELOG_DIR"
printf '%s\n' "$GENERATED" > "$NEW_CHANGELOG"
echo -e "${GREEN}✓ Changelog written to $NEW_CHANGELOG${NC}"

echo ""
echo -e "${YELLOW}New changelog content:${NC}"
echo "=========================="
cat "$NEW_CHANGELOG"
echo "=========================="
echo ""

echo -e "${YELLOW}Proceed? (y=yes / e=edit / n=abort)${NC}"
read -r response
if [[ "$response" =~ ^[Ee]$ ]]; then
    "${EDITOR:-nano}" "$NEW_CHANGELOG"
    echo ""
    echo -e "${YELLOW}Updated changelog:${NC}"
    echo "=========================="
    cat "$NEW_CHANGELOG"
    echo "=========================="
    echo ""
    echo -e "${YELLOW}Proceed? (y/n)${NC}"
    read -r response
fi
if [[ ! "$response" =~ ^[Yy]$ ]]; then
    echo "Aborted. Reverting..."
    git checkout -- "$GRADLE_FILE"
    rm -f "$NEW_CHANGELOG"
    exit 1
fi

echo "Staging files..."
git add "$GRADLE_FILE" "$NEW_CHANGELOG"

COMMIT_MESSAGE="Bump version to $NEW_NAME (versionCode $NEW)"
echo "Committing: $COMMIT_MESSAGE"
git commit -m "$COMMIT_MESSAGE"

TAG_NAME="v$NEW_NAME"
echo "Tagging: $TAG_NAME"
git tag "$TAG_NAME"

echo -e "${GREEN}✓ Commit and tag created${NC}"

echo ""
echo -e "${YELLOW}Push commit and tag to origin? (y/n)${NC}"
read -r push_response
if [[ "$push_response" =~ ^[Yy]$ ]]; then
    echo "Pushing commit..."
    git push origin HEAD
    echo "Pushing tag..."
    git push origin "$TAG_NAME"
    echo -e "${GREEN}✓ Pushed to origin${NC}"
else
    echo -e "${YELLOW}Not pushed. To push later:${NC}"
    echo "  git push origin HEAD"
    echo "  git push origin $TAG_NAME"
fi

echo ""
echo -e "${GREEN}Done.${NC}"
echo "  versionCode: $CURRENT → $NEW"
echo "  versionName: $CURRENT_NAME → $NEW_NAME"
echo "  changelog:   $NEW_CHANGELOG"
echo "  tag:         $TAG_NAME"

#!/bin/sh
# Sync this repository's Git commit identity from the active GitHub CLI account.
#
# Git itself does not read `gh auth switch`. This script is called by the
# pre-commit hook so author/committer config follows the currently active
# `gh` account before the commit object is created.

set -e

if [ "${MEDLOG_SYNC_GH_IDENTITY:-1}" = "0" ]; then
    exit 0
fi

if [ "$(git config --bool hooks.medlogSyncGhIdentity 2>/dev/null || echo true)" = "false" ]; then
    exit 0
fi

if ! command -v gh >/dev/null 2>&1; then
    echo "[identity] GitHub CLI is required to sync commit identity." >&2
    echo "[identity] Install gh or disable with: git config hooks.medlogSyncGhIdentity false" >&2
    exit 1
fi

ACTIVE_ACCOUNT="$(gh api user --jq .login 2>/dev/null || true)"

if [ -z "$ACTIVE_ACCOUNT" ]; then
    ACTIVE_ACCOUNT="$(gh auth status 2>&1 | awk '
        /Logged in to github.com account / { account = $7 }
        /Active account: true/ { print account; exit }
    ')"
fi

case "$ACTIVE_ACCOUNT" in
    Tinnci)
        GIT_NAME="Tinnci"
        GIT_EMAIL="23432137+Tinnci@users.noreply.github.com"
        TEMPLATE="$HOME/.gitmessage-tinnci"
        ;;
    shisoratsu)
        GIT_NAME="shisoratsu"
        GIT_EMAIL="277485761+shisoratsu@users.noreply.github.com"
        TEMPLATE="$HOME/.gitmessage-shisoratsu"
        ;;
    "")
        echo "[identity] No active GitHub CLI account found." >&2
        echo "[identity] Run: gh auth status" >&2
        exit 1
        ;;
    *)
        echo "[identity] Unknown active GitHub CLI account: $ACTIVE_ACCOUNT" >&2
        echo "[identity] Add this account to scripts/sync_git_identity_from_gh.sh before committing." >&2
        exit 1
        ;;
esac

git config user.name "$GIT_NAME"
git config user.email "$GIT_EMAIL"
git config user.useConfigOnly true

if [ -f "$TEMPLATE" ]; then
    git config commit.template "$TEMPLATE"
else
    git config --unset commit.template 2>/dev/null || true
fi

echo "[identity] commit identity: $GIT_NAME <$GIT_EMAIL> from gh active account '$ACTIVE_ACCOUNT'"

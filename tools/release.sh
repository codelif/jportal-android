#!/usr/bin/env bash
# makes the signed tag for a release and stops, pushing it is what starts the build.
# usage: tools/release.sh 0.2.0, notes come from release-notes.md or else $EDITOR
set -euo pipefail
die() { echo "release: $*" >&2; exit 1; }

v=${1:?usage: tools/release.sh <x.y.z>}
v=${v#v}
tag=v$v
cd "$(git rev-parse --show-toplevel)"

[[ $v =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || die "$v isn't x.y.z"
[[ $(git branch --show-current) == main ]] || die "not on main"
[[ -z $(git status --porcelain) ]] || die "uncommitted changes"
[[ -z $(git -C ktjiit status --porcelain) ]] || die "uncommitted changes in ktjiit"
git rev-parse -q --verify "refs/tags/$tag" > /dev/null && die "$tag already exists"

last=$(git tag -l 'v*' --sort=-v:refname | head -1)
if [[ -n $last ]]; then
    [[ $(printf '%s\n' "$last" "$tag" | sort -V | tail -1) == "$tag" ]] || die "$tag isn't newer than $last"
fi

# ci checks these too, failing here is cheaper than failing there
git verify-commit HEAD 2> /dev/null || die "HEAD isn't signed"
git -C ktjiit verify-commit HEAD 2> /dev/null || die "the pinned ktjiit commit isn't signed"
git -C ktjiit fetch -q https://github.com/codelif/ktjiit.git main
git -C ktjiit merge-base --is-ancestor HEAD FETCH_HEAD || die "the pinned ktjiit commit isn't on github yet, push ktjiit first"

SCISSORS='# ------------------------ >8 ------------------------'
notes=
[[ -f release-notes.md ]] && notes=$(< release-notes.md)
if [[ -z ${notes//[[:space:]]/} ]]; then
    draft=$(mktemp --suffix=.md)
    trap 'rm -f "$draft"' EXIT
    # git's scissors: everything from that line down is dropped, so markdown headings survive
    {
        echo
        echo "$SCISSORS"
        echo "notes for $tag go above the line, in markdown. leave it empty to call off the release"
        echo "commits since ${last:-the start}:"
        git log --format='- %s' ${last:+"$last"..}HEAD
    } > "$draft"
    ${VISUAL:-${EDITOR:-vi}} "$draft"
    notes=$(sed "/^$SCISSORS\$/,\$d" "$draft" | sed '/./,$!d')
    [[ -n ${notes//[[:space:]]/} ]] || die "no notes, no release"
fi
# git strips # lines from tag messages by default, which would eat the headings
git tag -s --cleanup=whitespace "$tag" -m "$tag" -m "$notes"
git verify-tag "$tag" 2> /dev/null || die "the tag came out unsigned"

echo "tagged $tag${last:+ (last was $last)}, ship it with: git push origin main $tag"

#!/usr/bin/env bash
# cuts a release and stops, pushing it is what starts the build. a release gets its version and its
# notes written into the tree and committed, then the signed tag. a test round (0.2.0-rc.1) only gets the tag.
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

last=$(git -c versionsort.suffix=- tag -l 'v*' --sort=-v:refname | head -1)
if [[ -n $last ]]; then
    if [[ $tag != *-* && $last == "$tag"-* ]]; then
        : # A stable release is newer than its prereleases.
    else
        [[ $(printf '%s\n' "$last" "$tag" | sort -V | tail -1) == "$tag" ]] || die "$tag isn't newer than $last"
    fi
fi

# ci checks these too, failing here is cheaper than failing there
git verify-commit HEAD 2> /dev/null || die "HEAD isn't signed"
git -C ktjiit verify-commit HEAD 2> /dev/null || die "the pinned ktjiit commit isn't signed"
git -C ktjiit fetch -q https://github.com/codelif/ktjiit.git main
git -C ktjiit merge-base --is-ancestor HEAD FETCH_HEAD || die "the pinned ktjiit commit isn't on github yet, push ktjiit first"

# the notes are whatever gets written, nothing is added to them. markdown is fine
notes=
[[ -f release-notes.md ]] && notes=$(< release-notes.md)
if [[ -z ${notes//[[:space:]]/} ]]; then
    draft=$(mktemp --suffix=.md)
    trap 'rm -f "$draft"' EXIT
    ${VISUAL:-${EDITOR:-vi}} "$draft"
    notes=$(< "$draft")
fi
notes=$(sed '/[^[:space:]]/,$!d' <<< "$notes")
[[ -n $notes ]] || die "no notes, no release"

if [[ $tag != *-* ]]; then
    gradle=app/build.gradle.kts
    IFS=. read -r major minor patch <<< "$v"
    code=$(( ((10#$major * 100 + 10#$minor) * 100 + 10#$patch) * 100 + 99 ))
    log=fastlane/metadata/android/en-US/changelogs/$code.txt

    # f-droid and play show plain text, 500 characters at most
    plain=$(sed -E 's/^#+ *//' <<< "$notes")
    if (( ${#plain} > 500 )); then
        printf '%s\n' "$notes" > release-notes.md
        die "the notes are ${#plain} characters and the stores take 500. they're in release-notes.md, trim them and run this again"
    fi

    # f-droid reads these two literals out of the build file, ci fails a tag they don't match
    [[ $(grep -cE '^ *versionCode = [0-9]+$' "$gradle") == 1 && $(grep -cE '^ *versionName = "[^"]+"$' "$gradle") == 1 ]] \
        || die "can't find the versionCode and versionName literals in $gradle"
    sed -E -i "s/^( *versionCode = )[0-9]+\$/\\1$code/; s/^( *versionName = )\"[^\"]+\"\$/\\1\"$v\"/" "$gradle"
    printf '%s\n' "$plain" > "$log"

    git add "$gradle" "$log"
    # nothing staged means the tree already said all this, HEAD is the release as it stands
    if ! git diff --cached --quiet; then
        # the tree was clean going in, so a hard reset only takes back what was just written
        git commit -q -S -m "chore(release): $tag" || { git reset -q --hard; die "the release commit failed, nothing was changed"; }
    fi
fi

# git strips # lines from tag messages by default, which would eat the headings
git tag -s --cleanup=whitespace "$tag" -m "$tag" -m "$notes"
git verify-tag "$tag" 2> /dev/null || die "the tag came out unsigned"
rm -f release-notes.md

echo "tagged $tag${last:+ (last was $last)}, ship it with: git push origin main $tag"

#!/bin/bash

set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
project_dir="$(cd "$script_dir/.." && pwd)"
output_dir="${1:-$project_dir/../IPA}"
derived_dir="$(mktemp -d /tmp/punctum-unsigned-derived.XXXXXX)"
package_dir="$(mktemp -d /tmp/punctum-unsigned-package.XXXXXX)"

cleanup() {
    rm -rf "$derived_dir" "$package_dir"
}
trap cleanup EXIT

export DEVELOPER_DIR="/Applications/Xcode.app/Contents/Developer"

xcodebuild \
    -project "$project_dir/Punctum.xcodeproj" \
    -scheme Punctum \
    -configuration Release \
    -destination "generic/platform=iOS" \
    -derivedDataPath "$derived_dir" \
    CODE_SIGNING_ALLOWED=NO \
    CODE_SIGNING_REQUIRED=NO \
    ASSETCATALOG_COMPILER_APPICON_NAME= \
    EXCLUDED_SOURCE_FILE_NAMES=Assets.xcassets \
    build

source_app="$derived_dir/Build/Products/Release-iphoneos/Punctum.app"
payload_dir="$package_dir/Payload"
packaged_app="$payload_dir/Punctum.app"
mkdir -p "$payload_dir" "$output_dir"
ditto "$source_app" "$packaged_app"

icon_source="$project_dir/Punctum/Assets.xcassets/AppIcon.appiconset/Punctum-AppIcon-1024.png"
sips -z 120 120 "$icon_source" --out "$packaged_app/PunctumIcon60@2x.png" >/dev/null
sips -z 180 180 "$icon_source" --out "$packaged_app/PunctumIcon60@3x.png" >/dev/null
cp "$icon_source" "$packaged_app/PunctumIcon1024.png"

info_plist="$packaged_app/Info.plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIcons dict" "$info_plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIcons:CFBundlePrimaryIcon dict" "$info_plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconFiles array" "$info_plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIcons:CFBundlePrimaryIcon:CFBundleIconFiles:0 string PunctumIcon60" "$info_plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIconFiles array" "$info_plist"
/usr/libexec/PlistBuddy -c "Add :CFBundleIconFiles:0 string PunctumIcon60" "$info_plist"

version="$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$info_plist")"
output_path="$output_dir/Punctum-$version-unsigned.ipa"
rm -f "$output_path"
(
    cd "$package_dir"
    ditto -c -k --keepParent Payload "$output_path"
)

echo "$output_path"

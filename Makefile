AAB := app/build/outputs/bundle/release/app-release.aab

# Studio JBR (JDK 21) — required for PebbleKit Android 2 (Java-21 bytecode); the
# default shell uses corretto-17. Auto-detected across environments: the gie
# sandbox mounts JetBrains Toolbox at /opt/jetbrains-toolbox, while on the host it
# lives under ~/.local/share/JetBrains/Toolbox. The first existing path below wins;
# an exported JAVA_HOME or `make … JAVA_HOME=/path` overrides it.
JAVA_HOME ?= $(firstword $(wildcard \
    /opt/jetbrains-toolbox/apps/android-studio/jbr \
    $(HOME)/.local/share/JetBrains/Toolbox/apps/android-studio/jbr \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-21-openjdk))

# Current versionCode parsed from app/build.gradle so promote/upload don't need it
# passed in. Override with `VERSION=<n>` to act on a different build.
CURRENT_VERSION := $(shell awk '/versionCode [0-9]/ { print $$2 }' app/build.gradle)

# GPG-encrypted Play Store service-account key. Committed encrypted; decrypted
# in-memory only — never written to disk. Injected as SUPPLY_JSON_KEY_DATA.
PLAY_KEY_GPG := fastlane/sykero-software-682627ee0658.json.gpg

_require-key:
	@test -f "$(PLAY_KEY_GPG)" || { echo "Missing $(PLAY_KEY_GPG)"; exit 1; }

# Bump version, auto-generate changelog, commit, tag, push (after confirmation).
increment-version:
	bash scripts/increment-version.sh

# Build a signed release AAB (uses keystore.properties.gpg for signing secrets).
bundle:
	@echo "Building release AAB (signing via keystore.properties.gpg)..."
	JAVA_HOME=$(JAVA_HOME) ./gradlew bundleRelease
	@echo "✓ AAB: $(AAB)"

# Build a signed release APK for direct sideloading / sharing (the AAB above is
# for Play upload; this APK is directly installable with `adb install`). Same
# signing path as `bundle` (keystore.properties.gpg). Uses the JDK-21 JAVA_HOME
# above — run this via `make`, NOT a bare `./gradlew assembleRelease`, or the
# ambient JDK 17 fails on PebbleKit Android 2's Java-21 bytecode. The output
# filename carries the version + build time, so glob for the newest one.
release-apk:
	@echo "Building release APK (signing via keystore.properties.gpg)..."
	JAVA_HOME=$(JAVA_HOME) ./gradlew :app:assembleRelease
	@apk=$$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1); \
	test -n "$$apk" || { echo "No APK produced under app/build/outputs/apk/release/"; exit 1; }; \
	echo "✓ APK: $$apk"; \
	case "$$apk" in *-unsigned.apk) echo "  NOTE: UNSIGNED — keystore.properties.gpg not found/decryptable (release signing keys are host-only).";; esac

# Upload an already-built AAB + en-US changelog for the current versionCode.
# Does NOT rebuild — run `make bundle` first. Split from deploy so the two YubiKey
# touches (keystore sign + gpg decrypt) don't race the touch window.
upload: _require-key
	@test -f "$(AAB)" || { echo "AAB not found at $(AAB). Run 'make bundle' first."; exit 1; }
	@if [ ! -f fastlane/metadata/android/en-US/changelogs/$(CURRENT_VERSION).txt ]; then \
		echo "Warning: no changelog at fastlane/metadata/android/en-US/changelogs/$(CURRENT_VERSION).txt — Play Store 'What's new' will be empty."; \
	fi
	SUPPLY_JSON_KEY_DATA="$$(gpg --decrypt --quiet $(PLAY_KEY_GPG))" \
	SUPPLY_AAB=$(AAB) \
	SUPPLY_SKIP_UPLOAD_AAB=false \
	SUPPLY_SKIP_UPLOAD_CHANGELOGS=false \
	fastlane supply

# Convenience: bundle + upload (default track: internal per .env.default).
deploy: bundle upload

# Update store-listing metadata only (titles/descriptions). Skips binaries etc.
fastlane-supply: _require-key
	SUPPLY_JSON_KEY_DATA="$$(gpg --decrypt --quiet $(PLAY_KEY_GPG))" \
	fastlane supply

# Dry-run: validate metadata + credentials without writing anything.
fastlane-validate: _require-key
	SUPPLY_JSON_KEY_DATA="$$(gpg --decrypt --quiet $(PLAY_KEY_GPG))" \
	fastlane supply --validate_only true

# Verify the decrypted key authenticates against the Play Developer API.
# Uses a temporary fd (/dev/fd/3) so the plaintext never hits disk.
fastlane-auth-check: _require-key
	@exec 3< <(gpg --decrypt --quiet $(PLAY_KEY_GPG)); \
	fastlane run validate_play_store_json_key json_key:/dev/fd/3; \
	exec 3<&-

# Promote an existing release between tracks without re-uploading the AAB.
# Defaults to the versionCode in app/build.gradle; override with VERSION=<n>.
# Override tracks: `FROM=alpha TO=beta make promote`.
VERSION ?= $(CURRENT_VERSION)
FROM    ?= internal
TO      ?= production
promote: _require-key
	@test -n "$(VERSION)" || { echo "Could not determine versionCode from app/build.gradle. Pass VERSION=<n>."; exit 1; }
	@echo "Promoting versionCode $(VERSION): $(FROM) → $(TO)"
	SUPPLY_JSON_KEY_DATA="$$(gpg --decrypt --quiet $(PLAY_KEY_GPG))" \
	SUPPLY_TRACK=$(FROM) \
	SUPPLY_SKIP_UPLOAD_METADATA=true \
	fastlane supply \
	  --track_promote_to $(TO) \
	  --version_code $(VERSION)

.PHONY: _require-key increment-version bundle release-apk upload deploy fastlane-supply fastlane-validate fastlane-auth-check promote

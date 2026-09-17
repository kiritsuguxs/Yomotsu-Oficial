import re

file_path = "/workspace/Yomotsu-Oficial/.github/workflows/build.yml"
with open(file_path, "r") as f:
    content = f.read()

# Swap the order of "Prepare Yomotsu branding" and "Run unit tests"
parts = content.split("      - name: Run unit tests")
before = parts[0]
after = parts[1]

# Extract Prepare Yomotsu branding block
branding_block = """      - name: Prepare Yomotsu branding
        run: |
          base64 --decode branding/yomotsu-icon.png.b64 > "$RUNNER_TEMP/yomotsu-icon.png"
          for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
            mkdir -p "app/src/main/res/mipmap-$density"
            cp "$RUNNER_TEMP/yomotsu-icon.png" "app/src/main/res/mipmap-$density/ic_launcher.png"
            cp "$RUNNER_TEMP/yomotsu-icon.png" "app/src/main/res/mipmap-$density/ic_launcher_foreground.png"
          done
"""

unit_tests_block = """      - name: Run unit tests
        id: unit_tests
        run: ./gradlew testDebugUnitTest

      - name: Verify SQLDelight migrations
        run: ./gradlew verifySqlDelightMigration

      - name: Upload test report
        if: steps.unit_tests.outcome == 'failure'
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: test-report-${{ github.sha }}
          path: app/build/reports/tests/testDebugUnitTest"""

content = content.replace(unit_tests_block, "")
content = content.replace(branding_block, branding_block + "\n" + unit_tests_block + "\n")

with open(file_path, "w") as f:
    f.write(content)

print("Fixed build.yml order!")

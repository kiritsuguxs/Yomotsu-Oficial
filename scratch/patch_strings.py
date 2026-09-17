import re

file_path = "/workspace/Yomotsu-Oficial/i18n/src/commonMain/moko-resources/base/strings.xml"
with open(file_path, "r") as f:
    content = f.read()

strings = """    <string name="theme_yotsuba">Yotsuba</string>
    <string name="theme_onyx">Ônix</string>
    <string name="theme_neon">Neon</string>
    <string name="theme_twilight">Crepúsculo</string>
    <string name="theme_orchid">Orquídea</string>
    <string name="theme_autumn">Outono</string>"""

content = content.replace('    <string name="theme_yotsuba">Yotsuba</string>', strings)

with open(file_path, "w") as f:
    f.write(content)

print("Patched strings.xml!")

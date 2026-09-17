with open('YOMOTSU_CHANGELOG.md', 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_entry = """## 0.20.4-Y33
- **Novo Domínio Oficial:** Todos os links internos do aplicativo (Ajuda, Privacidade, Guias) foram atualizados para apontar para o novo site profissional: yomotsu-app.github.io.

"""

output = []
for line in lines:
    if line.startswith("## 0.20.4-Y32"):
        output.append(new_entry)
        output.append(line)
    else:
        output.append(line)

with open('YOMOTSU_CHANGELOG.md', 'w', encoding='utf-8') as f:
    f.writelines(output)

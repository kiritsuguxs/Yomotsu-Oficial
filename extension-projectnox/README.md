# 🌙 Project Nox - Extensão Mihon/Yomotsu

Extensão para leitura de mangás, manhwas e webtoons da plataforma **[Project Nox](https://manga.project-nox-awerkori.workers.dev/)** no aplicativo **Mihon**, **Yomotsu** ou qualquer fork compatível do Tachiyomi.

## ✨ Funcionalidades

| Funcionalidade | Suporte |
|---|---|
| 📖 Catálogo completo | ✅ |
| 🔍 Busca por título | ✅ |
| 🏷️ Filtro por formato | ✅ Manhwa, Manga, Manhua |
| 📊 Filtro por status | ✅ Em andamento, Completo, Hiato, Cancelado |
| 🎯 Filtro por gêneros | ✅ 26 gêneros/tags |
| 📑 Ordenação | ✅ Recentes, Populares, Avaliação, A-Z, Z-A |
| 🔗 Deep links | ✅ Abrir links do site no app |
| 🌐 Idioma | Português (pt-BR) |

## 📱 Instalação

1. Baixe o APK mais recente na [aba Releases](../../releases)
2. Instale no seu dispositivo Android
3. Abra o Mihon/Yomotsu → Explorar → A extensão "Project Nox" aparecerá automaticamente

## 🔧 Como funciona

O site Project Nox é construído com **SvelteKit** e usa rendering server-side. A extensão consome os endpoints internos `__data.json` do SvelteKit para obter os dados estruturados de:

- **Catálogo:** `/catalogo/__data.json` com parâmetros de busca, filtros e paginação
- **Detalhes da obra:** `/obra/{slug}/__data.json` com informações completas e lista de capítulos
- **Páginas do capítulo:** `/ler/{id}/__data.json` com URLs das imagens via `/media/{media_id}`

## 🏗️ Estrutura do Projeto

```
extension-projectnox/
├── build.gradle.kts                 # Configuração do Gradle
├── src/main/
│   ├── AndroidManifest.xml          # Manifest com deep link handlers
│   └── java/.../projectnox/
│       ├── ProjectNox.kt            # Classe principal da extensão
│       ├── Filters.kt               # Filtros de busca
│       └── ProjectNoxUrlActivity.kt # Handler de deep links
└── .github/workflows/
    └── build-extension.yml          # CI/CD para build do APK
```

## ⚙️ Requisitos

- **Android 5.0+** (API 21)
- **Mihon**, **Yomotsu**, **Tachiyomi** ou fork compatível

## 📋 Notas Técnicas

- A extensão decodifica o formato compacto de dados do SvelteKit onde objetos referenciam valores por índice em um array flat
- As imagens são servidas via `/media/{media_id}` usando IDs UUID
- Os capítulos são identificados por UUID e as páginas incluem posição, media_id, largura e altura
- Suporta todos os parâmetros de busca do catálogo: `q`, `tag`, `tipo`, `status`, `ordem`, `pagina`

## 📄 Licença

Este projeto segue a mesma licença do repositório principal.

---
name: mihon-extensions
description: >-
  Guia completo para criar, corrigir, configurar e compilar extensões para o aplicativo Yomotsu/Mihon/Tachiyomi.
  Use esta skill sempre que o usuário pedir para criar uma nova extensão, corrigir uma existente, atualizar seletores de raspagem ou configurar o fluxo de CI/CD para compilar APKs de extensão.
---

# Guia de Desenvolvimento de Extensões Yomotsu / Mihon

Este guia contém todos os padrões, requisitos obrigatórios, nova política de versionCode do Keiyoushi, modelo 1.6 e soluções de problemas conhecidos para desenvolvimento e compilação de extensões.

---

## 1. Requisitos Obrigatórios do `AndroidManifest.xml` (Padrão 1.6 Moderno)

Para que o Yomotsu/Mihon reconheça o APK instalado como uma extensão compatível com as regras modernas:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ⚠️ OBRIGATÓRIO: Sem isso a extensão fica invisível no app -->
    <uses-feature android:name="tachiyomi.extension" />

    <application
        android:label="${appName}"
        android:icon="@mipmap/ic_launcher">

        <!-- Classe principal da fonte (relativa ou completa) -->
        <meta-data
            android:name="tachiyomi.extension.class"
            android:value="${extClass}" />

        <!-- Deixar vazio se usar classe única, ou nome da fábrica -->
        <meta-data
            android:name="tachiyomi.extension.factory"
            android:value="${extFactory}" />

        <!-- 0 para livre, 1 para conteúdo adulto -->
        <meta-data
            android:name="tachiyomi.extension.nsfw"
            android:value="${nsfw}" />

        <!-- Versão da lib suportada pelo Yomotsu (ex: 1.4 ou 1.6) -->
        <meta-data
            android:name="tachiyomix.extensionLib"
            android:value="${libVersion}" />

        <!-- Nome amigável da fonte no app -->
        <meta-data
            android:name="tachiyomix.name"
            android:value="NomeDaExtensao" />

        <!-- Aviso de conteúdo: 0 = Livre (Safe), 1 = Misto (Mixed), 2 = Adulto (NSFW) -->
        <meta-data
            android:name="tachiyomix.contentWarning"
            android:value="2" />

        <!-- Activity de Deep Link para abrir URLs no app -->
        <activity
            android:name=".DeepLinkActivity"
            android:excludeFromRecents="true"
            android:exported="true"
            android:theme="@android:style/Theme.NoDisplay">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data
                    android:host="exemplo.com"
                    android:pathPattern="/obra/..*"
                    android:scheme="https" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

---

## 2. Nova Política de `versionCode` do Keiyoushi e `build.gradle.kts`

O Keiyoushi calcula o `versionCode` para o Android combinando a versão da lib e o número de revisão da extensão.

### Fórmula:
```kotlin
val extVersionCode = 3
val libVersion = "1.6"

val calculatedVersionCode = libVersion.split(".")
    .joinToString("") { it.padStart(2, '0') }
    .toInt() * 1000 + extVersionCode
// Para libVersion = "1.6" e extVersionCode = 3:
// "01" + "06" -> 106 * 1000 + 3 = 106003
```

### Template Oficial de `build.gradle.kts` para Extensões Standalone:
```kotlin
plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

val extVersionCode = 3
val libVersion = "1.6"

val calculatedVersionCode = libVersion.split(".")
    .joinToString("") { it.padStart(2, '0') }
    .toInt() * 1000 + extVersionCode

android {
    namespace = "eu.kanade.tachiyomi.extension.<idioma>.<nome>"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.<idioma>.<nome>"
        minSdk = 21
        targetSdk = 34
        versionCode = calculatedVersionCode
        versionName = "$libVersion.$extVersionCode"

        manifestPlaceholders["appName"] = "Tachiyomi: NomeDaExtensao"
        manifestPlaceholders["extClass"] = ".NomeDaClasse"
        manifestPlaceholders["extVersionCode"] = extVersionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "1"
        manifestPlaceholders["sourceUrl"] = ""
        manifestPlaceholders["isNsfw"] = "true"
        manifestPlaceholders["libVersion"] = libVersion
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

---

## 3. Template do Código da Extensão (`HttpSource` Direto)

⚠️ **Evite `ParsedHttpSource`**: Essa classe foi descontinuada e removida em versões recentes. Herde diretamente de `HttpSource()` e use Jsoup dentro dos métodos de parse:

```kotlin
package eu.kanade.tachiyomi.extension.<idioma>.<nome>

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

class NomeDaFonte : HttpSource() {

    override val name = "Nome Da Fonte"
    override val baseUrl = "https://exemplo.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // 1. Obras Populares
    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) GET("$baseUrl/populares/", headers) else GET("$baseUrl/populares/page/$page/", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.card-obra").map { el ->
            SManga.create().apply {
                val link = el.selectFirst("a")!!
                setUrlWithoutDomain(link.attr("href"))
                title = el.selectFirst(".titulo")?.text()?.trim().orEmpty()
                thumbnail_url = el.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a.proxima-pagina") != null
        return MangasPage(mangas, hasNextPage)
    }

    // 2. Últimas Atualizações
    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) GET("$baseUrl/ultimos/", headers) else GET("$baseUrl/ultimos/page/$page/", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // 3. Busca
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (page == 1) GET("$baseUrl/?s=$query", headers) else GET("$baseUrl/page/$page/?s=$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // 4. Detalhes da Obra
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.titulo")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst("div.capa img")?.attr("abs:src")
            description = document.selectFirst("div.sinopse")?.text()?.trim()
            author = document.selectFirst(".autor")?.text()?.trim()
            artist = author
            status = SManga.COMPLETED
        }
    }

    // 5. Lista de Capítulos
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("ul.capitulos li a").map { el ->
            SChapter.create().apply {
                name = el.text().trim()
                setUrlWithoutDomain(el.attr("href"))
                date_upload = System.currentTimeMillis()
            }
        }
    }

    // 6. Lista de Páginas do Leitor
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div.leitor img")
        return images.mapIndexed { index, img ->
            val url = img.attr("data-src").ifEmpty { img.attr("abs:src") }
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
```

---

## 4. Padronização de Ícones (`@mipmap/ic_launcher`)

Para garantir que o ícone fique perfeito, nítido e no esquadro no app Yomotsu em qualquer modelo de smartphone:

1. **Evitar `favicon.ico` pequeno**: Nunca baixe diretamente o `favicon.ico` da raiz do site sem validar, pois muitos são 16x16 px esticados ou páginas de bloqueio HTML. Sempre busque o logotipo real do cabeçalho do site.
2. **Centralização e Margens**: O logotipo deve ficar perfeitamente centralizado nos eixos X e Y no canvas com uma margem de segurança de ~15% a 20% para não ser cortado pelas máscaras circulares ou de squircle do Android.
3. **Todas as Densidades Obrigatórias**:
   Gere sempre os arquivos PNG em todas as 5 pastas de recursos `src/main/res/`:
   - `mipmap-mdpi/ic_launcher.png` (48x48 px)
   - `mipmap-hdpi/ic_launcher.png` (72x72 px)
   - `mipmap-xhdpi/ic_launcher.png` (96x96 px)
   - `mipmap-xxhdpi/ic_launcher.png` (144x144 px)
   - `mipmap-xxxhdpi/ic_launcher.png` (192x192 px)

### Script Gerador de Ícones em Python:
```python
from PIL import Image
import os

im = Image.open("logo_oficial.png").convert("RGBA")
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}
bg_color = (33, 43, 54, 255) # Cor de fundo do tema da fonte
base_res = "src/main/res"

for folder, size in densities.items():
    path = os.path.join(base_res, folder)
    os.makedirs(path, exist_ok=True)
    canvas = Image.new("RGBA", (size, size), bg_color)
    target_w = int(size * 0.78)
    scale = target_w / im.width
    target_h = int(im.height * scale)
    scaled = im.resize((target_w, target_h), Image.Resampling.LANCZOS)
    canvas.paste(scaled, ((size - target_w) // 2, (size - target_h) // 2), scaled)
    canvas.save(os.path.join(path, "ic_launcher.png"), format="PNG")
```

---

## 5. ProGuard / R8 Obrigatório (`proguard-rules.pro`)

```pro
-keep class eu.kanade.tachiyomi.extension.** { *; }
-dontwarn rx.**
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
-dontwarn eu.kanade.tachiyomi.**
```

---

## 6. Regras de CI/CD (GitHub Actions)

1. **Evitar GitHub Releases para Extensões**: O Yomotsu verifica Releases para atualização do app principal. Gerar Releases para extensões no mesmo repositório confunde o atualizador. Use sempre `actions/upload-artifact@v4`.
2. **Ambiente PRoot/AndCode**: No ambiente mobile/PRoot, não execute comandos de espera longa (`sleep`) bloqueando a sessão em segundo plano, pois o sistema operacional Android pode reiniciar o container por gerenciamento de bateria.

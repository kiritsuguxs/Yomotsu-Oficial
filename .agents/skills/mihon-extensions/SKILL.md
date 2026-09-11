---
name: mihon-extensions
description: >-
  Guia completo para criar, corrigir, configurar e compilar extensões para o aplicativo Yomotsu/Mihon/Tachiyomi.
  Use esta skill sempre que o usuário pedir para criar uma nova extensão, corrigir uma existente, atualizar seletores de raspagem ou configurar o fluxo de CI/CD para compilar APKs de extensão.
---

# Guia de Desenvolvimento de Extensões Yomotsu / Mihon

Este guia contém todos os padrões, requisitos obrigatórios e soluções de problemas conhecidos para desenvolvimento e compilação de extensões.

---

## 1. Requisitos Obrigatórios do AndroidManifest.xml

Para que o Yomotsu/Mihon reconheça o APK instalado como uma extensão, **todos** os itens abaixo são mandatórios:

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

        <!-- Versão da lib suportada pelo Yomotsu (1.4 ou 1.6) -->
        <meta-data
            android:name="tachiyomix.extensionLib"
            android:value="${libVersion}" />

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

## 2. Configuração do Gradle (`build.gradle.kts`)

```kotlin
plugins {
    id("com.android.application") version "8.2.2"
    kotlin("android") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.<idioma>.<nome>"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.<idioma>.<nome>"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.4.1"

        manifestPlaceholders["appName"] = "Tachiyomi: NomeDaFonte"
        manifestPlaceholders["extClass"] = ".NomeDaClasse"
        manifestPlaceholders["extVersionCode"] = versionCode.toString()
        manifestPlaceholders["extFactory"] = ""
        manifestPlaceholders["nsfw"] = "0"
        manifestPlaceholders["sourceUrl"] = ""
        manifestPlaceholders["isNsfw"] = "false"
        manifestPlaceholders["libVersion"] = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Assinar com a chave debug permite instalação direta (sideload) sem erros de APK não assinado
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
    // API das extensões Mihon/Tachiyomi
    compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("org.jsoup:jsoup:1.17.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

Regras do `proguard-rules.pro`:
```proguard
# Manter classe da extensão e pontos de entrada
-keep class eu.kanade.tachiyomi.extension.** { *; }

# Manter interfaces da biblioteca de fontes
-keep class eu.kanade.tachiyomi.source.** { *; }

# Ignorar avisos de classes providas em runtime pelo app hospedeiro
-dontwarn rx.**
-dontwarn okhttp3.**
-dontwarn org.jsoup.**
-dontwarn eu.kanade.tachiyomi.**
-dontwarn android.**
-dontwarn java.lang.invoke.**
```

---

## 2.1. Arquivo `settings.gradle.kts` Independente (OBRIGATÓRIO)

Cada extensão **deve** conter seu próprio `settings.gradle.kts` dentro da sua respectiva pasta (ex: `extension-minhaextensao/settings.gradle.kts`):

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "extension-<nome>"
```

> [!CAUTION]
> **NUNCA** adicione `include(":extension-<nome>")` no `settings.gradle.kts` da raiz do repositório Yomotsu-Oficial! Isso quebra o build do app principal.

---

## 3. Estrutura do Código Kotlin (`ParsedHttpSource`)

- Extender `ParsedHttpSource()` ou `HttpSource()`.
- Respostas OkHttp: Sempre tratar nulabilidade com `response.body?.string().orEmpty()`.
- Headers: Sempre definir `headersBuilder()` com `User-Agent` de navegador atualizado.

---

## 4. GitHub Actions CI/CD (`.github/workflows/build-*-extension.yml`)

### ⚠️ Regra Crítica sobre Releases
**NUNCA crie GitHub Releases no repositório do app Yomotsu (`Yomotsu-Oficial`) para extensões!**
O verificador de atualizações interno do Yomotsu (`AppUpdateChecker`) lê a API de Releases do GitHub desse mesmo repositório. Criar uma Release de extensão acionará falsos avisos de "Atualização disponível" para os usuários do app.
**Solução:**
- Gerar os APKs e salvá-los usando `actions/upload-artifact@v4`.
- Ou manter as releases em um repositório separado dedicado a extensões.

### Configuração recomendada do CI:
- Usar **Gradle 8.5** (`gradle/actions/setup-gradle@v3` com `gradle-version: '8.5'`).
- JDK 17 (`actions/setup-java@v4` com `temurin`).
- `working-directory` apontando para a pasta da extensão.

---

## 5. Checklist de Diagnóstico e Correção

| Sintoma | Causa Raiz | Solução |
| :--- | :--- | :--- |
| **Extensão instalada fica invisível no app** | Falta `<uses-feature android:name="tachiyomi.extension" />` no Manifest. | Adicionar a tag `<uses-feature>` no Manifest. |
| **Pede para "Confiar" ao instalar** | APK assinado com chave própria (não oficial). | **Comportamento normal esperado**. Tocar no item e clicar em "Confiar". |
| **Erro de compilação Kotlin em `response.body`** | `response.body` é nulo em algumas versões do OkHttp. | Usar `response.body?.string().orEmpty()`. |
| **App Yomotsu acusa falso aviso de atualização** | Foi criada uma Release no GitHub para a extensão. | Deletar a Release no GitHub e passar o CI para usar artefatos (`upload-artifact`). |
| **`AAPT: error: file failed to compile` (ic_launcher.png)** | Arquivo de ícone não é um PNG real (ex: baixou HTML ou .ico com curl). | Baixar imagem PNG legítima e verificar cabeçalho binário (`\x89PNG`). |
| **`Execution failed for task ':minifyReleaseWithR8'` / Missing class `rx.Observable`** | O R8 não encontra classes runtime providas pelo app host. | Adicionar `-dontwarn rx.**`, `-dontwarn eu.kanade.tachiyomi.**` no `proguard-rules.pro`. |
| **`plugin version '' is invalid` no build raiz** | A extensão foi incluída no `settings.gradle.kts` do app Yomotsu. | Manter a extensão como projeto Gradle standalone com seu próprio `settings.gradle.kts`. |

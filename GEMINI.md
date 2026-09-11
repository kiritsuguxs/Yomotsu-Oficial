# Diretrizes para Extensões do Yomotsu / Mihon

Ao trabalhar com extensões neste repositório ou em módulos de extensão:

1. **Manifest Obrigatório e Metadados 1.6**: Qualquer extensão **deve** conter `<uses-feature android:name="tachiyomi.extension" />` no `AndroidManifest.xml`, além das meta-tags essenciais:
   - `tachiyomi.extension.class`: classe da fonte (ex: `.NhentaiBr`)
   - `tachiyomi.extension.factory`: classe de fábrica (ou vazia)
   - `tachiyomi.extension.nsfw`: `"1"` se adulto, `"0"` se livre
   - `tachiyomix.extensionLib`: `${libVersion}` (ex: `"1.6"`)
   - `tachiyomix.name`: Nome limpo da extensão exibido na interface (ex: `"Nhentai BR"`)
   - `tachiyomix.contentWarning`: Código de aviso (`0` = Livre/Safe, `1` = Misto/Mixed, `2` = Adulto/NSFW)
2. **Nova Política de versioncode do Keiyoushi**:
   - Para extensões no modelo 1.6, o `versionCode` DEVE ser calculado com a fórmula:
     ```kotlin
     val extVersionCode = 3
     val libVersion = "1.6"
     val calculatedVersionCode = libVersion.split(".")
         .joinToString("") { it.padStart(2, '0') }
         .toInt() * 1000 + extVersionCode
     ```
     Para 1.6 build 3, resulta em `106003`. O `versionName` deve ser `"$libVersion.$extVersionCode"` (`1.6.3`).
3. **Padrão Moderno: `HttpSource` Direto (Sem `ParsedHttpSource`)**:
   - `ParsedHttpSource` foi descontinuado e não existe nas libs mais novas.
   - Sempre herde diretamente de `HttpSource()`.
   - O parsing de HTML deve ser feito diretamente com Jsoup dentro de `popularMangaParse(response: Response): MangasPage`, `latestUpdatesParse(response)`, `searchMangaParse(response)`, `mangaDetailsParse(response)` e `pageListParse(response)`.
   - Nunca declare `imageUrlParse(document: Document)`. Se necessário declarar para o contrato do `HttpSource`, a assinatura correta é `imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")`.
4. **Assinatura**: Builds release de extensões devem configurar `signingConfig = signingConfigs.getByName("debug")` para que o APK seja assinado e aceito pelo instalador do Android (sideload).
5. **CI / Releases**: **NUNCA** crie GitHub Releases no repositório `Yomotsu-Oficial` para extensões, pois o app updater do Yomotsu consulta as Releases deste repositório e confundirá releases de extensão com atualizações do próprio aplicativo. Em vez disso, use artefatos do GitHub Actions (`actions/upload-artifact@v4`).
6. **Tratamento de Nulabilidade**: No código Kotlin dos scrapers, sempre trate a nulabilidade do corpo da resposta com `response.body?.string().orEmpty()`.
7. **Projeto Gradle Standalone**: Cada extensão DEVE possuir seu próprio `settings.gradle.kts` isolado dentro de sua pasta. **NUNCA** adicione `include(":extension-nome")` no `settings.gradle.kts` raiz do app Yomotsu.
8. **Ícones Válidos e Centralizados (AAPT e Densidades)**: O ícone `@mipmap/ic_launcher` DEVE ser um arquivo PNG binário autêntico (iniciando com `\x89PNG`). Nunca use favicons esticados ou baixados sem validar o cabeçalho. O logotipo deve ser perfeitamente centralizado nos eixos X e Y com margem de segurança de ~20% e gerado para todas as 5 densidades de tela (`mdpi`: 48px, `hdpi`: 72px, `xhdpi`: 96px, `xxhdpi`: 144px, `xxxhdpi`: 192px).
9. **ProGuard / R8 Rules**: Como extensões utilizam dependências `compileOnly` injetadas pelo app hospedeiro em tempo de execução, o arquivo `proguard-rules.pro` DEVE conter `-dontwarn rx.**`, `-dontwarn okhttp3.**`, `-dontwarn org.jsoup.**`, `-dontwarn eu.kanade.tachiyomi.**` para não quebrar a minificação R8.
10. **Compilação Standalone Estável**: Para extensões independentes, use `compileOnly("com.github.tachiyomiorg:extensions-lib:1.4.4")` com AGP 8.2.2 e Kotlin 1.9.22. Ele fornece todas as interfaces runtime necessárias para gerar APKs compatíveis com libVersion 1.6 sem exigir o plugin interno KSP complexo do Keiyoushi.
11. **Skill Disponível**: Consulte a skill em `.agents/skills/mihon-extensions/SKILL.md` para templates completos de código, manifesto, ícones e build.

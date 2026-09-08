# Diretrizes para Extensões do Yomotsu / Mihon

Ao trabalhar com extensões neste repositório ou em submódulos de extensão:

1. **Manifest Obrigatório**: Qualquer extensão **deve** conter `<uses-feature android:name="tachiyomi.extension" />` no `AndroidManifest.xml`, além das meta-tags `tachiyomi.extension.class`, `tachiyomi.extension.factory`, `tachiyomi.extension.nsfw` e `tachiyomix.extensionLib` (versão 1.4). Sem isso, o Yomotsu ignora completamente o APK instalado.
2. **Assinatura**: Builds release de extensões devem configurar `signingConfig = signingConfigs.getByName("debug")` para que o APK seja assinado e aceito pelo instalador do Android (sideload).
3. **CI / Releases**: **NUNCA** crie GitHub Releases no repositório `Yomotsu-Oficial` para extensões, pois o app updater do Yomotsu consulta as Releases deste repositório e confundirá releases de extensão com atualizações do próprio aplicativo. Em vez disso, use artefatos do GitHub Actions (`actions/upload-artifact@v4`).
4. **Tratamento de Nulabilidade**: No código Kotlin dos scrapers, sempre trate a nulabilidade do corpo da resposta com `response.body?.string().orEmpty()`.
5. **Skill Disponível**: Consulte a skill em `.agents/skills/mihon-extensions/SKILL.md` para o modelo completo de `build.gradle.kts`, `AndroidManifest.xml` e ProGuard.

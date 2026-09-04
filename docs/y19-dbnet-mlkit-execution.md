# Y19 DBNet + ML Kit — execução e verificação

## Escopo e estado

Este é um experimento não oficial na branch agent/y19-dbnet-mlkit-test1. Ele mantém a identidade do aplicativo, assinatura, atualizador e versão Y17; não é uma release, merge ou promoção.

O fluxo experimental usa DBNet para detectar e agrupar linhas, ML Kit para reconhecer a página inteira uma única vez e associação geométrica para formar blocos coerentes. A máscara DBNet chega à limpeza apenas onde a região foi associada e traduzida. Qualquer falha, associação ambígua, grupo DBNet parcialmente reconhecido ou metadado inválido retorna a página inteira ao OCR selecionado, sem misturar blocos experimentais e de fallback.

## Evidência já verificada

- A CI 33371742703, no commit e7124db4f8652961f0481639b0e6fa409a90e9d4, passou as 384 unidades sem failures/errors/skips, 8 testes Android, migrações e o executável C++ ASan/UBSan com 42 assertions.
- A mesma CI verificou a identidade app.mihon.tachiyomiat, versionCode 77, versionName 0.20.4-Y17, o certificado SHA-256 0a72eafb442d14f10893a7cbd4b6034292d18d786dceda0f55f8d854a515ede6, assets Paddle e ausência dos pesos DBNet no APK.
- O APK assinado dessa execução teve 116891180 bytes. Comparado manualmente ao baseline Y19 DBNet+Paddle de 116858368 bytes, a diferença é 32812 bytes. A workflow daquela execução ainda baixava o baseline Y17, portanto ela não é a validação final da comparação Y19.
- A CI 33372801104, no commit 2488b7323e2fc9a1db3c1ed7c36f556eea6cae9e, confirmou o RED de teste primeiro: às 08:36:01 a verificação de empacotamento recebeu o APK Y17 de 107609006 bytes e falhou somente no assert que exige 116858368 bytes. As unidades, migrações, verificação nativa, assinatura, identidade e assets passaram antes desse assert; o job Android também passou.

A validação final da nova revisão ainda está pendente; este documento não registra uma CI verde futura.

## Baseline e artefato de verificação

A workflow protege a revisão do baseline antes do download: run 33349457520, head ffd53db5677b4a1c1eacc0e784cfc08f23d73bbf e artefato Yomotsu-Y19-DBNet-experimental-arm64. O ZIP baixado é y19-baseline.zip e contém o APK de 116858368 bytes. O artefato de Actions de saída tem o nome explícito Yomotsu-Y19-DBNet-MLKit-experimental-arm64.

Depois de todas as verificações existentes de identidade, assets, assinatura e empacotamento, somente o APK final é renomeado para Yomotsu-Y19-DBNet-MLKit-test1-arm64.apk. O renomeio não altera bytes, manifesto, identidade, assinatura ou compatibilidade do atualizador. A entrega esperada é o ZIP de artefato do GitHub Actions contendo esse APK.

## Métricas e aceitação em telefone

Cada página pode registrar uma linha estruturada YomotsuDBNet com pagePreparation, dbnetRequest, workerPreparation, workerInference, workerPostprocess, grouping, mlKit, association, maskPreparation e total, além de status, fallbackIncluded, formas e contagens. A linha não inclui texto reconhecido, texto traduzido ou credenciais. total inclui o fallback quando fallbackIncluded é verdadeiro e não deve ser calculado pela soma de tempos que podem se sobrepor.

Ainda não há benchmark ou aceitação em telefone. A aceitação pendente deve repetir o capítulo e as páginas problemáticas, verificar resíduos do idioma de origem, sobreposições, separação de balões e o comportamento normal com DBNet desligado, e registrar as métricas disponíveis.

## Limites conhecidos e custos de segurança

- O caminho real de detecção usa o wrapper implementado, e não o caminho de OCR citado no plano que não existe; isso não muda o comportamento.
- A dilatação de raio 1 é recortada novamente aos quadriláteros exatos; por isso pode deixar antialiasing que caia fora deles, mas não pode apagar fora das permissões associadas.
- Quando um bloco ML Kit cruza múltiplos grupos DBNet, a página inteira faz fallback; isso reduz cobertura para preservar separação.
- Um grupo parcialmente reconhecido também faz fallback da página inteira; membros falsos positivos aumentam a chance de fallback.
- Um grupo preparado sem máscara útil faz fallback da página inteira. Segmentação ou clipping fracos podem aumentar esse custo; a máscara vazia persistida é uma operação sem efeito.
- O escopo estreito da Task 7 e o seletor Android foram levados adiante para obter o RED real de Compose; isso adicionou commits e CI de teste, não uma mudança de produto.
- Metadados EXIF rotacionados ou inválidos são rejeitados apenas no experimento: a página faz fallback e pode desabilitar a sessão DBNet da execução atual, em vez de aplicar uma correção EXIF insegura.

## Limite de downgrade

Builds novos leem JSON antigo ou nulo. Porém, o Y17 antigo usa defaultJson estrito e não consegue decodificar o campo não nulo dbnetCleanupMask. TranslationManager captura essa falha de decodificação e apaga o arquivo de tradução do capítulo inteiro. Portanto, voltar para um APK antigo depois de traduções mascaradas pode descartar esses arquivos de tradução de capítulo.

Compatibilidade de instalação futura por identidade e certificado é um assunto separado. Este experimento não deve ser descrito como seguro para downgrade e não altera o gerenciador de cache protegido para ocultar esse risco.

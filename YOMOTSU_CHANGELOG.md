# Novidades do Yomotsu

Este arquivo contém somente as mudanças importantes para quem usa o aplicativo.
O workflow de lançamento lê automaticamente a seção correspondente à versão publicada.

## 0.20.4-Y19
- Adicionado o detector experimental DBNet como opção desativada por padrão para aparelhos ARM64 e textos em inglês.
- O fluxo híbrido usa o DBNet para detectar e agrupar linhas, o ML Kit para reconhecer a página inteira uma vez e a associação geométrica para formar regiões coerentes antes da tradução.
- A máscara DBNet limpa somente as regiões associadas e traduzidas, com recorte e refinamento para remover melhor o texto sem preencher áreas sólidas indevidas.
- Falhas, resultados inválidos ou associações ambíguas retornam a página inteira com segurança ao OCR selecionado, sem misturar resultados parciais.
- O detector roda em processo separado para proteger o leitor contra falhas nativas.
- O modelo DBNet de aproximadamente 153 MB permanece fora do APK, é validado por SHA-256 e só exibe o aviso de download quando algum arquivo realmente precisa ser baixado.

## 0.20.4-Y17
- Melhorado o encaixe das traduções dentro da região segura dos balões, incluindo formatos ovais e arredondados.
- Melhorada a separação entre balões vizinhos para evitar a união indevida e a sobreposição de falas.
- Corrigido o agrupamento de parágrafos com várias linhas e fragmentos curtos da mesma fala.
- Melhorada a limpeza do texto original, preservando os contornos dos balões e os desenhos ao redor.
- Corrigida a escolha da cor de limpeza que podia criar faixas pretas dentro de balões claros em páginas escuras.
- Ajuste automático do tamanho da fonte mais consistente, aproveitando o espaço útil de cada balão.
- Gemini com comunicação REST, limite de 30 segundos e mensagens de erro HTTP mais claras, sem repetição automática de requisições.

## 0.20.4-Y16
- Renovadas as opções de fonte da tradução, mantendo Anime Ace como padrão e com migração automática da opção antiga.
- Adicionado auto-scroll ajustável nos modos Webtoon e Vertical contínuo.
- Ao ativar o auto-scroll, os controles do leitor são ocultados automaticamente; ao tocar na tela, a rolagem é pausada e os controles podem voltar a aparecer normalmente.

## 0.20.4-Y16-test1
- Renovadas as opções de fonte da tradução, com Anime Ace como padrão e migração automática da opção antiga.
- Adicionado auto-scroll ajustável nos modos Webtoon e Vertical contínuo, pausado ao primeiro toque.
- A tela do mangá e as barras do leitor agora usam uma cor de destaque extraída da capa, sem alterar as páginas ou os balões traduzidos.

## 0.20.4-Y15
- Adicionada tradução automática configurável individualmente em cada obra.
- Adicionado botão para traduzir manualmente os capítulos já baixados.
- Adicionado motor de tradução reserva quando o principal não consegue concluir a tradução.
- Downloads e arquivos temporários da tradução não aparecem mais na Galeria do celular.

## 0.20.4-Y14
- Corrigidos espaços invisíveis que permaneciam entre trechos traduzidos pelo PaddleOCR.
- Frases completas separadas pelo OCR agora podem compartilhar o mesmo balão quando a geometria da imagem confirma que pertencem à mesma fala.
- Balões próximos continuam separados para evitar a união indevida de diálogos diferentes.

## 0.20.4-Y13.1
- PaddleOCR usa o modelo oficial inglês PP-OCRv5 mobile e agora limita o detector a uma resolução adequada para celular, acelerando bastante a tradução de capítulos.
- Falas de larguras parecidas continuam agrupadas internamente, com a ordem original restaurada antes da tradução.
- ML Kit, limpeza de balões, agrupamento, renderização, memória, cache, glossário e Google Tradutor permanecem iguais à Y13.

## 0.20.4-Y13
- Adicionado o PaddleOCR como alternativa ao ML Kit, mantendo o Google Tradutor, o cache, a memória e as opções existentes.
- Melhorado o agrupamento de linhas próximas de uma mesma fala no PaddleOCR.
- Removida a hifenização automática: as linhas agora quebram apenas entre palavras.
- A limpeza dos balões usa cantos arredondados para evitar abas brancas laterais.
- Corrigida a detecção de balões circulares grandes com pouco texto, comum em pensamentos e narrações; painéis largos continuam excluídos.

## 0.20.4-Y13-test8
- Corrigida a detecção de balões circulares grandes com pouco texto, comum em pensamentos e narrações.
- Painéis largos continuam fora da detecção para não receber tradução como se fossem balões.

## 0.20.4-Y13-test3
- PaddleOCR volta a reunir linhas próximas da mesma fala quando a primeira linha ainda não terminou a frase.
- O texto traduzido não recebe mais hifenização automática dentro dos balões.
- A limpeza de balões detectados usa cantos arredondados para evitar abas brancas laterais.

## 0.20.4-Y12
- O glossário agora aceita vários termos de uma vez e diferencia termos, nomes, títulos e técnicas.
- Nomes e outros itens protegidos são preservados também no ML Kit e no Google Tradutor.
- Correções manuais passam a ser aprendidas por obra e reaproveitadas nos capítulos seguintes.
- Glossário, memória e cache usam uma estrutura versionada, preparada para futura importação, exportação e backup.
- A tradução funciona em primeiro plano e mostra capítulo, página e etapa atual na notificação.
- Filas grandes continuam capítulo a capítulo, inclusive quando muitos downloads são solicitados de uma vez.
- A exportação experimental de CBZ traduzido foi removida; a tradução no leitor e o JSON editável continuam disponíveis.

## 0.20.4-Y11
- Reforçada a segurança da atualização por cima da Y10.1, preservando pacote e assinatura do aplicativo.
- Removido o último funding herdado do Mihon e isolado o atualizador nos canais do Yomotsu.
- Adicionados testes obrigatórios de regressão e validação da assinatura antes de disponibilizar novos APKs.
- Falas que claramente continuarem em inglês recebem uma segunda tentativa automática; se ainda falharem, o original permanece visível para correção manual.
- Traduções longas passam a usar um modo de encaixe seguro, com mais espaço útil e redução adicional da fonte somente quando necessário.

## 0.20.4-Y10
- Melhorada a detecção e a organização das falas para traduzir mais textos sem separar frases relacionadas.
- Ajustados o tamanho das páginas, o encaixe, a centralização e o tamanho das traduções nos balões.
- Adicionada edição por toque longo: traduções prontas podem ser corrigidas e textos restantes em inglês podem ser traduzidos manualmente.
- O texto original em inglês só é coberto depois que a tradução manual é salva.
- Corrigidos os botões Salvar e Retraduzir do editor e publicados os links de ajuda e privacidade.

## 0.20.4-Y9.1
- Adicionada uma margem interna segura para manter a tradução dentro dos balões arredondados.
- Reduzido o tamanho do texto quando necessário, sem cortar linhas que não cabem.
- Removida a hifenização automática que separava palavras no meio.
- Capítulos já traduzidos pela primeira Y9 recebem o encaixe seguro sem apagar a tradução.

## 0.20.4-Y9
- Limpeza ampliada e sem rotação para apagar melhor o texto original dos balões.
- Detecção do espaço claro ao redor do texto para aproveitar melhor o interior do balão.
- Tamanho, quebra de linha, alinhamento e centralização da tradução ajustados automaticamente.
- Compatibilidade mantida com capítulos traduzidos em versões anteriores.

## 0.20.4-Y8
- Adicionada continuidade entre os diálogos durante a tradução.
- Gemini e OpenRouter agora consideram o contexto das falas anteriores.
- Melhorada a consistência de nomes, termos e pronomes ao longo do capítulo.
- Cache e glossário continuam sendo respeitados durante a tradução contextual.

## 0.20.4-Y7
- Adicionado cache inteligente e persistente de traduções por obra e idioma.
- Traduções já conhecidas podem ser reutilizadas sem traduzir novamente.
- Cache integrado ao DeepL, Google Translate, ML Kit e Gemini com preservação de contexto.
- Melhorada a prioridade do glossário para respeitar termos salvos mesmo com diferenças de maiúsculas e pontuação.
- Mantida a tradução contextual quando há conteúdo novo para evitar perda de qualidade.

## 0.20.4-Y6
- Adicionada memória de tradução persistente.
- Adicionado glossário de tradução por obra.
- Glossário integrado aos tradutores disponíveis no Yomotsu.
- Melhorada a consistência das traduções de nomes e termos recorrentes.

## 0.20.4-Y5
- Adicionado o DeepL como opção de tradução.
- Incluído suporte às APIs Free e Pro por chave configurada pelo usuário.

<!-- Adicione novas versões acima desta linha no formato:
## 0.20.4-Y10
- Primeira novidade.
- Segunda novidade.
-->

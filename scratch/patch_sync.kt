            for (msg in messages) {
                if (msg.id == fromMessageId && iterations > 0) continue
                val content = msg.content
                if (content is org.drinkless.tdlib.TdApi.MessageDocument || content is org.drinkless.tdlib.TdApi.MessageVideo) {
                    val caption = if (content is org.drinkless.tdlib.TdApi.MessageDocument) content.caption.text else (content as org.drinkless.tdlib.TdApi.MessageVideo).caption.text
                    
                    var titleMatch: String? = null
                    var capMatch: String? = null
                    var sinopseMatch: String? = null

                    val oMatch = obraRegex.find(caption)?.groupValues?.get(1)?.trim()
                    val cMatch = capRegex.find(caption)?.groupValues?.get(1)?.trim()
                    val sMatch = sinopseRegex.find(caption)?.groupValues?.get(1)?.trim()

                    if (!oMatch.isNullOrBlank()) titleMatch = oMatch
                    if (!cMatch.isNullOrBlank()) capMatch = cMatch
                    if (!sMatch.isNullOrBlank()) sinopseMatch = sMatch

                    if (titleMatch != null && capMatch != null) {
                        val mangaType = when {
                            caption.contains("#YomotsuAnime", ignoreCase = true) -> "ANIME"
                            caption.contains("#YomotsuNovel", ignoreCase = true) -> "NOVEL"
                            else -> "MANGA"
                        }
                        
                        var m = currentIndex.find { it.title.equals(titleMatch, ignoreCase = true) }
                        if (m == null) {
                            val cUrl = existingCovers[titleMatch!!.lowercase()]
                                ?: libraryMangas[titleMatch!!.lowercase()]
                                ?: ""
                            val desc = existingDescs[titleMatch!!.lowercase()]
                                ?: sinopseMatch
                                ?: ""
                            m = CloudManga(titleMatch!!, desc, cUrl, mutableListOf(), type = mangaType)
                            currentIndex.add(m)
                        }

                        if (m.chapters.none { it.name.equals(capMatch, ignoreCase = true) }) {
                            val docId = if (content is org.drinkless.tdlib.TdApi.MessageDocument) content.document.document.id else (content as org.drinkless.tdlib.TdApi.MessageVideo).video.video.id
                            val remoteDocId = if (content is org.drinkless.tdlib.TdApi.MessageDocument) content.document.document.remote.id else (content as org.drinkless.tdlib.TdApi.MessageVideo).video.video.remote.id
                            m.chapters.add(
                                CloudChapter(
                                    name = capMatch!!,
                                    messageId = msg.id,
                                    fileId = docId,
                                    remoteFileId = remoteDocId,
                                    date = msg.date.toLong(),
                                ),
                            )
                        }
                    }
                }
            }

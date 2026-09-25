fun com.hippo.unifile.UniFile.size(): Long {
    var size = 0L
    listFiles()?.forEach { file ->
        size += if (file.isDirectory) file.size() else file.length()
    }
    return size
}

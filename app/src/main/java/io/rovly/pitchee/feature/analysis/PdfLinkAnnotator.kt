package io.rovly.pitchee.feature.analysis

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

internal object PdfLinkAnnotator {
    fun addUriLink(
        file: File,
        uri: String,
        rect: List<Float> = listOf(42f, 38f, 114f, 54f),
    ) {
        val original = file.readBytes()
        val text = String(original, StandardCharsets.ISO_8859_1)
        val pageHeaderRegex = Regex("""(\d+)\s+0\s+obj\s*""")
        val pageTypeRegex = Regex("""/Type\s*/Page\b""")
        val pageObjects = mutableListOf<PageObject>()
        var searchFrom = 0

        while (true) {
            val pageType = pageTypeRegex.find(text, searchFrom) ?: break
            val prefix = text.substring(0, pageType.range.first)
            val header = pageHeaderRegex.findAll(prefix).lastOrNull()
            val objectEnd = text.indexOf("endobj", pageType.range.last)
            if (header != null && objectEnd > pageType.range.last) {
                val dictionaryStart = text.indexOf("<<", header.range.last + 1)
                val dictionaryEnd = text.lastIndexOf(">>", objectEnd)
                if (dictionaryStart in header.range.last..<objectEnd && dictionaryEnd > dictionaryStart) {
                    pageObjects += PageObject(
                        objectNumber = header.groupValues[1].toInt(),
                        dictionary = text.substring(dictionaryStart, dictionaryEnd + 2),
                    )
                }
            }
            searchFrom = pageType.range.last + 1
        }

        if (pageObjects.isEmpty()) return

        val startXref = Regex("""startxref\s+(\d+)""")
            .findAll(text)
            .lastOrNull()
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: return
        val trailer = text.substringBeforeLast("%%EOF")
        val originalSize = Regex("""/Size\s+(\d+)""")
            .find(trailer)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return
        val rootObject = Regex("""/Root\s+(\d+)\s+0\s+R""")
            .find(trailer)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: return

        val output = ByteArrayOutputStream()
        output.write(original)
        output.write('\n'.code)
        val offsets = linkedMapOf<Int, Long>()
        var nextObjectNumber = originalSize
        val annotations = mutableListOf<Pair<Int, Int>>()

        pageObjects.forEach { page ->
            if ("/Annots" in page.dictionary) return@forEach
            val annotationObjectNumber = nextObjectNumber++
            annotations += page.objectNumber to annotationObjectNumber
            val updatedDictionary = page.dictionary
                .removeSuffix(">>")
                .trimEnd() + " /Annots [$annotationObjectNumber 0 R] >>"
            writeObject(output, page.objectNumber, updatedDictionary, offsets)
        }

        if (annotations.isEmpty()) return

        annotations.forEach { (_, annotationObjectNumber) ->
            writeObject(
                output,
                annotationObjectNumber,
                linkAnnotation(uri, rect),
                offsets,
            )
        }

        val xrefOffset = output.size().toLong()
        val xrefWriter = output.writer(StandardCharsets.ISO_8859_1)
        xrefWriter.write("xref\n")
        offsets.entries.sortedBy { it.key }.forEach { (objectNumber, offset) ->
            xrefWriter.write("$objectNumber 1\n")
            xrefWriter.write("%010d 00000 n \n".format(offset))
        }
        xrefWriter.write(
            "trailer\n<</Size $nextObjectNumber /Root $rootObject 0 R /Prev $startXref>>\n",
        )
        xrefWriter.write("startxref\n$xrefOffset\n%%EOF\n")
        xrefWriter.flush()

        file.writeBytes(output.toByteArray())
    }

    private fun writeObject(
        output: ByteArrayOutputStream,
        objectNumber: Int,
        body: String,
        offsets: MutableMap<Int, Long>,
    ) {
        offsets[objectNumber] = output.size().toLong()
        val writer = output.writer(StandardCharsets.ISO_8859_1)
        writer.write("$objectNumber 0 obj\n$body\nendobj\n")
        writer.flush()
    }

    private fun linkAnnotation(uri: String, rect: List<Float>): String {
        val rectValues = rect.joinToString(" ") { value ->
            if (value % 1f == 0f) value.toInt().toString() else value.toString()
        }
        val escapedUri = uri.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        return "<</Type /Annot /Subtype /Link /Rect [$rectValues] " +
            "/Border [0 0 0] /A << /S /URI /URI ($escapedUri) >> >>"
    }

    private data class PageObject(
        val objectNumber: Int,
        val dictionary: String,
    )
}

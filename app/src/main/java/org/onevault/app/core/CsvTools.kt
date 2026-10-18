package org.onevault.app.core

/** Minimal RFC-4180-aware delimited record parser for manual CSV imports. */
object CsvTools {
    fun parse(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>()
        val row=mutableListOf<String>()
        val field=StringBuilder()
        var quoted=false; var i=0
        while(i<text.length) {
            val ch=text[i]
            when {
                ch=='"' && quoted && i+1<text.length && text[i+1]=='"' -> {field.append('"');i++}
                ch=='"' -> quoted=!quoted
                ch==',' && !quoted -> {row+=field.toString();field.clear()}
                (ch=='\r'||ch=='\n') && !quoted -> {
                    if(ch=='\r' && i+1<text.length && text[i+1]=='\n')i++
                    row+=field.toString();field.clear()
                    if(row.any{it.isNotBlank()})rows+=row.toList()
                    row.clear()
                }
                else -> field.append(ch)
            }
            i++
        }
        require(!quoted){"CSV has an unclosed quotation mark"}
        row+=field.toString()
        if(row.any{it.isNotBlank()})rows+=row.toList()
        return rows
    }
}

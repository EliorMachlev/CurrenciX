package com.eliormachlev.currencix.model.adapter

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException
import java.time.LocalDate

/** ISO dates ("2026-10-06") to and from [LocalDate]. */
internal class LocalDateAdapter : TypedJsonAdapter<LocalDate>(LocalDate::class.java) {
    @Synchronized
    @Throws(IOException::class)
    override fun fromJson(reader: JsonReader): LocalDate? = LocalDate.parse(reader.nextString())

    @Synchronized
    @Throws(IOException::class)
    override fun toJson(
        writer: JsonWriter,
        value: LocalDate?,
    ) {
        writer.value(value?.toString())
    }
}

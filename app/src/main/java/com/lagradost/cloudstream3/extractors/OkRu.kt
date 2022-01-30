package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.app


class Mailru : OkRu() {
    override val mainUrl = "https://mail.ru"
}

class Mailru1 : OkRu() {
    override val mainUrl = "http://mail.ru"
}

class Okru1 : OkRu() {
    override val mainUrl = "https://ok.ru"
}


open class OkRu : ExtractorApi() {
    override val name = "Okru"
    override val mainUrl = "http://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doc = app.get(url).document
        val urlString = doc.select("div[data-options]").attr("data-options")
            .substringAfter("\\\"videos\\\":[{\\\"name\\\":\\\"")
            .substringBefore("]")
        urlString.split("{\\\"name\\\":\\\"").reversed().forEach {
            val extractedUrl = it.substringAfter("url\\\":\\\"")
                .substringBefore("\\\"")
                .replace("\\\\u0026", "&")
            val Quality = it.uppercase().substringBefore("\\\"")

            return listOf(
                ExtractorLink(
                    name,
                    "$name ${Quality}",
                    extractedUrl,
                    url,
                    Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
        }
        return null
    }
}
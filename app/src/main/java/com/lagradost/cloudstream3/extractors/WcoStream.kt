package com.lagradost.cloudstream3.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.utils.*

class Vidstreamz : WcoStream() {
    override var mainUrl = "https://vidstreamz.online"
}
class Vizcloud : WcoStream() {
    override var mainUrl = "https://vizcloud2.ru"
}
class Vizcloud2 : WcoStream() {
    override var mainUrl = "https://vizcloud2.online"
}

class VizcloudOnline : WcoStream() {
    override var mainUrl = "https://vizcloud.online"
}

class VizcloudXyz : WcoStream() {
    override var mainUrl = "https://vizcloud.xyz"
}

class VizcloudLive : WcoStream() {
    override var mainUrl = "https://vizcloud.live"
}

class VizcloudInfo : WcoStream() {
    override var mainUrl = "https://vizcloud.info"
}

class VizcloudDigital : WcoStream() {
    override var mainUrl = "https://vizcloud.digital"
}

open class WcoStream : ExtractorApi() {
    override var name = "VidStream" //Cause works for animekisa and wco
    override var mainUrl = "https://vidstream.pro"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val baseUrl = url.split("/e/")[0]

        val html = app.get(url, headers = mapOf("Referer" to "https://wcostream.cc/")).text
        val (Id) = (Regex("/e/(.*?)?domain").find(url)?.destructured ?: Regex("""/e/(.*)""").find(url)?.destructured) ?: return emptyList()
        val (skey) = Regex("""skey\s=\s['"](.*?)['"];""").find(html)?.destructured ?: return emptyList()

        val apiLink = "$baseUrl/info/$Id?domain=wcostream.cc&skey=$skey"
        val referrer = "$baseUrl/e/$Id?domain=wcostream.cc"

        val response = app.get(apiLink, headers = mapOf("Referer" to referrer)).text

        data class Sources(
            @JsonProperty("file") val file: String,
            @JsonProperty("label") val label: String?
        )

        data class Media(
            @JsonProperty("sources") val sources: List<Sources>
        )

        data class WcoResponse(
            @JsonProperty("success") val success: Boolean,
            @JsonProperty("media") val media: Media
        )

        val mapped = response.let { mapper.readValue<WcoResponse>(it) }
        val sources = mutableListOf<ExtractorLink>()

        if (mapped.success) {
            mapped.media.sources.apmap {
                if (mainUrl == "https://vizcloud2.ru" || mainUrl == "https://vizcloud.online") {
                    if (it.file.contains("vizcloud2.ru") || it.file.contains("vizcloud.online")) {
                        //Had to do this thing 'cause "list.m3u8#.mp4" gives 404 error so no quality is added
                        val link1080 = it.file.replace("list.m3u8#.mp4","H4/v.m3u8")
                        val link720 = it.file.replace("list.m3u8#.mp4","H3/v.m3u8")
                        val link480 = it.file.replace("list.m3u8#.mp4","H2/v.m3u8")
                        val link360 = it.file.replace("list.m3u8#.mp4","H1/v.m3u8")
                        val linkauto = it.file.replace("#.mp4","")
                        listOf(
                            link1080,
                            link720,
                            link480,
                            link360,
                            linkauto).apmap { serverurl ->
                            val testurl = app.get(serverurl, headers = mapOf("Referer" to url)).text
                            if (testurl.contains("EXTM3")) {
                                val quality = if (serverurl.contains("H4")) "1080p"
                                else if (serverurl.contains("H3")) "720p"
                                else if (serverurl.contains("H2")) "480p"
                                else if (serverurl.contains("H1")) "360p"
                                else "Auto"
                                sources.add(
                                    ExtractorLink(
                                        "VidStream",
                                        "VidStream $quality",
                                        serverurl,
                                        url,
                                        getQualityFromName(quality),
                                        true,
                                    )
                                )
                            }
                        }
                    }
                }
                if (mainUrl == "https://vidstream.pro" || mainUrl == "https://vidstreamz.online" || mainUrl == "https://vizcloud2.online"
                    || mainUrl == "https://vizcloud.xyz" || mainUrl == "https://vizcloud.live" || mainUrl == "https://vizcloud.info"
                    || mainUrl == "https://vizcloud.digital") {
                    if (it.file.contains("vizcloud2") || it.file.contains("vizloud.xyz")) {
                        val link1080 = it.file.replace("list.m3u8#.mp4","H4/v.m3u8")
                        val link720 = it.file.replace("list.m3u8#.mp4","H3/v.m3u8")
                        val link480 = it.file.replace("list.m3u8#.mp4","H2/v.m3u8")
                        val link360 = it.file.replace("list.m3u8#.mp4","H1/v.m3u8")
                        val linkauto = it.file.replace("#.mp4","")
                        listOf(
                            link1080,
                            link720,
                            link480,
                            link360,
                            linkauto).apmap { serverurl ->
                            val testurl = app.get(serverurl.replace("#.mp4",""), headers = mapOf("Referer" to url)).text
                            if (testurl.contains("EXTM3")) {
                                val quality = if (serverurl.contains("H4")) "1080p"
                                else if (serverurl.contains("H3")) "720p"
                                else if (serverurl.contains("H2")) "480p"
                                else if (serverurl.contains("H1")) "360p"
                                else "Auto"
                                sources.add(
                                    ExtractorLink(
                                        "VidStream",
                                        "VidStream $quality",
                                        serverurl.replace("#.mp4",""),
                                        url,
                                        getQualityFromName(quality),
                                        true,
                                    )
                                )
                            }
                        }
                    } else {
                     val link1080 = it.file.replace("list.m3u8","hls/1080/1080.m3u8")
                     val link720 = it.file.replace("list.m3u8","hls/720/720.m3u8")
                     val link480 = it.file.replace("list.m3u8","hls/480/480.m3u8")
                     val link360 = it.file.replace("list.m3u8","hls/360/360.m3u8")
                     val linkauto = it.file
                    listOf(
                        link1080,
                        link720,
                        link480,
                        link360,
                        linkauto).apmap { serverurl ->
                        val testurl = app.get(serverurl, headers = mapOf("Referer" to url)).text
                        if (testurl.contains("EXTM3")) {
                            val quality = if (serverurl.contains("1080")) "1080p"
                            else if (serverurl.contains("720")) "720p"
                            else if (serverurl.contains("480")) "480p"
                            else if (serverurl.contains("360")) "360p"
                            else "Auto"
                            sources.add(
                                ExtractorLink(
                                    "VidStream",
                                    "VidStream $quality",
                                    serverurl,
                                    url,
                                    getQualityFromName(quality),
                                    true,
                                )
                            )
                        }
                    }
                   }
                }
            }
        }
        return sources
    }
}

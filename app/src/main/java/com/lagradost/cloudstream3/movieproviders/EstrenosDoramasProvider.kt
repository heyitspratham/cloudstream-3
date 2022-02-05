package com.lagradost.cloudstream3.movieproviders

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import java.util.*
import kotlin.collections.ArrayList


class EstrenosDoramasProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.Movie
            else TvType.TvSeries
        }
    }

    override val mainUrl = "https://www23.estrenosdoramas.net"
    override val name = "EstrenosDoramas"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair(mainUrl, "Últimas series"),
            Pair("$mainUrl/category/peliculas", "Películas"),
        )

        val items = ArrayList<HomePageList>()

        for (i in urls) {
            try {

                val home = app.get(i.first, timeout = 120).document.select("div.clearfix").map {
                    val title = it.selectFirst("h3 a").text().replace(Regex("[Pp]elicula| "),"")
                    val poster = it.selectFirst("img.cate_thumb").attr("src")
                    AnimeSearchResponse(
                        title,
                        it.selectFirst("a").attr("href"),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                            DubStatus.Dubbed
                        ) else EnumSet.of(DubStatus.Subbed),
                    )
                }

                items.add(HomePageList(i.second, home))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (items.size <= 0) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): ArrayList<SearchResponse> {
        val search =
            app.get("$mainUrl/?s=$query", timeout = 120).document.select("div.clearfix").map {
                val title = it.selectFirst("h3 a").text().replace(Regex("[Pp]elicula| "),"")
                val href = it.selectFirst("a").attr("href")
                val image = it.selectFirst("img.cate_thumb").attr("src")
                AnimeSearchResponse(
                    title,
                    href,
                    this.name,
                    TvType.Anime,
                    image,
                    null,
                    if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(
                        DubStatus.Dubbed
                    ) else EnumSet.of(DubStatus.Subbed),
                )
            }
        return ArrayList(search)
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst("head meta[property]").attr("content")
        val title = doc.selectFirst("h1.titulo").text()
        val description = try {
            doc.selectFirst("div.post div.highlight div.font").toString()
        } catch (e:Exception){
            null
        }
        val desc = Regex("(<b>Sinopsis: <\\/b><\\/span>.+.\\n.*\\n.*|<b>Sinopsis:<\\/b><\\/span>.+)")
        val finaldesc = description?.let {
            desc.findAll(it).map {
                it.value.replace("<br>","").replace("<b>Sinopsis: </b></span>","")
                    .replace("<p>","").replace("<b>","").replace("</b>","")
                    .replace("</span>","").replace("Sinopsis:","")
            }.toList().first()
        }
        val episodes = doc.select("div.post .lcp_catlist a").map {
            val name = it.selectFirst("a").text()
            val link = it.selectFirst("a").attr("href")
            AnimeEpisode(link, name)
        }.reversed()

        return when (val type = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries) {
            TvType.TvSeries -> {
                return newAnimeLoadResponse(title, url, type) {
                    japName = null
                    engName = title
                    posterUrl = poster
                    addEpisodes(DubStatus.Subbed, episodes)
                    plot = finaldesc
                }
            }
            TvType.Movie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    TvType.Movie,
                    url,
                    poster,
                    null,
                    finaldesc,
                    null,
                    null,
                )
            }
            else -> null
        }
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers = mapOf("Host" to "repro3.estrenosdoramas.us",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "X-Requested-With" to "XMLHttpRequest",
            "Origin" to "https://repro3.estrenosdoramas.us",
            "DNT" to "1",
            "Connection" to "keep-alive",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin",
            "Cache-Control" to "max-age=0",)

        val document = app.get(data).document
       document.select("div.tab_container iframe").apmap { container ->
            val directlink = container.attr("src").replace("//ok.ru","https://ok.ru")
            for (extractor in extractorApis) {
                if (directlink.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(directlink, data)?.apmap {
                        callback(it)
                    }
                }
            }
        }

        if (document.toString().contains("reproducir14")) {
            val regex = Regex("(https:\\/\\/repro.\\.estrenosdoramas\\.us\\/repro\\/reproducir14\\.php\\?key=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            regex.findAll(document.toString()).map {
                it.value
            }.toList().apmap {
                val doc = app.get(it).text
                val videoid = doc.substringAfter("vid=\"").substringBefore("\" n")
                val token = doc.substringAfter("name=\"").substringBefore("\" s")
                val acctkn = doc.substringAfter("{ acc: \"").substringBefore("\", id:")
                val link = app.post("https://repro3.estrenosdoramas.us/repro/proto4.php",
                    headers = headers,
                    data = mapOf(
                        Pair("acc",acctkn),
                        Pair("id",videoid),
                        Pair("tk",token)),
                    allowRedirects = false
                ).text
                val extracteklink = link.substringAfter("\"urlremoto\":\"").substringBefore("\"}")
                    .replace("\\/", "/").replace("//ok.ru/","https://ok.ru/")
                for (extractor in extractorApis) {
                    if (extracteklink.startsWith(extractor.mainUrl)) {
                        extractor.getSafeUrl(extracteklink, data)?.apmap {
                            callback(it)
                        }
                    }
                }
            }
        }

        if (document.toString().contains("reproducir120")) {
            val regex = Regex("(https:\\/\\/repro3.estrenosdoramas.us\\/repro\\/reproducir120\\.php\\?\\nkey=[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
            regex.findAll(document.toString()).map {
                it.value
            }.toList().apmap {
                val doc = app.get(it).text
                val videoid = doc.substringAfter("var videoid = '").substringBefore("';")
                val token = doc.substringAfter("var tokens = '").substringBefore("';")
                val acctkn = doc.substringAfter("{ acc: \"").substringBefore("\", id:")
                val link = app.post("https://repro3.estrenosdoramas.us/repro/api3.php",
                    headers = headers,
                    data = mapOf(
                        Pair("acc",acctkn),
                        Pair("id",videoid),
                        Pair("tk",token)),
                    allowRedirects = false
                ).text
                val extracteklink = link.substringAfter("\"{file:'").substringBefore("',label:")
                    .replace("\\/", "/")
                val quality = link.substringAfter(",label:'").substringBefore("',type:")
                val type = link.substringAfter("type: '").substringBefore("'}\"")
                if (extracteklink.isNotBlank())
                    callback(
                        ExtractorLink(
                            "Movil",
                            "Movil $quality",
                            extracteklink,
                            "",
                            Qualities.Unknown.value,
                            !type.contains("mp4")
                        )
                    )
            }
        }
        return true
    }
}
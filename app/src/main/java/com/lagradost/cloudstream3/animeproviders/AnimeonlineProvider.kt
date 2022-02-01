package com.lagradost.cloudstream3.animeproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.util.*
import kotlin.collections.ArrayList

class AnimeonlineProvider:MainAPI() {
    override val mainUrl: String
        get() = "https://animeonline1.ninja"
    override val name: String
        get() = "Animeonline"
    override val lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/genero/en-emision/", "En emisión",),
            Pair("$mainUrl/genero/sin-censura/", "Sin censura",),
        )
        val items = ArrayList<HomePageList>()

        items.add(HomePageList("Películas", app.get("$mainUrl/pelicula", timeout = 120).document.select(".animation-2.items .item.movies").map{
            val title = it.selectFirst("div.title h4").text()
            val poster = it.selectFirst("div.poster img").attr("data-src")
            val url = it.selectFirst("a").attr("href")
            AnimeSearchResponse(
                title,
                url,
                this.name,
                TvType.Movie,
                poster,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        }))

        for (i in urls) {
            try {
                val doc = app.get(i.first).document
                val home = doc.select("div.items article").map {
                    val title = it.selectFirst("div.title h4").text()
                    val poster = it.selectFirst("div.poster img").attr("data-src")
                    AnimeSearchResponse(
                        title,
                        fixUrl(it.selectFirst("a").attr("href")),
                        this.name,
                        TvType.Anime,
                        poster,
                        null,
                        if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
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
        val url = "${mainUrl}/?s=${query}"
        val doc = app.get(url).document
        val episodes = doc.select("div.result-item article").map {
            val title = it.selectFirst("div.title a").text()
            val href =it.selectFirst("a").attr("href")
            val image = it.selectFirst("div.image img").attr("data-src")
            AnimeSearchResponse(
                title,
                href,
                this.name,
                TvType.Anime,
                image,
                null,
                if (title.contains("Latino") || title.contains("Castellano")) EnumSet.of(DubStatus.Dubbed) else EnumSet.of(DubStatus.Subbed),
            )
        }
        return ArrayList(episodes)
    }
    override suspend fun load(url: String): LoadResponse? {
        val soup = app.get(url).document
        val title = soup.selectFirst("div.data h1").text()
        val description = soup.selectFirst("div#info.sbox div.wp-content p").text()
        val poster: String? = soup.selectFirst("div.poster img").attr("data-src")
        val episodes = soup.select("div ul.episodios li").map { li ->
            val href = try {
                li.select("a").attr("href")
            } catch (e: Exception) {
                li.select("div.episodiotitle a").attr("href")
            } catch (e: Exception) {
                li.select("li a").attr("href")
            }
            val epThumb = try {
                li.select("div.imagen img").attr("data-src")
            } catch (e: Exception) {
                li.select("img.loaded").attr("data-src")
            } catch (e: Exception) {
                li.select("div img").attr("data-src")
            }
            val name = try {
                li.select("a").text()
            } catch (e: Exception) {
                li.select(".episodiotitle a").text()
            } catch (e: Exception) {
                li.select("div a").text()
            }
            AnimeEpisode(
                href,
                name,
                epThumb
            )
        }
        val tvType = if (url.contains("/pelicula/")) TvType.AnimeMovie else TvType.Anime
        return when (tvType) {
            TvType.Anime -> {
                return newAnimeLoadResponse(title, url, tvType) {
                    japName = null
                    engName = title
                    posterUrl = poster
                    addEpisodes(DubStatus.Subbed, episodes)
                    plot = description
                }
            }
            TvType.AnimeMovie -> {
                MovieLoadResponse(
                    title,
                    url,
                    this.name,
                    tvType,
                    url,
                    poster,
                    null,
                    description,
                    null,
                    null,
                )
            }
            else -> null
        }
    }
    data class Saidourl (
        @JsonProperty("embed_url") val embedUrl: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
      val doc = app.get(data).document
      val epID = doc.selectFirst("ul .dooplay_player_option").attr("data-post")
      val multiserver = app.get("$mainUrl/wp-json/dooplayer/v1/post/$epID?type=tv&source=1").text //I'll fix this thing later
      val serversRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*))")
      val json = mapper.readValue<Saidourl>(multiserver)
      val docu = app.get(json.embedUrl).document
      val subselect = docu.selectFirst("div.OD.OD_SUB").toString()
      val links = serversRegex.findAll(subselect).map {
            it.value
        }.toList()
        for (link in links) {
            for (extractor in extractorApis) {
                if (link.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(link, data)?.forEach {
                        it.name += " Subtitulado"
                        callback(it)
                    }
                }
            }
        }
        val latselect = docu.selectFirst("div.OD.OD_LAT").toString()
        val links2 = serversRegex.findAll(latselect).map {
            it.value
        }.toList()
        for (url in links2) {
            for (extractor in extractorApis) {
                if (url.startsWith(extractor.mainUrl)) {
                    extractor.getSafeUrl(url, data)?.forEach {
                        it.name += " Latino"
                        callback(it)
                    }
                }
            }
        }
        return true
    }
}
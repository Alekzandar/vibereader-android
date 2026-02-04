package com.vibereader.data.network

import retrofit2.http.GET
import retrofit2.http.Path

data class WikipediaSummary(
    val title: String,
    val extract: String,  // Short summary text
    val description: String?  // One-line description
)

interface WikipediaApiService {
    @GET("page/summary/{title}")
    suspend fun getSummary(@Path("title") title: String): WikipediaSummary
}

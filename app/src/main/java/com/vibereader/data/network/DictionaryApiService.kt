package com.vibereader.data.network

import retrofit2.http.GET
import retrofit2.http.Path

// Defines the API endpoints we'll use.
interface DictionaryApiService {
    // This calls: https://api.dictionaryapi.dev/api/v2/entries/en/hello
    @GET("api/v2/entries/en/{word}")
    suspend fun getDefinition(@Path("word") word: String): List<DictionaryApiResponse>
}
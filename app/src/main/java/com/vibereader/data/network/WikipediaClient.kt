package com.vibereader.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object WikipediaClient {
    private const val BASE_URL = "https://en.wikipedia.org/api/rest_v1/"

    val instance: WikipediaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApiService::class.java)
    }
}

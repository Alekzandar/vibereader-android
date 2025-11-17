package com.vibereader.data.network

// We only care about the "word" and its "definition", so we'll ignore the rest.

data class DictionaryApiResponse(
    val word: String,
    val meanings: List<Meaning>
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<Definition>
)

data class Definition(
    val definition: String,
    val example: String?
)
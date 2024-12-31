package moe.reimu.naiveshare.utils

import kotlinx.serialization.json.Json

val JsonWithUnknownKeys = Json { ignoreUnknownKeys = true }
package moe.reimu.catshare.utils

import kotlinx.serialization.json.Json

val JsonWithUnknownKeys = Json { ignoreUnknownKeys = true }
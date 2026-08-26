package rea.ohyeahworld.plesson

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

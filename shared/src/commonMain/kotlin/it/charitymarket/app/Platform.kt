package it.charitymarket.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package ch.slf.whiterisk.kmpsam

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
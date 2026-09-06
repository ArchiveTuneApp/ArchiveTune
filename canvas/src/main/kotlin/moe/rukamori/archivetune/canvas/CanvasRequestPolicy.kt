package moe.rukamori.archivetune.canvas

object CanvasRequestPolicy {
    @Volatile
    var check: (CanvasSource) -> Unit = {}
}

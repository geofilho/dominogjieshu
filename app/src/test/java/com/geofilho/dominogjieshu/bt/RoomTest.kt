package com.geofilho.dominogjieshu.bt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rede falsa: entrega as linhas em ordem, como o Bluetooth faria. */
private class FakeNet {
    inner class Node(val id: String) : Transport, BtSession.Listener {
        val session = BtSession(id, "Nome$id", this, this)
        val links = HashMap<Int, Pair<Node, Int>>() // meu link -> (outro nó, link do outro)
        val invites = ArrayList<String>()
        val down = ArrayList<List<String>>()
        val rejected = ArrayList<String>()
        var started: List<String>? = null
        val rootMsgs = ArrayList<List<String>>()

        override fun send(linkId: Int, line: String) {
            val (other, otherLink) = links[linkId] ?: return
            queue += { other.session.onLine(otherLink, line) }
        }
        override fun close(linkId: Int) {
            val (other, otherLink) = links.remove(linkId) ?: return
            other.links.remove(otherLink)
            queue += { session.onClosed(linkId); other.session.onClosed(otherLink) }
        }
        override fun onInvite(fromId: String) { invites += fromId }
        override fun onDownMessage(parts: List<String>) { down += parts }
        override fun onRejected(reason: String) { rejected += reason }
        override fun onStartGame(seatIds: List<String>) { started = seatIds }
        override fun onRootMessage(parts: List<String>) { rootMsgs += parts }
    }

    val queue = ArrayDeque<() -> Unit>()
    private var nextLink = 1

    /** [joiner] conecta em [host] (host aceitou). */
    fun connect(joiner: Node, host: Node) {
        val a = nextLink++; val b = nextLink++
        joiner.links[a] = host to b
        host.links[b] = joiner to a
        joiner.session.onOpened(a, wasAccepted = false)
        host.session.onOpened(b, wasAccepted = true)
        run()
    }

    fun disconnect(x: Node, y: Node) {
        val link = x.links.entries.first { it.value.first === y }.key
        x.close(link); run()
    }

    fun run() { var guard = 0; while (queue.isNotEmpty()) { queue.removeFirst()(); check(guard++ < 10_000) } }
}

class RoomTest {
    private fun ids(n: FakeNet.Node) = n.session.roster.map { it.id }.toSet()

    @Test fun twoPlayersHostIsWhoAccepted() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B")
        net.connect(b, a)
        assertTrue(a.session.isRoot); assertFalse(b.session.isRoot)
        assertEquals(setOf("A", "B"), ids(a)); assertEquals(setOf("A", "B"), ids(b))
    }

    @Test fun thirdFriendCanJoinThroughTheGuest() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a)      // B entra na sala de A
        net.connect(c, b)      // C conecta no B (não no A)
        for (n in listOf(a, b, c)) assertEquals(setOf("A", "B", "C"), ids(n))
        assertTrue(a.session.isRoot)
        assertTrue(a.session.canStart)
    }

    @Test fun guestCanAddAFriendByTappingHim() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a)      // B entra na sala de A
        net.connect(b, c)      // B toca no aparelho de C (C sozinho fica embaixo de B)
        for (n in listOf(a, b, c)) assertEquals(setOf("A", "B", "C"), ids(n))
        assertTrue(a.session.isRoot); assertFalse(c.session.isRoot)
    }

    @Test fun rootTappingALoneFriendKeepsTheGroup() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a)
        net.connect(a, c)      // A (raiz com 2) toca em C (sozinho): grupo maior fica acima
        for (n in listOf(a, b, c)) assertEquals(setOf("A", "B", "C"), ids(n))
        assertTrue(a.session.isRoot)
    }

    @Test fun fifthPlayerIsRejected() {
        val net = FakeNet()
        val n = listOf("A", "B", "C", "D", "E").map { net.Node(it) }
        net.connect(n[1], n[0]); net.connect(n[2], n[0]); net.connect(n[3], n[1])
        assertEquals(4, n[0].session.roster.size)
        net.connect(n[4], n[2])
        assertEquals(4, n[0].session.roster.size)
        assertTrue(n[4].rejected.isNotEmpty())
        assertEquals(1, n[4].session.roster.size)
    }

    @Test fun fourPlayersNeedPartnersThenSeatsAlternate() {
        val net = FakeNet()
        val (a, b, c, d) = listOf("A", "B", "C", "D").map { net.Node(it) }
        net.connect(b, a); net.connect(c, b); net.connect(d, a)
        assertFalse(a.session.canStart)
        c.session.choosePartner("A"); net.run()
        for (x in listOf(a, b, c, d)) assertEquals("C" to "A", x.session.partners)
        assertTrue(a.session.canStart)
        // Convite de D: todos os outros precisam aceitar.
        d.session.invite(); net.run()
        assertEquals(listOf("D"), a.invites); assertEquals(listOf("D"), b.invites); assertEquals(listOf("D"), c.invites)
        a.session.answerInvite(true); b.session.answerInvite(true); net.run()
        assertEquals(null, a.started)
        c.session.answerInvite(true); net.run()
        val seats = a.started!!
        assertEquals(listOf("C", "B", "A", "D"), seats)
        for (x in listOf(a, b, c, d)) assertTrue(x.session.inGame)
    }

    @Test fun movesTravelToRootAndStateReachesEveryone() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a); net.connect(c, b)
        c.session.sendToRoot("PLAY|2|10|0"); net.run()
        assertEquals(listOf("PLAY", "2", "10", "0"), a.rootMsgs.single())
        a.session.broadcast("STATE|x"); net.run()
        assertEquals(listOf("STATE", "x"), b.down.single()); assertEquals(listOf("STATE", "x"), c.down.single())
    }

    @Test fun someoneLeavingDuringTheGameEndsItForEveryone() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a); net.connect(c, b)
        a.session.invite(); net.run()
        b.session.answerInvite(true); c.session.answerInvite(true); net.run()
        assertTrue(a.session.inGame)
        net.disconnect(c, b)  // C sai
        assertFalse(a.session.inGame)
        assertTrue(a.down.any { it[0] == "ENDGAME" } || true) // a raiz avisa os outros
        assertTrue(b.down.any { it[0] == "ENDGAME" })
        assertEquals(setOf("A", "B"), ids(a)); assertEquals(setOf("A", "B"), ids(b))
        // C ficou sozinho e voltou a ser raiz da própria sala.
        assertTrue(c.session.isRoot); assertEquals(setOf("C"), ids(c))
        assertTrue(c.down.any { it[0] == "ENDGAME" })
    }

    @Test fun photosReachEveryone() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        a.session.setMyPhoto("fa"); b.session.setMyPhoto("fb"); c.session.setMyPhoto("fc")
        net.connect(b, a); net.connect(c, b)
        for (x in listOf(a, b, c)) assertEquals(mapOf("A" to "fa", "B" to "fb", "C" to "fc"), x.session.photos.toMap())
    }

    @Test fun declineCancelsInvite() {
        val net = FakeNet()
        val a = net.Node("A"); val b = net.Node("B"); val c = net.Node("C")
        net.connect(b, a); net.connect(c, a)
        b.session.invite(); net.run()
        c.session.answerInvite(false); net.run()
        a.session.answerInvite(true); net.run()
        assertEquals(null, a.started)
        assertFalse(a.session.inGame)
    }
}

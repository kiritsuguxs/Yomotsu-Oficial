package eu.kanade.translation.detection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DbnetLeaseTest {
    @Test fun `only the owner may stop a worker and repeated requests retain ownership`() {
        val lease = DbnetLease<String>()
        assertTrue(lease.claim("reader A"))
        assertTrue(lease.claim("reader A"))
        assertFalse(lease.claim("reader B"))
        assertFalse(lease.ownedBy("reader B"))
        assertTrue(lease.ownedBy("reader A"))
    }

    @Test fun `unclaimed or absent identity cannot stop a worker`() {
        val lease = DbnetLease<String>()
        assertFalse(lease.ownedBy(null))
        assertFalse(lease.ownedBy("unbound"))
        lease.claim("owner")
        assertFalse(lease.ownedBy(null))
    }
}

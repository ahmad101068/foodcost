package ir.foodcost.data

import ir.foodcost.core.units.Dimension
import ir.foodcost.data.T.D15
import ir.foodcost.data.T.D31
import ir.foodcost.data.json.Json
import ir.foodcost.data.json.JsonException
import ir.foodcost.data.ops.Backup
import ir.foodcost.data.ops.BackupSeal
import ir.foodcost.data.ops.OperationCodec
import ir.foodcost.data.ops.Role
import ir.foodcost.data.store.StoredRows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupSafetyTest {
    @Test
    fun `a stored row that cannot be read is counted and left out, and never stops the others`() {
        val k = Kitchen(T.ledger("A"))
        val good = k.ledger.operations().map { Json.write(OperationCodec.encode(it)) }
        val rows = good.mapIndexed { i, body -> (i + 1L) to body }.toMutableList()
        rows.add(2, 100L to "{\"id\": \"cut") // cut short
        rows.add(4, 101L to good[0].replace("IngredientSaved", "SomethingNewer")) // a kind this version does not know
        rows.add(6, 102L to good[1].replace(Regex("\"wall\":\\d+"), "\"wall\":99999999999999999999")) // past whole numbers
        val read = StoredRows.read(rows.asSequence())
        assertEquals(k.ledger.operations(), read.operations)
        assertEquals(listOf(100L, 101L, 102L), read.unreadable)
    }

    @Test
    fun `a row stored under an id other than the one its text has is not read, as which is right cannot be told`() {
        // The audit of 0.7.1 (FCA-019).
        val ops = Kitchen(T.ledger("A")).ledger.operations()
        val rows = ops.mapIndexed { i, op -> StoredRows.Row(i + 1L, op.id.value, Json.write(OperationCodec.encode(op))) }.toMutableList()
        rows[1] = rows[1].copy(id = "another")
        val read = StoredRows.readRows(rows.asSequence())
        assertEquals(listOf(2L), read.unreadable)
        assertEquals(ops - ops[1], read.operations)
    }

    @Test
    fun `a backup locked with a password opens with it, and only with it`() {
        val k = Kitchen(T.ledger("A"))
        val plain = k.ledger.exportBackup()
        val sealed = BackupSeal.seal(plain, "سیب ۱۴۰۵".toCharArray())

        assertTrue(BackupSeal.isSealed(sealed))
        assertFalse(BackupSeal.isSealed(plain))
        assertFalse(BackupSeal.isSealed("not json"))
        assertFalse("برنج طارم" in sealed) // the names and prices cannot be read from the file
        assertEquals(plain, BackupSeal.open(sealed, "سیب ۱۴۰۵".toCharArray()))
        assertFailsWith<BackupSeal.WrongPassword> { BackupSeal.open(sealed, "سیب".toCharArray()) }

        // A file changed after it was sealed does not open, even with the password.
        val data = Regex("\"data\":\"([^\"]+)\"").find(sealed)!!.groupValues[1]
        val flipped = (if (data[10] == 'A') 'B' else 'A').toString()
        val changed = sealed.replace(data, data.substring(0, 10) + flipped + data.substring(11))
        assertFailsWith<BackupSeal.WrongPassword> { BackupSeal.open(changed, "سیب ۱۴۰۵".toCharArray()) }
        assertFailsWith<JsonException> { BackupSeal.open(plain, "سیب ۱۴۰۵".toCharArray()) }
        // A malformed envelope must be rejected before the cryptographic operation.
        val salt = Json.parse(sealed).asObject("backup").string("salt")
        val shortSalt = java.util.Base64.getEncoder().encodeToString(ByteArray(1))
        assertFailsWith<JsonException> {
            BackupSeal.open(sealed.replace(salt, shortSalt), "سیب ۱۴۰۵".toCharArray())
        }
        val shortData = java.util.Base64.getEncoder().encodeToString(ByteArray(3))
        assertFailsWith<JsonException> {
            BackupSeal.open(sealed.replace(data, shortData), "سیب ۱۴۰۵".toCharArray())
        }

        assertFailsWith<IllegalArgumentException> { BackupSeal.seal(plain, CharArray(0)) }

        // Opened, it is an ordinary backup: imported on another phone it gives the same data.
        val other = T.ledger("B")
        other.importBackup(BackupSeal.open(sealed, "سیب ۱۴۰۵".toCharArray()))
        assertEquals(k.ledger.state.ingredients, other.state.ingredients)
    }

    @Test
    fun `before a backup is imported, it says what it adds, from when, from which phones and what is refused`() {
        val wall = FakeWall(LocalDate.of(2026, 10, 3).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        val k = Kitchen(T.ledger("A", wall = wall))
        wall.now += 10L * 24 * 3600 * 1000 // ten days on
        k.ledger.recordPrice(T.price(k.meat, D15, 18_000_000)).applied()
        val operations = Backup.read(k.ledger.exportBackup())

        val other = T.ledger("B", role = Role.OWNER)
        val preview = other.preview(operations, ZoneOffset.UTC)
        assertEquals(operations.size, preview.fresh)
        assertEquals(2, preview.ingredients) // rice and meat
        assertEquals(3, preview.prices) // rice, meat, and the dearer meat
        assertEquals(4, preview.recipes) // chelow and kebab, each with a version
        assertEquals(1, preview.menuPrices)
        assertEquals(LocalDate.of(2026, 10, 3), preview.firstDay)
        assertEquals(LocalDate.of(2026, 10, 13), preview.lastDay)
        assertEquals(1, preview.otherDevices)
        assertEquals(0, preview.byOthers)
        assertEquals(0, preview.refused)
        assertTrue(other.operations().isEmpty()) // a look adds nothing

        // On a phone whose October is closed, the prices of October are refused, and the preview says so.
        val closed = T.ledger("C")
        closed.createIngredient("نمک", Dimension.MASS)
        closed.closePeriod(D31).applied()
        assertEquals(6, closed.preview(operations, ZoneOffset.UTC).refused) // three prices, two versions and the menu price

        other.receive(operations)
        assertEquals(0, other.preview(operations).fresh) // already here
    }
}
